package org.pojobook.serializer;

import org.pojobook.annotation.CobolField;
import org.pojobook.annotation.CobolRecord;
import org.pojobook.exception.SerializationException;
import org.pojobook.util.DisplayNumericUtil;
import org.pojobook.util.SignedNumericUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serializes POJO instances to COBOL binary format.
 * Optimized with field metadata caching and pre-allocated buffers.
 */
public class CobolSerializer {

    private static final Logger logger = LoggerFactory.getLogger(CobolSerializer.class);

    // Cache for field metadata to avoid repeated reflection
    private static final Map<Class<?>, List<FieldMetadata>> FIELD_CACHE = new ConcurrentHashMap<>();

    // Cache for single-occur field annotations to avoid expensive proxy creation
    private static final Map<CobolField, CobolField> SINGLE_OCCUR_CACHE = new ConcurrentHashMap<>();

    /**
     * Cached field metadata for performance.
     */
    private record FieldMetadata(Field field, CobolField annotation, int fieldSize, int baseLength) {
        public FieldMetadata {
            field.setAccessible(true);
        }
    }

    /**
     * Serialize a POJO to COBOL binary format.
     */
    public byte[] serialize(Object pojo, Charset charset) throws SerializationException {
        validatePojo(pojo);

        List<FieldMetadata> fields = getFieldMetadata(pojo.getClass());
        int totalSize = calculateTotalSize(fields);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(totalSize);
        for (FieldMetadata fieldMeta : fields) {
            writeField(fieldMeta, pojo, baos, charset);
        }

        return baos.toByteArray();
    }

