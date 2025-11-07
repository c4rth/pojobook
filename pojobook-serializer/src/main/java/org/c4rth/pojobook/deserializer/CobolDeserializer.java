package org.c4rth.pojobook.deserializer;

import org.c4rth.pojobook.annotation.CobolField;
import org.c4rth.pojobook.annotation.CobolRecord;
import org.c4rth.pojobook.exception.DeserializationException;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Deserializes COBOL binary data into POJO instances.
 */
public class CobolDeserializer {

    /**
     * Deserialize COBOL binary data to a POJO.
     */
    public <T> T deserialize(byte[] data, Class<T> clazz, Charset charset) throws Exception {
        CobolRecord recordAnnotation = clazz.getAnnotation(CobolRecord.class);

        if (recordAnnotation == null) {
            throw new DeserializationException("Class must be annotated with @CobolRecord");
        }

        // Create instance - handle private constructors and inner classes
        java.lang.reflect.Constructor<T> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        T instance = constructor.newInstance();

        Field[] fields = clazz.getDeclaredFields();
        int offset = 0;

        // Sort fields by position
        Field[] sortedFields = Arrays.stream(fields)
                .filter(f -> f.isAnnotationPresent(CobolField.class))
                .toArray(Field[]::new);

        for (Field field : sortedFields) {
            field.setAccessible(true);
            CobolField cobolField = field.getAnnotation(CobolField.class);

            // Calculate field length - handle nested class arrays specially
            int fieldLength;
            if (field.getType().isArray() && isNestedClassType(field.getType().getComponentType())) {
                // Nested class array - calculate total length for all elements
                int elementLength = calculateNestedClassLength(field.getType().getComponentType());
                fieldLength = elementLength * cobolField.occurs();
            } else {
                // Simple field or simple array
                fieldLength = calculateFieldLength(cobolField) * cobolField.occurs();
            }

            if (offset + fieldLength > data.length) {
                throw new DeserializationException("Data buffer too small for field: " + field.getName());
            }

            byte[] fieldData = Arrays.copyOfRange(data, offset, offset + fieldLength);
            Object value = deserializeField(fieldData, cobolField, field.getType(), charset);

            field.set(instance, value);
            offset += fieldLength;
        }

        return instance;
    }

    /**
     * Deserialize a single field.
     */
    private Object deserializeField(byte[] data, CobolField cobolField, Class<?> fieldType, Charset charset) throws Exception {
        // Handle arrays
        if (cobolField.occurs() > 1) {
            return deserializeArray(data, cobolField, fieldType, charset);
        }

        return switch (cobolField.type()) {
            case DISPLAY -> deserializeDisplay(data, cobolField, fieldType, charset);
            case COMP, COMP_5 -> deserializeComp(data, fieldType);
            case COMP_1 -> deserializeComp1(data);
            case COMP_2 -> deserializeComp2(data);
            case COMP_3, PACKED_DECIMAL -> deserializeComp3(data, cobolField);
            case ZONED_DECIMAL -> deserializeZonedDecimal(data, cobolField);
        };
    }

    /**
     * Deserialize array field.
     */
    private Object deserializeArray(byte[] data, CobolField cobolField, Class<?> fieldType, Charset charset) throws Exception {
        Class<?> componentType = fieldType.getComponentType();
        Object array = Array.newInstance(componentType, cobolField.occurs());

        // Check if component type is a nested class (has @CobolField or is in same package)
        boolean isNestedClass = isNestedClassType(componentType);

        if (isNestedClass) {
            // Deserialize nested class array - recursively deserialize each element
            int elementLength = calculateNestedClassLength(componentType);

            for (int i = 0; i < cobolField.occurs(); i++) {
                byte[] elementData = Arrays.copyOfRange(data, i * elementLength, (i + 1) * elementLength);
                Object element = deserialize(elementData, componentType, charset);
                Array.set(array, i, element);
            }
        } else {
            // Deserialize primitive/wrapper array
            int elementLength = calculateFieldLength(cobolField);

            for (int i = 0; i < cobolField.occurs(); i++) {
                byte[] elementData = Arrays.copyOfRange(data, i * elementLength, (i + 1) * elementLength);
                CobolField singleField = createSingleOccurField(cobolField);
                Object element = deserializeField(elementData, singleField, componentType, charset);
                Array.set(array, i, element);
            }
        }

        return array;
    }

