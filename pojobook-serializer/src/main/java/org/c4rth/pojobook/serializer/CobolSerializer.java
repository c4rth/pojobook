package org.c4rth.pojobook.serializer;

import org.c4rth.pojobook.annotation.CobolField;
import org.c4rth.pojobook.annotation.CobolRecord;
import org.c4rth.pojobook.exception.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Serializes POJO instances to COBOL binary format.
 */
public class CobolSerializer {

    private static final Logger logger = LoggerFactory.getLogger(CobolSerializer.class);

    /**
     * Serialize a POJO to COBOL binary format.
     */
    public byte[] serialize(Object pojo, Charset charset) {
        if (pojo == null) {
            throw new SerializationException("POJO cannot be null");
        }

        Class<?> clazz = pojo.getClass();
        CobolRecord recordAnnotation = clazz.getAnnotation(CobolRecord.class);

        if (recordAnnotation == null) {
            throw new SerializationException("Class must be annotated with @CobolRecord");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Get all fields and sort by position
        Field[] fields = clazz.getDeclaredFields();
        Arrays.stream(fields)
                .filter(f -> f.isAnnotationPresent(CobolField.class))
                .forEach(field -> {
                    try {
                        field.setAccessible(true);
                        CobolField cobolField = field.getAnnotation(CobolField.class);
                        Object value = field.get(pojo);

                        byte[] fieldBytes = serializeField(value, cobolField, charset);
                        baos.write(fieldBytes);
                    } catch (Exception e) {
                        logger.error("Error serializing field: {}", field.getName(), e);
                        throw new SerializationException("Serialization error field: " + field.getName() + " - " + e.getMessage(), e);
                    }
                });

        return baos.toByteArray();
    }

    /**
     * Serialize a single field.
     */
    private byte[] serializeField(Object value, CobolField cobolField, Charset charset) {

        if (value == null) {
            // For nested class arrays (empty picture), calculate proper length
            if (cobolField.picture().isEmpty() && cobolField.occurs() > 1) {
                // This is a nested class array - need to calculate total length
                // We can't know the class type here, so return empty array
                // The length will be determined during actual serialization
                return new byte[0];
            }
            return new byte[calculateFieldLength(cobolField)];
        }

        // Handle arrays (including nested class arrays)
        if (value.getClass().isArray()) {
            return serializeArray(value, cobolField, charset);
        }

        return switch (cobolField.type()) {
            case DISPLAY -> serializeDisplay(value, cobolField, charset);
            case COMP, COMP_5 -> serializeComp(value, cobolField);
            case COMP_1 -> serializeComp1(value);
            case COMP_2 -> serializeComp2(value);
            case COMP_3, PACKED_DECIMAL -> serializeComp3(value, cobolField);
            case ZONED_DECIMAL -> serializeZonedDecimal(value, cobolField);
        };
    }

    /**
     * Serialize an array field (handles both primitive arrays and nested class arrays).
     */
    private byte[] serializeArray(Object arrayValue, CobolField cobolField, Charset charset) {
        int length = java.lang.reflect.Array.getLength(arrayValue);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (int i = 0; i < length; i++) {
            Object element = java.lang.reflect.Array.get(arrayValue, i);

            if (element == null) {
                // Write empty bytes for null element
                int elementLength = cobolField.picture().isEmpty()
                        ? calculateNestedClassLength(arrayValue.getClass().getComponentType())
                        : calculateFieldLength(cobolField);
                baos.write(new byte[elementLength], 0, elementLength);
            } else if (isNestedClassType(element.getClass())) {
                // Recursively serialize nested class
                byte[] elementBytes = serialize(element, charset);
                baos.write(elementBytes, 0, elementBytes.length);
            } else {
                // Serialize primitive/wrapper element
                byte[] elementBytes = switch (cobolField.type()) {
                    case DISPLAY -> serializeDisplay(element, cobolField, charset);
                    case COMP, COMP_5 -> serializeComp(element, cobolField);
                    case COMP_1 -> serializeComp1(element);
                    case COMP_2 -> serializeComp2(element);
                    case COMP_3, PACKED_DECIMAL -> serializeComp3(element, cobolField);
                    case ZONED_DECIMAL -> serializeZonedDecimal(element, cobolField);
                };
                baos.write(elementBytes, 0, elementBytes.length);
            }
        }

        return baos.toByteArray();
    }

    /**
     * Check if a type is a nested class with COBOL fields.
     */
    private boolean isNestedClassType(Class<?> type) {
        // Check if it has @CobolField annotated fields
        java.lang.reflect.Field[] fields = type.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            if (field.isAnnotationPresent(CobolField.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate the total length of a nested class by summing all its fields.
     */
    private int calculateNestedClassLength(Class<?> clazz) {
        java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
        int totalLength = 0;

        for (java.lang.reflect.Field field : fields) {
            if (field.isAnnotationPresent(CobolField.class)) {
                CobolField cobolField = field.getAnnotation(CobolField.class);
                int fieldLength = calculateFieldLength(cobolField);
                totalLength += fieldLength * cobolField.occurs();
            }
        }

        return totalLength;
    }

    /**
     * Serialize DISPLAY field.
     */
    private byte[] serializeDisplay(Object value, CobolField field, Charset charset) {
        // Handle SIGN LEADING/TRAILING SEPARATE
        if (field.signed() && field.signSeparate() && value instanceof Number) {
            return serializeDisplayWithSeparateSign((Number) value, field, charset);
        }

        String strValue = value.toString();
        int length = field.length() > 0 ? field.length() :
                field.integerDigits() + field.decimalDigits();

        // Pad or truncate
        if (strValue.length() < length) {
            if (field.picture().startsWith("9")) {
                // Numeric field - pad with leading zeros
                strValue = String.format("%1$" + length + "s", strValue).replace(' ', '0');
            } else {
                // Alphanumeric field - pad with spaces
                strValue = String.format("%-" + length + "s", strValue);
            }
            strValue = String.format("%-" + length + "s", strValue);
        } else if (strValue.length() > length) {
            strValue = strValue.substring(0, length);
        }

        return strValue.getBytes(charset);
    }

    /**
     * Serialize DISPLAY field with SIGN LEADING/TRAILING SEPARATE.
     */
    private byte[] serializeDisplayWithSeparateSign(Number value, CobolField field, Charset charset) {
        BigDecimal decimal;
        if (value instanceof BigDecimal bd) {
            decimal = bd;
        } else {
            decimal = new BigDecimal(value.toString());
        }

        // Scale to match COBOL definition
        if (field.decimalDigits() > 0) {
            decimal = decimal.setScale(field.decimalDigits(), RoundingMode.HALF_UP);
        }

        // Get absolute value and format as string
        BigDecimal absValue = decimal.abs();
        String unscaledValue = absValue.unscaledValue().toString();
        int totalDigits = field.integerDigits() + field.decimalDigits();

        // Pad with leading zeros using String.format
        unscaledValue = String.format("%0" + totalDigits + "d", absValue.unscaledValue());

        // Determine sign character
        char signChar = decimal.signum() >= 0 ? '+' : '-';
        // Convert sign to proper encoding (EBCDIC)
        byte[] signBytes = String.valueOf(signChar).getBytes(charset);
        byte signByte = signBytes[0];

        // Build result based on sign position
        byte[] digitBytes = unscaledValue.getBytes(charset);
        byte[] result = new byte[totalDigits + 1]; // digits + sign

        if ("LEADING".equalsIgnoreCase(field.signPosition())) {
            // SIGN LEADING SEPARATE: sign byte first, then digits
            result[0] = signByte;
            System.arraycopy(digitBytes, 0, result, 1, digitBytes.length);
        } else {
            // SIGN TRAILING SEPARATE (or default): digits first, then sign byte
            System.arraycopy(digitBytes, 0, result, 0, digitBytes.length);
            result[digitBytes.length] = signByte;
        }

        return result;
    }

    /**
     * Serialize COMP/BINARY field.
     */
    private byte[] serializeComp(Object value, CobolField field) {
        long longValue = ((Number) value).longValue();
        int totalDigits = field.integerDigits() + field.decimalDigits();

        ByteBuffer buffer;
        if (totalDigits <= 4) {
            buffer = ByteBuffer.allocate(2);
            buffer.putShort((short) longValue);
        } else if (totalDigits <= 9) {
            buffer = ByteBuffer.allocate(4);
            buffer.putInt((int) longValue);
        } else {
            buffer = ByteBuffer.allocate(8);
            buffer.putLong(longValue);
        }

        return buffer.array();
    }

    /**
     * Serialize COMP-1 (float) field.
     */
    private byte[] serializeComp1(Object value) {
        float floatValue = ((Number) value).floatValue();
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putFloat(floatValue);
        return buffer.array();
    }

    /**
     * Serialize COMP-2 (double) field.
     */
    private byte[] serializeComp2(Object value) {
        double doubleValue = ((Number) value).doubleValue();
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putDouble(doubleValue);
        return buffer.array();
    }

    /**
     * Serialize COMP-3 (packed decimal) field.
     */
    private byte[] serializeComp3(Object value, CobolField field) {
        BigDecimal decimal;
        if (value instanceof BigDecimal bd) {
            decimal = bd;
        } else {
            decimal = new BigDecimal(value.toString());
        }

        // Scale to match COBOL definition
        decimal = decimal.setScale(field.decimalDigits(), RoundingMode.HALF_UP);

        StringBuilder digits = new StringBuilder(decimal.unscaledValue().abs().toString());
        int totalDigits = field.integerDigits() + field.decimalDigits();

        // Pad with zeros if needed - use String padding instead of format
        while (digits.length() < totalDigits) {
            digits.insert(0, "0");
        }

        int byteLength = (totalDigits / 2) + 1;
        byte[] packed = new byte[byteLength];

        int digitIndex = 0;
        for (int i = 0; i < byteLength - 1; i++) {
            int high = Character.digit(digits.charAt(digitIndex++), 10);
            int low = Character.digit(digits.charAt(digitIndex++), 10);
            packed[i] = (byte) ((high << 4) | low);
        }

        // Last byte: digit + sign
        // If odd number of digits, include the last digit; if even, use 0
        int lastDigit = (totalDigits % 2 != 0) ?
                Character.digit(digits.charAt(digitIndex), 10) : 0;
        int sign = decimal.signum() >= 0 ? 0x0C : 0x0D;
        packed[byteLength - 1] = (byte) ((lastDigit << 4) | sign);

        return packed;
    }

    /**
     * Serialize ZONED-DECIMAL field.
     */
    private byte[] serializeZonedDecimal(Object value, CobolField field) {
        BigDecimal decimal = new BigDecimal(value.toString());
        decimal = decimal.setScale(field.decimalDigits(), RoundingMode.HALF_UP);

        String digits = decimal.unscaledValue().abs().toString();
        int totalDigits = field.integerDigits() + field.decimalDigits();
        digits = String.format("%0" + totalDigits + "d", new BigInteger(digits));

        byte[] zoned = new byte[totalDigits];
        for (int i = 0; i < totalDigits - 1; i++) {
            zoned[i] = (byte) (0xF0 | Character.digit(digits.charAt(i), 10));
        }

        // Last byte includes sign
        int lastDigit = Character.digit(digits.charAt(totalDigits - 1), 10);
        int sign = decimal.signum() >= 0 ? 0xF0 : 0xD0;
        zoned[totalDigits - 1] = (byte) (sign | lastDigit);

        return zoned;
    }

    /**
     * Calculate the byte length of a field.
     */
    private int calculateFieldLength(CobolField field) {
        int totalDigits = field.integerDigits() + field.decimalDigits();

        int baseLength = switch (field.type()) {
            case DISPLAY -> field.length() > 0 ? field.length() : totalDigits;
            case COMP, COMP_5 -> {
                if (totalDigits <= 4) yield 2;
                if (totalDigits <= 9) yield 4;
                yield 8;
            }
            case COMP_1 -> 4;
            case COMP_2 -> 8;
            case COMP_3, PACKED_DECIMAL -> (totalDigits / 2) + 1;
            case ZONED_DECIMAL -> totalDigits;
        };

        // Add extra byte for SIGN SEPARATE CHARACTER
        if (field.type() == org.c4rth.pojobook.CobolDataType.DISPLAY && field.signed() && field.signSeparate()) {
            baseLength += 1;
        }

        return baseLength;
    }
}