    /**
     * Get cached field metadata or compute and cache it.
     */
    private List<FieldMetadata> getFieldMetadata(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, this::computeFieldMetadata);
    }

    /**
     * Compute field metadata for a class.
     */
    private List<FieldMetadata> computeFieldMetadata(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(CobolField.class))
                .map(f -> {
                    CobolField annotation = f.getAnnotation(CobolField.class);
                    int baseLength = annotation.length() > 0 ? annotation.length() : annotation.integerDigits() + annotation.decimalDigits();
                    if (annotation.signed() && annotation.signSeparate()) {
                        baseLength++;
                    }
                    int size = baseLength * annotation.occurs();
                    return new FieldMetadata(f, annotation, size, baseLength);
                })
                .toList();
    }

    /**
     * Calculate total serialized size for pre-allocation.
     */
    private int calculateTotalSize(List<FieldMetadata> fields) {
        return fields.stream()
                .mapToInt(FieldMetadata::fieldSize)
                .sum();
    }

    private void validatePojo(Object pojo) throws SerializationException {
        if (pojo == null) {
            throw new SerializationException("POJO cannot be null");
        }
        if (pojo.getClass().getAnnotation(CobolRecord.class) == null) {
            throw new SerializationException("Class must be annotated with @CobolRecord");
        }
    }

    private void writeField(FieldMetadata fieldMeta, Object pojo, ByteArrayOutputStream baos, Charset charset) throws SerializationException {
        try {
            Object value = fieldMeta.field.get(pojo);
            byte[] fieldBytes = serializeFieldWithMeta(value, fieldMeta, charset);
            baos.write(fieldBytes);
        } catch (IllegalAccessException | IOException e) {
            logger.error("Error serializing field: {}", fieldMeta.field.getName(), e);
            throw new SerializationException("Serialization error field: " + fieldMeta.field.getName(), e);
        }
    }

    /**
     * Serialize a single field with metadata.
     */
    private byte[] serializeFieldWithMeta(Object value, FieldMetadata fieldMeta, Charset charset) throws SerializationException {
        if (value == null) {
            return new byte[fieldMeta.fieldSize];
        }

        return value.getClass().isArray()
                ? serializeArray(value, fieldMeta.annotation, charset)
                : serializeSimpleFieldWithLength(value, fieldMeta.annotation, fieldMeta.baseLength(), charset);
    }

    /**
     * Serialize a single field.
     */
    private byte[] serializeField(Object value, CobolField cobolField, Charset charset) throws SerializationException {
        if (value == null) {
            return createNullFieldBytes(cobolField);
        }

        return value.getClass().isArray()
                ? serializeArray(value, cobolField, charset)
                : serializeSimpleField(value, cobolField, charset);
    }

    private byte[] createNullFieldBytes(CobolField cobolField) {
        int fieldLength = calculateFieldLength(cobolField);
        return new byte[fieldLength];
    }

    private int calculateFieldLength(CobolField field) {
        int baseLength = field.length() > 0 ? field.length() : field.integerDigits() + field.decimalDigits();
        if (field.signed() && field.signSeparate()) {
            baseLength++;
        }
        return baseLength * field.occurs();
    }

    private byte[] serializeSimpleField(Object value, CobolField cobolField, Charset charset) {
        return switch (cobolField.type()) {
            case DISPLAY -> serializeDisplay(value, cobolField, charset);
            case COMP, COMP_5 -> serializeComp(value, cobolField);
            case COMP_1 -> serializeComp1(value);
            case COMP_2 -> serializeComp2(value);
            case COMP_3, PACKED_DECIMAL -> serializeComp3(value, cobolField);
            case ZONED_DECIMAL -> serializeZonedDecimal(value, cobolField);
        };
    }

    private byte[] serializeSimpleFieldWithLength(Object value, CobolField cobolField, int baseLength, Charset charset) {
        return switch (cobolField.type()) {
            case DISPLAY -> serializeDisplayWithLength(value, cobolField, baseLength, charset);
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
    private byte[] serializeArray(Object arrayValue, CobolField cobolField, Charset charset) throws SerializationException {
        int length = Array.getLength(arrayValue);

        // Get cached single-occur field to avoid creating expensive proxy for each element
        CobolField singleOccurField = getSingleOccurField(cobolField);

        // Pre-calculate element size for buffer allocation
        int elementSize = calculateFieldLength(singleOccurField);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(elementSize * length);

        for (int i = 0; i < length; i++) {
            Object element = Array.get(arrayValue, i);
            byte[] elementBytes = serializeArrayElement(element, singleOccurField, charset);
            try {
                baos.write(elementBytes);
            } catch (IOException e) {
                throw new SerializationException("Error writing array element", e);
            }
        }

        return baos.toByteArray();
    }

    /**
     * Get or create cached single-occur field annotation.
     */
    private CobolField getSingleOccurField(CobolField original) {
        return SINGLE_OCCUR_CACHE.computeIfAbsent(original, this::createSingleOccurField);
    }

    private byte[] serializeArrayElement(Object element, CobolField singleOccurField, Charset charset) throws SerializationException {
        if (element == null) {
            return createNullFieldBytes(singleOccurField);
        }

        return isNestedClassType(element.getClass())
                ? serialize(element, charset)
                : serializeSimpleField(element, singleOccurField, charset);
    }

    /**
     * Check if a type is a nested class with COBOL fields.
     */
    private boolean isNestedClassType(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .anyMatch(f -> f.isAnnotationPresent(CobolField.class));
    }

    /**
     * Create a CobolField annotation with occurs=1 for array element processing.
     */
    private CobolField createSingleOccurField(CobolField original) {
        return new CobolField() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return CobolField.class;
            }

            @Override
            public int level() {
                return original.level();
            }

            @Override
            public String name() {
                return original.name();
            }

            @Override
            public String picture() {
                return original.picture();
            }

            @Override
            public org.pojobook.CobolDataType type() {
                return original.type();
            }

            @Override
            public int length() {
                return original.length();
            }

            @Override
            public int integerDigits() {
                return original.integerDigits();
            }

            @Override
            public int decimalDigits() {
                return original.decimalDigits();
            }

            @Override
            public boolean signed() {
                return original.signed();
            }

            @Override
            public String signPosition() {
                return original.signPosition();
            }

            @Override
            public boolean signSeparate() {
                return original.signSeparate();
            }

            @Override
            public int occurs() {
                return 1;
            }

            @Override
            public int minOccurs() {
                return original.minOccurs();
            }

            @Override
            public int maxOccurs() {
                return original.maxOccurs();
            }

            @Override
            public String dependingOn() {
                return original.dependingOn();
            }

            @Override
            public int position() {
                return original.position();
            }

            @Override
            public String redefines() {
                return original.redefines();
            }

            @Override
            public boolean filler() {
                return original.filler();
            }

            @Override
            public String value() {
                return original.value();
            }

            @Override
            public boolean justifiedRight() {
                return original.justifiedRight();
            }

            @Override
            public boolean blankWhenZero() {
                return original.blankWhenZero();
            }

            @Override
            public String sync() {
                return original.sync();
            }

            @Override
            public String[] indexedBy() {
                return original.indexedBy();
            }

            @Override
            public String[] keys() {
                return original.keys();
            }

            @Override
            public boolean ascendingKey() {
                return original.ascendingKey();
            }

            @Override
            public boolean descendingKey() {
                return original.descendingKey();
            }
        };
    }

    /**
     * Serialize DISPLAY field.
     */
    private byte[] serializeDisplay(Object value, CobolField field, Charset charset) {
        boolean isNumeric = isNumericPicture(field.picture());

        if (field.signed() && field.signSeparate() && value instanceof Number) {
            return serializeDisplayWithSeparateSign((Number) value, field, charset);
        }

        if (field.signed() && !field.signSeparate() && value instanceof Number && isNumeric) {
            return serializeDisplayWithEmbeddedSign((Number) value, field, charset);
        }

        if (!field.signed() && field.decimalDigits() > 0 && value instanceof Number && isNumeric) {
            return serializeDisplayWithImpliedDecimal((Number) value, field, charset);
        }

        return serializeDisplayString(value, field, isNumeric, charset);
    }

    /**
     * Serialize DISPLAY field with pre-calculated length (optimized).
     */
    private byte[] serializeDisplayWithLength(Object value, CobolField field, int baseLength, Charset charset) {
        boolean isNumeric = isNumericPicture(field.picture());

        if (field.signed() && field.signSeparate() && value instanceof Number) {
            return serializeDisplayWithSeparateSign((Number) value, field, charset);
        }

        if (field.signed() && !field.signSeparate() && value instanceof Number && isNumeric) {
            return serializeDisplayWithEmbeddedSign((Number) value, field, charset);
        }

        if (!field.signed() && field.decimalDigits() > 0 && value instanceof Number && isNumeric) {
            return serializeDisplayWithImpliedDecimal((Number) value, field, charset);
        }

        return serializeDisplayStringWithLength(value, baseLength, isNumeric, charset);
    }

    /**
     * Serialize DISPLAY string with pre-calculated length (optimized).
     */
    private byte[] serializeDisplayStringWithLength(Object value, int length, boolean isNumeric, Charset charset) {
        String strValue = value.toString();
        int strLen = strValue.length();

        // Fast path: if exact length, avoid allocations
        if (strLen == length) {
            return strValue.getBytes(charset);
        }

        // Optimize: use String.repeat() and pre-computed length
        if (strLen < length) {
            int padding = length - strLen;
            if (isNumeric) {
                // Numeric: pad left with zeros
                strValue = "0".repeat(padding) + strValue;
            } else {
                // Alphanumeric: pad right with spaces
                strValue = strValue + " ".repeat(padding);
            }
        } else {
            strValue = strValue.substring(0, length);
        }

        return strValue.getBytes(charset);
    }

    private boolean isNumericPicture(String picture) {
        return picture.startsWith("9") || picture.startsWith("S9");
    }

    private byte[] serializeDisplayString(Object value, CobolField field, boolean isNumeric, Charset charset) {
        String strValue = value.toString();
        int length = field.length() > 0 ? field.length() : field.integerDigits() + field.decimalDigits();
        int strLen = strValue.length();

        // Fast path: if exact length, avoid allocations
        if (strLen == length) {
            return strValue.getBytes(charset);
        }

        // Optimize: use String.repeat() instead of String.format()
        if (strLen < length) {
            int padding = length - strLen;
            if (isNumeric) {
                // Numeric: pad left with zeros
                strValue = "0".repeat(padding) + strValue;
            } else {
                // Alphanumeric: pad right with spaces
                strValue = strValue + " ".repeat(padding);
            }
        } else {
            strValue = strValue.substring(0, length);
        }

        return strValue.getBytes(charset);
    }

    /**
     * Serialize DISPLAY field with implied decimal (V in PIC clause).
     */
    private byte[] serializeDisplayWithImpliedDecimal(Number value, CobolField field, Charset charset) {
        int length = field.integerDigits() + field.decimalDigits();
        return DisplayNumericUtil.formatUnsignedWithImpliedDecimal(value, length, field.decimalDigits(), charset);
    }

    /**
     * Serialize DISPLAY field with embedded sign (overpunch notation).
     */
    private byte[] serializeDisplayWithEmbeddedSign(Number value, CobolField field, Charset charset) {
        int length = field.integerDigits() + field.decimalDigits();
        return SignedNumericUtil.formatSignedDisplay(value, length, field.decimalDigits(), charset);
    }

    /**
     * Serialize DISPLAY field with SIGN LEADING/TRAILING SEPARATE.
     */
    private byte[] serializeDisplayWithSeparateSign(Number value, CobolField field, Charset charset) {
        BigDecimal decimal = toBigDecimal(value);
        if (field.decimalDigits() > 0) {
            decimal = decimal.setScale(field.decimalDigits(), RoundingMode.HALF_UP);
        }

        String unscaledValue = formatUnscaledValue(decimal.abs(), field);
        byte signByte = getSignByte(decimal, charset);
        byte[] digitBytes = unscaledValue.getBytes(charset);

        return buildSignedResult(digitBytes, signByte, field);
    }

    private BigDecimal toBigDecimal(Number value) {
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    private String formatUnscaledValue(BigDecimal absValue, CobolField field) {
        int totalDigits = field.integerDigits() + field.decimalDigits();
        return String.format("%0" + totalDigits + "d", absValue.unscaledValue());
    }

    private byte getSignByte(BigDecimal decimal, Charset charset) {
        char signChar = decimal.signum() >= 0 ? '+' : '-';
        return String.valueOf(signChar).getBytes(charset)[0];
    }

    private byte[] buildSignedResult(byte[] digitBytes, byte signByte, CobolField field) {
        byte[] result = new byte[digitBytes.length + 1];

        if ("LEADING".equalsIgnoreCase(field.signPosition())) {
            result[0] = signByte;
            System.arraycopy(digitBytes, 0, result, 1, digitBytes.length);
        } else {
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

        // Inline ByteBuffer operations for performance
        if (totalDigits <= 4) {
            return ByteBuffer.allocate(2).putShort((short) longValue).array();
        }
        if (totalDigits <= 9) {
            return ByteBuffer.allocate(4).putInt((int) longValue).array();
        }
        return ByteBuffer.allocate(8).putLong(longValue).array();
    }

    /**
     * Serialize COMP-1 (float) field.
     */
    private byte[] serializeComp1(Object value) {
        return ByteBuffer.allocate(4).putFloat(((Number) value).floatValue()).array();
    }

    /**
     * Serialize COMP-2 (double) field.
     */
    private byte[] serializeComp2(Object value) {
        return ByteBuffer.allocate(8).putDouble(((Number) value).doubleValue()).array();
    }

    /**
     * Serialize COMP-3 (packed decimal) field.
     */
    private byte[] serializeComp3(Object value, CobolField field) {
        BigDecimal decimal = toBigDecimal(((Number) value).doubleValue()).setScale(field.decimalDigits(), RoundingMode.HALF_UP);
        String digits = formatUnscaledDigits(decimal.abs(), field);

        return packDigits(digits, decimal.signum(), field.integerDigits() + field.decimalDigits());
    }

    private String formatUnscaledDigits(BigDecimal absValue, CobolField field) {
        int totalDigits = field.integerDigits() + field.decimalDigits();
        StringBuilder digits = new StringBuilder(absValue.unscaledValue().toString());

        while (digits.length() < totalDigits) {
            digits.insert(0, "0");
        }

        return digits.toString();
    }

    private byte[] packDigits(String digits, int signum, int totalDigits) {
        int byteLength = (totalDigits / 2) + 1;
        byte[] packed = new byte[byteLength];

        int digitIndex = 0;
        for (int i = 0; i < byteLength - 1; i++) {
            int high = Character.digit(digits.charAt(digitIndex++), 10);
            int low = Character.digit(digits.charAt(digitIndex++), 10);
            packed[i] = (byte) ((high << 4) | low);
        }

        int lastDigit = (totalDigits % 2 != 0) ? Character.digit(digits.charAt(digitIndex), 10) : 0;
        int sign = signum >= 0 ? 0x0C : 0x0D;
        packed[byteLength - 1] = (byte) ((lastDigit << 4) | sign);

        return packed;
    }

    /**
     * Serialize ZONED-DECIMAL field.
     */
    private byte[] serializeZonedDecimal(Object value, CobolField field) {
        BigDecimal decimal = toBigDecimal(new BigDecimal(value.toString())).setScale(field.decimalDigits(), RoundingMode.HALF_UP);
        String digits = formatUnscaledDigits(decimal.abs(), field);
        int totalDigits = field.integerDigits() + field.decimalDigits();

        return createZonedBytes(digits, decimal.signum(), totalDigits);
    }

    private byte[] createZonedBytes(String digits, int signum, int totalDigits) {
        byte[] zoned = new byte[totalDigits];

        for (int i = 0; i < totalDigits - 1; i++) {
            zoned[i] = (byte) (0xF0 | Character.digit(digits.charAt(i), 10));
        }

        int lastDigit = Character.digit(digits.charAt(totalDigits - 1), 10);
        int sign = signum >= 0 ? 0xF0 : 0xD0;
        zoned[totalDigits - 1] = (byte) (sign | lastDigit);

        return zoned;
    }
}