    /**
     * Check if a type is a nested class with COBOL fields.
     */
    private boolean isNestedClassType(Class<?> type) {
        // Check if it has @CobolField annotated fields
        Field[] fields = type.getDeclaredFields();
        for (Field field : fields) {
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
        Field[] fields = clazz.getDeclaredFields();
        int totalLength = 0;

        for (Field field : fields) {
            if (field.isAnnotationPresent(CobolField.class)) {
                CobolField cobolField = field.getAnnotation(CobolField.class);

                // Check if this is a nested class array
                if (field.getType().isArray() && isNestedClassType(field.getType().getComponentType())) {
                    // Recursively calculate length of nested class
                    int nestedLength = calculateNestedClassLength(field.getType().getComponentType());
                    // The occurs on the field annotation tells us how many elements
                    totalLength += nestedLength * cobolField.occurs();
                } else if (field.getType().isArray() && !isNestedClassType(field.getType().getComponentType())) {
                    // Simple array (like String[])
                    int fieldLength = calculateFieldLength(cobolField);
                    totalLength += fieldLength * cobolField.occurs();
                } else {
                    // Simple field (not an array)
                    int fieldLength = calculateFieldLength(cobolField);
                    totalLength += fieldLength;
                }
            }
        }

        return totalLength;
    }

    /**
     * Create a CobolField annotation with occurs=1 for array element processing.
     */
    private CobolField createSingleOccurField(CobolField original) {
        return new CobolField() {
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return CobolField.class;
            }

            public int level() {
                return original.level();
            }

            public String name() {
                return original.name();
            }

            public String picture() {
                return original.picture();
            }

            public org.c4rth.pojobook.CobolDataType type() {
                return original.type();
            }

            public int length() {
                return original.length();
            }

            public int integerDigits() {
                return original.integerDigits();
            }

            public int decimalDigits() {
                return original.decimalDigits();
            }

            public boolean signed() {
                return original.signed();
            }

            public String signPosition() {
                return original.signPosition();
            }

            public boolean signSeparate() {
                return original.signSeparate();
            }

            public int occurs() {
                return 1;
            }

            public int minOccurs() {
                return original.minOccurs();
            }

            public int maxOccurs() {
                return original.maxOccurs();
            }

            public String dependingOn() {
                return original.dependingOn();
            }

            public int position() {
                return original.position();
            }

            public String redefines() {
                return original.redefines();
            }

            public boolean filler() {
                return original.filler();
            }

            public String value() {
                return original.value();
            }

            public boolean justifiedRight() {
                return original.justifiedRight();
            }

            public boolean blankWhenZero() {
                return original.blankWhenZero();
            }

            public String sync() {
                return original.sync();
            }

            public String[] indexedBy() {
                return original.indexedBy();
            }

            public String[] keys() {
                return original.keys();
            }

            public boolean ascendingKey() {
                return original.ascendingKey();
            }

            public boolean descendingKey() {
                return original.descendingKey();
            }
        };
    }

    /**
     * Deserialize DISPLAY field.
     */
    private Object deserializeDisplay(byte[] data, CobolField field, Class<?> targetType, Charset charset) {
        // Handle SIGN LEADING/TRAILING SEPARATE
        if (field.signed() && field.signSeparate()) {
            return deserializeDisplayWithSeparateSign(data, field, targetType, charset);
        }

        String strValue = new String(data, charset).trim();

        if (targetType == String.class) {
            return strValue;
        } else if (targetType == Integer.class || targetType == int.class) {
            return strValue.isEmpty() ? 0 : Integer.parseInt(strValue);
        } else if (targetType == Long.class || targetType == long.class) {
            return strValue.isEmpty() ? 0L : Long.parseLong(strValue);
        } else if (targetType == BigDecimal.class) {
            return strValue.isEmpty() ? BigDecimal.ZERO : new BigDecimal(strValue);
        } else if (targetType == BigInteger.class) {
            return strValue.isEmpty() ? BigInteger.ZERO : new BigInteger(strValue);
        }

        return strValue;
    }

    /**
     * Deserialize DISPLAY field with SIGN LEADING/TRAILING SEPARATE.
     */
    private Object deserializeDisplayWithSeparateSign(byte[] data, CobolField field, Class<?> targetType, Charset charset) {
        if (data.length == 0) {
            return getDefaultValue(targetType);
        }

        // Extract sign and digits based on position
        byte signByte;
        byte[] digitBytes;
        int totalDigits = field.integerDigits() + field.decimalDigits();

        if ("LEADING".equalsIgnoreCase(field.signPosition())) {
            // SIGN LEADING SEPARATE: first byte is sign, rest are digits
            signByte = data[0];
            digitBytes = new byte[data.length - 1];
            System.arraycopy(data, 1, digitBytes, 0, digitBytes.length);
        } else {
            // SIGN TRAILING SEPARATE (or default): last byte is sign, rest are digits
            signByte = data[data.length - 1];
            digitBytes = new byte[data.length - 1];
            System.arraycopy(data, 0, digitBytes, 0, digitBytes.length);
        }

        // Convert sign byte to boolean ('+' = positive, '-' = negative)
        // Decode the sign byte using the proper character encoding
        String signStr = new String(new byte[]{signByte}, charset);
        boolean isNegative = signStr.equals("-");

        // Convert digit bytes to string
        String digitString = new String(digitBytes, charset).trim();
        if (digitString.isEmpty()) {
            return getDefaultValue(targetType);
        }

        // Parse the numeric value
        BigDecimal value;
        if (field.decimalDigits() > 0) {
            // Has decimal places - need to insert decimal point
            BigInteger unscaledValue = new BigInteger(digitString);
            value = new BigDecimal(unscaledValue, field.decimalDigits());
        } else {
            value = new BigDecimal(digitString);
        }

        // Apply sign
        if (isNegative) {
            value = value.negate();
        }

        // Convert to target type
        if (targetType == BigDecimal.class) {
            return value;
        } else if (targetType == Integer.class || targetType == int.class) {
            return value.intValue();
        } else if (targetType == Long.class || targetType == long.class) {
            return value.longValue();
        } else if (targetType == Double.class || targetType == double.class) {
            return value.doubleValue();
        } else if (targetType == Float.class || targetType == float.class) {
            return value.floatValue();
        } else if (targetType == BigInteger.class) {
            return value.toBigInteger();
        } else if (targetType == String.class) {
            return value.toPlainString();
        }

        return value;
    }

    /**
     * Get default value for a target type.
     */
    private Object getDefaultValue(Class<?> targetType) {
        if (targetType == String.class) {
            return "";
        } else if (targetType == Integer.class || targetType == int.class) {
            return 0;
        } else if (targetType == Long.class || targetType == long.class) {
            return 0L;
        } else if (targetType == BigDecimal.class) {
            return BigDecimal.ZERO;
        } else if (targetType == BigInteger.class) {
            return BigInteger.ZERO;
        }
        return null;
    }

    /**
     * Deserialize COMP/BINARY field.
     */
    private Object deserializeComp(byte[] data, Class<?> targetType) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        long value;
        if (data.length == 2) {
            value = buffer.getShort();
        } else if (data.length == 4) {
            value = buffer.getInt();
        } else {
            value = buffer.getLong();
        }

        if (targetType == Short.class || targetType == short.class) {
            return (short) value;
        } else if (targetType == Integer.class || targetType == int.class) {
            return (int) value;
        } else if (targetType == Long.class || targetType == long.class) {
            return value;
        }

        return value;
    }

    /**
     * Deserialize COMP-1 (float) field.
     */
    private Object deserializeComp1(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        return buffer.getFloat();
    }

    /**
     * Deserialize COMP-2 (double) field.
     */
    private Object deserializeComp2(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        return buffer.getDouble();
    }

    /**
     * Deserialize COMP-3 (packed decimal) field.
     */
    private Object deserializeComp3(byte[] data, CobolField field) {
        StringBuilder digits = new StringBuilder();

        int totalDigits = field.integerDigits() + field.decimalDigits();
        boolean isOddDigits = (totalDigits % 2) != 0;

        // Extract digits from packed format
        for (int i = 0; i < data.length - 1; i++) {
            int high = (data[i] >> 4) & 0x0F;
            int low = data[i] & 0x0F;
            digits.append(high).append(low);
        }

        // Last byte contains last digit (if odd total digits) and sign
        int lastDigit = (data[data.length - 1] >> 4) & 0x0F;
        int sign = data[data.length - 1] & 0x0F;

        // Only add last digit if we have odd number of total digits
        if (isOddDigits) {
            digits.append(lastDigit);
        }

        BigInteger bigIntValue = new BigInteger(digits.toString());

        // Apply sign
        if (sign == 0x0D || sign == 0x0B) {
            bigIntValue = bigIntValue.negate();
        }

        // If no decimal places, return Integer/Long instead of BigDecimal
        if (field.decimalDigits() == 0) {
            // Return appropriate integer type based on size
            if (field.integerDigits() <= 9) {
                return bigIntValue.intValue();
            } else if (field.integerDigits() <= 18) {
                return bigIntValue.longValue();
            } else {
                return bigIntValue;
            }
        }

        // Has decimal places - return BigDecimal
        BigDecimal result = new BigDecimal(bigIntValue);
        result = result.setScale(field.decimalDigits(), RoundingMode.UNNECESSARY);
        result = result.divide(BigDecimal.TEN.pow(field.decimalDigits()), RoundingMode.UNNECESSARY);

        return result.stripTrailingZeros();
    }

    /**
     * Deserialize ZONED-DECIMAL field.
     */
    private Object deserializeZonedDecimal(byte[] data, CobolField field) {
        StringBuilder digits = new StringBuilder();

        for (int i = 0; i < data.length - 1; i++) {
            digits.append(data[i] & 0x0F);
        }

        // Last byte
        int lastDigit = data[data.length - 1] & 0x0F;
        int zone = (data[data.length - 1] >> 4) & 0x0F;
        digits.append(lastDigit);

        BigDecimal result = new BigDecimal(new BigInteger(digits.toString()));
        result = result.setScale(field.decimalDigits(), RoundingMode.UNNECESSARY);
        result = result.divide(BigDecimal.TEN.pow(field.decimalDigits()), RoundingMode.UNNECESSARY);

        // Apply sign
        if (zone == 0x0D || zone == 0x0B) {
            result = result.negate();
        }

        return result.stripTrailingZeros();
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

