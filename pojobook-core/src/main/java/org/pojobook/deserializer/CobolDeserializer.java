package org.pojobook.deserializer;

import org.pojobook.CobolDataType;
import org.pojobook.annotation.CobolField;
import org.pojobook.annotation.CobolRecord;
import org.pojobook.exception.DeserializationException;
import org.pojobook.util.CobolFieldUtil;
import org.pojobook.util.DisplayNumericUtil;
import org.pojobook.util.SignedNumericUtil;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deserializes COBOL binary data into POJO instances.
 * Optimized with field metadata caching and direct array access.
 */
public class CobolDeserializer {

    // Cache for field metadata to avoid repeated reflection
    private static final Map<Class<?>, List<FieldMetadata>> FIELD_CACHE = new ConcurrentHashMap<>();

    // Cache for single-occur field annotations
    private static final Map<CobolField, CobolField> SINGLE_OCCUR_CACHE = new ConcurrentHashMap<>();

    /**
     * Cached field metadata for performance.
     */
    private record FieldMetadata(Field field, CobolField annotation, int fieldSize, int baseLength,
                                 Class<?> fieldType, boolean isNumeric, boolean isString) {
        public FieldMetadata {
            field.setAccessible(true);
        }
    }

    /**
     * Deserialize COBOL binary data to a POJO.
     */
    public <T> T deserialize(byte[] data, Class<T> clazz, Charset charset) throws DeserializationException {
        validateClass(clazz);
        T instance = createInstance(clazz);

        List<FieldMetadata> fields = getFieldMetadata(clazz);
        int offset = 0;

        for (FieldMetadata fieldMeta : fields) {
            validateDataLength(data, offset, fieldMeta.fieldSize, fieldMeta.field.getName());

            // Use optimized deserialization with cached metadata
            Object value = deserializeFieldWithMeta(data, offset, fieldMeta, charset);

            setFieldValue(fieldMeta.field, instance, value);
            offset += fieldMeta.fieldSize;
        }

        return instance;
    }

    /**
     * Deserialize field using cached metadata (optimized hot path).
     */
    private Object deserializeFieldWithMeta(byte[] data, int offset, FieldMetadata fieldMeta, Charset charset)
            throws DeserializationException {
        if (fieldMeta.annotation.occurs() > 1) {
            return deserializeArrayDirect(data, offset, fieldMeta.fieldSize, fieldMeta.annotation,
                                         fieldMeta.fieldType, charset);
        }

        return switch (fieldMeta.annotation.type()) {
            case DISPLAY -> deserializeDisplayOptimized(data, offset, fieldMeta.baseLength, fieldMeta, charset);
            case COMP, COMP_5 -> deserializeCompDirect(data, offset, fieldMeta.baseLength, fieldMeta.fieldType);
            case COMP_1 -> ByteBuffer.wrap(data, offset, 4).getFloat();
            case COMP_2 -> ByteBuffer.wrap(data, offset, 8).getDouble();
            case COMP_3, PACKED_DECIMAL -> deserializeComp3Direct(data, offset, fieldMeta.baseLength, fieldMeta.annotation);
            case ZONED_DECIMAL -> deserializeZonedDecimalDirect(data, offset, fieldMeta.baseLength, fieldMeta.annotation);
        };
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
                    int size = calculateFieldLength(f, annotation);

                    // Calculate base length (single occurrence)
                    int baseLength;
                    if (f.getType().isArray() && isNestedClassType(f.getType().getComponentType())) {
                        baseLength = calculateNestedClassLength(f.getType().getComponentType());
                    } else {
                        baseLength = CobolFieldUtil.calculateFieldLength(annotation);
                    }

                    // Cache type information
                    Class<?> fieldType = f.getType();
                    Class<?> componentType = fieldType.isArray() ? fieldType.getComponentType() : fieldType;
                    boolean isNumeric = isNumericType(componentType);
                    boolean isString = componentType == String.class;

                    return new FieldMetadata(f, annotation, size, baseLength, fieldType, isNumeric, isString);
                })
                .toList();
    }

    /**
     * Deserialize field using direct array access with offset.
     */
    private Object deserializeFieldDirect(byte[] data, int offset, int length, CobolField cobolField,
                                         Class<?> fieldType, Charset charset) throws DeserializationException {
        if (cobolField.occurs() > 1) {
            return deserializeArrayDirect(data, offset, length, cobolField, fieldType, charset);
        }

        return switch (cobolField.type()) {
            case DISPLAY -> deserializeDisplayDirect(data, offset, length, cobolField, fieldType, charset);
            case COMP, COMP_5 -> deserializeCompDirect(data, offset, length, fieldType);
            case COMP_1 -> ByteBuffer.wrap(data, offset, 4).getFloat();
            case COMP_2 -> ByteBuffer.wrap(data, offset, 8).getDouble();
            case COMP_3, PACKED_DECIMAL -> deserializeComp3Direct(data, offset, length, cobolField);
            case ZONED_DECIMAL -> deserializeZonedDecimalDirect(data, offset, length, cobolField);
        };
    }

    private <T> void validateClass(Class<T> clazz) throws DeserializationException {
        if (clazz.getAnnotation(CobolRecord.class) == null) {
            throw new DeserializationException("Class must be annotated with @CobolRecord");
        }
    }

    private <T> T createInstance(Class<T> clazz) throws DeserializationException {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new DeserializationException("Error creating instance of class: " + clazz.getName(), e);
        }
    }

    private int calculateFieldLength(Field field, CobolField cobolField) {
        if (field.getType().isArray() && isNestedClassType(field.getType().getComponentType())) {
            int elementLength = calculateNestedClassLength(field.getType().getComponentType());
            return elementLength * cobolField.occurs();
        }
        return CobolFieldUtil.calculateFieldLength(cobolField) * cobolField.occurs();
    }

    private void validateDataLength(byte[] data, int offset, int fieldLength, String fieldName) throws DeserializationException {
        if (offset + fieldLength > data.length) {
            throw new DeserializationException("Data buffer too small for field: " + fieldName);
        }
    }

    private void setFieldValue(Field field, Object instance, Object value) throws DeserializationException {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new DeserializationException("Error while setting field value: " + field.getName(), e);
        }
    }

    /**
     * Deserialize array field using direct array access.
     */
    private Object deserializeArrayDirect(byte[] data, int offset, int totalLength, CobolField cobolField,
                                         Class<?> fieldType, Charset charset) throws DeserializationException {
        Class<?> componentType = fieldType.getComponentType();
        Object array = Array.newInstance(componentType, cobolField.occurs());

        if (isNestedClassType(componentType)) {
            int elementLength = calculateNestedClassLength(componentType);
            for (int i = 0; i < cobolField.occurs(); i++) {
                // Still need to copy for nested classes since they need their own deserialize call
                byte[] elementData = Arrays.copyOfRange(data, offset + i * elementLength, offset + (i + 1) * elementLength);
                Array.set(array, i, deserialize(elementData, componentType, charset));
            }
        } else {
            int elementLength = CobolFieldUtil.calculateFieldLength(cobolField);
            CobolField singleField = getSingleOccurField(cobolField);

            for (int i = 0; i < cobolField.occurs(); i++) {
                // Direct array access - no copying!
                Object element = deserializeFieldDirect(data, offset + i * elementLength, elementLength,
                        singleField, componentType, charset);
                Array.set(array, i, element);
            }
        }

        return array;
    }

    /**
     * Get or create cached single-occur field annotation.
     */
    private CobolField getSingleOccurField(CobolField original) {
        return SINGLE_OCCUR_CACHE.computeIfAbsent(original, this::createSingleOccurField);
    }

    /**
     * Deserialize DISPLAY field using direct array access.
     */
    private Object deserializeDisplayDirect(byte[] data, int offset, int length, CobolField field,
                                           Class<?> targetType, Charset charset) {
        if (field.signed() && field.signSeparate()) {
            return deserializeDisplayWithSeparateSign(Arrays.copyOfRange(data, offset, offset + length), field, targetType, charset);
        }

        if (field.signed() && !field.signSeparate() && isNumericType(targetType)) {
            return deserializeDisplayWithEmbeddedSignDirect(data, offset, length, field, targetType, charset);
        }

        if (!field.signed() && field.decimalDigits() > 0 && isNumericType(targetType)) {
            return deserializeDisplayWithImpliedDecimalDirect(data, offset, length, field, targetType, charset);
        }

        return deserializeDisplayStringDirect(data, offset, length, targetType, charset);
    }

    /**
     * Optimized DISPLAY deserialization using cached metadata.
     */
    private Object deserializeDisplayOptimized(byte[] data, int offset, int length,
                                              FieldMetadata fieldMeta, Charset charset) {
        CobolField field = fieldMeta.annotation;

        // Fast path for simple string fields (most common case)
        if (fieldMeta.isString && !field.signed() && field.decimalDigits() == 0) {
            return deserializeDisplayStringFast(data, offset, length, charset);
        }

        if (field.signed() && field.signSeparate()) {
            return deserializeDisplayWithSeparateSign(Arrays.copyOfRange(data, offset, offset + length),
                                                     field, fieldMeta.fieldType, charset);
        }

        if (field.signed() && !field.signSeparate() && fieldMeta.isNumeric) {
            return deserializeDisplayWithEmbeddedSignDirect(data, offset, length, field, fieldMeta.fieldType, charset);
        }

        if (!field.signed() && field.decimalDigits() > 0 && fieldMeta.isNumeric) {
            return deserializeDisplayWithImpliedDecimalDirect(data, offset, length, field, fieldMeta.fieldType, charset);
        }

        return deserializeDisplayStringDirect(data, offset, length, fieldMeta.fieldType, charset);
    }

    /**
     * Fast path for simple string deserialization.
     */
    private String deserializeDisplayStringFast(byte[] data, int offset, int length, Charset charset) {
        // Create string and trim (COBOL typically pads with spaces)
        String str = new String(data, offset, length, charset);

        // Use String.trim() for correctness - JVM is highly optimized for this
        String trimmed = str.trim();

        // Return trimmed string (empty string if all spaces)
        return trimmed;
    }

    /**
     * Deserialize DISPLAY string using direct array access.
     */
    private Object deserializeDisplayStringDirect(byte[] data, int offset, int length, Class<?> targetType, Charset charset) {
        String strValue = new String(data, offset, length, charset).trim();

        if (targetType == String.class) {
            return strValue;
        }

        if (strValue.isEmpty()) {
            return getDefaultValue(targetType);
        }

        return switch (targetType.getSimpleName()) {
            case "Integer", "int" -> Integer.parseInt(strValue);
            case "Long", "long" -> Long.parseLong(strValue);
            case "BigDecimal" -> new BigDecimal(strValue);
            case "BigInteger" -> new BigInteger(strValue);
            default -> strValue;
        };
    }

    /**
     * Deserialize DISPLAY with implied decimal using direct array access.
     */
    private Object deserializeDisplayWithImpliedDecimalDirect(byte[] data, int offset, int length,
                                                              CobolField field, Class<?> targetType, Charset charset) {
        return switch (targetType.getSimpleName()) {
            case "Integer", "int" -> DisplayNumericUtil.parseUnsignedInt(data, offset, length, charset, field.decimalDigits());
            case "Long", "long" -> DisplayNumericUtil.parseUnsignedLong(data, offset, length, charset, field.decimalDigits());
            case "BigDecimal" -> DisplayNumericUtil.parseUnsignedWithImpliedDecimal(data, offset, length, charset, field.decimalDigits());
            case "BigInteger" -> DisplayNumericUtil.parseUnsignedWithImpliedDecimal(data, offset, length, charset, field.decimalDigits()).toBigInteger();
            default -> getDefaultValue(targetType);
        };
    }

    /**
     * Deserialize DISPLAY with embedded sign using direct array access.
     */
    private Object deserializeDisplayWithEmbeddedSignDirect(byte[] data, int offset, int length,
                                                            CobolField field, Class<?> targetType, Charset charset) {
        return switch (targetType.getSimpleName()) {
            case "Integer", "int" -> SignedNumericUtil.parseSignedInt(data, offset, length, charset);
            case "Long", "long" -> SignedNumericUtil.parseSignedLong(data, offset, length, charset);
            case "BigDecimal" -> SignedNumericUtil.parseSignedBigDecimal(data, offset, length, charset, field.decimalDigits());
            case "BigInteger" -> SignedNumericUtil.parseSignedBigDecimal(data, offset, length, charset, 0).toBigInteger();
            default -> getDefaultValue(targetType);
        };
    }

    /**
     * Deserialize COMP using direct array access.
     */
    private Object deserializeCompDirect(byte[] data, int offset, int length, Class<?> targetType) {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length);

        return switch (targetType.getSimpleName()) {
            case "Short", "short" -> buffer.getShort();
            case "Integer", "int" -> length == 2 ? (int) buffer.getShort() : buffer.getInt();
            case "Long", "long" -> length == 8 ? buffer.getLong() : (long) buffer.getInt();
            case "BigInteger" -> BigInteger.valueOf(length == 8 ? buffer.getLong() : (long) buffer.getInt());
            default -> 0;
        };
    }

    /**
     * Deserialize COMP-3 using direct array access.
     */
    private Object deserializeComp3Direct(byte[] data, int offset, int length, CobolField field) {
        StringBuilder digits = new StringBuilder();

        for (int i = 0; i < length - 1; i++) {
            int highNibble = (data[offset + i] >> 4) & 0x0F;
            int lowNibble = data[offset + i] & 0x0F;
            digits.append(highNibble).append(lowNibble);
        }

        int totalDigits = field.integerDigits() + field.decimalDigits();
        int lastDigit = (data[offset + length - 1] >> 4) & 0x0F;
        int sign = data[offset + length - 1] & 0x0F;

        if (totalDigits % 2 != 0) {
            digits.append(lastDigit);
        }

        boolean isNegative = (sign == 0x0D || sign == 0x0B);
        BigInteger biValue = new BigInteger(digits.toString());
        if (isNegative) {
            biValue = biValue.negate();
        }

        if (field.decimalDigits() > 0) {
            BigDecimal bdValue = new BigDecimal(biValue);
            return bdValue.divide(BigDecimal.TEN.pow(field.decimalDigits()), field.decimalDigits(), RoundingMode.HALF_UP).stripTrailingZeros();
        }

        // No decimal digits - convert to appropriate integer type
        return convertToIntegerType(biValue, field.integerDigits());
    }

    /**
     * Deserialize ZONED-DECIMAL using direct array access.
     */
    private Object deserializeZonedDecimalDirect(byte[] data, int offset, int length, CobolField field) {
        StringBuilder digits = new StringBuilder();
        boolean isNegative = false;

        for (int i = 0; i < length; i++) {
            int digit = data[offset + i] & 0x0F;
            digits.append(digit);
            if (i == length - 1) {
                int zone = data[offset + i] & 0xF0;
                isNegative = (zone == 0xD0 || zone == 0xB0);
            }
        }

        BigInteger biValue = new BigInteger(digits.toString());
        if (isNegative) {
            biValue = biValue.negate();
        }

        if (field.decimalDigits() > 0) {
            BigDecimal bdValue = new BigDecimal(biValue);
            return bdValue.divide(BigDecimal.TEN.pow(field.decimalDigits()), field.decimalDigits(), RoundingMode.HALF_UP);
        }

        return new BigDecimal(biValue);
    }

    // ==== OLD METHODS KEPT FOR NESTED CLASS DESERIALIZATION ====

    /**
     * Deserialize a single field.
     */
    private Object deserializeField(byte[] data, CobolField cobolField, Class<?> fieldType, Charset charset) throws DeserializationException {
        if (cobolField.occurs() > 1) {
            return deserializeArray(data, cobolField, fieldType, charset);
        }

        return switch (cobolField.type()) {
            case DISPLAY -> deserializeDisplay(data, cobolField, fieldType, charset);
            case COMP, COMP_5 -> deserializeComp(data, fieldType);
            case COMP_1 -> ByteBuffer.wrap(data).getFloat();
            case COMP_2 -> ByteBuffer.wrap(data).getDouble();
            case COMP_3, PACKED_DECIMAL -> deserializeComp3(data, cobolField);
            case ZONED_DECIMAL -> deserializeZonedDecimal(data, cobolField);
        };
    }

    /**
     * Deserialize array field.
     */
    private Object deserializeArray(byte[] data, CobolField cobolField, Class<?> fieldType, Charset charset) throws DeserializationException {
        Class<?> componentType = fieldType.getComponentType();
        Object array = Array.newInstance(componentType, cobolField.occurs());

        if (isNestedClassType(componentType)) {
            deserializeNestedClassArray(data, cobolField, componentType, array, charset);
        } else {
            deserializePrimitiveArray(data, cobolField, componentType, array, charset);
        }

        return array;
    }

    private void deserializeNestedClassArray(byte[] data, CobolField cobolField, Class<?> componentType,
                                             Object array, Charset charset) throws DeserializationException {
        int elementLength = calculateNestedClassLength(componentType);
        for (int i = 0; i < cobolField.occurs(); i++) {
            byte[] elementData = Arrays.copyOfRange(data, i * elementLength, (i + 1) * elementLength);
            Array.set(array, i, deserialize(elementData, componentType, charset));
        }
    }

    private void deserializePrimitiveArray(byte[] data, CobolField cobolField, Class<?> componentType,
                                           Object array, Charset charset) throws DeserializationException {
        int elementLength = CobolFieldUtil.calculateFieldLength(cobolField);
        CobolField singleField = createSingleOccurField(cobolField);

        for (int i = 0; i < cobolField.occurs(); i++) {
            byte[] elementData = Arrays.copyOfRange(data, i * elementLength, (i + 1) * elementLength);
            Object element = deserializeField(elementData, singleField, componentType, charset);
            Array.set(array, i, element);
        }
    }

    /**
     * Check if a type is a nested class with COBOL fields.
     */
    private boolean isNestedClassType(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> field.isAnnotationPresent(CobolField.class));
    }

    /**
     * Calculate the total length of a nested class by summing all its fields.
     */
    private int calculateNestedClassLength(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(CobolField.class))
                .mapToInt(field -> calculateFieldLength(field, field.getAnnotation(CobolField.class)))
                .sum();
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
            public CobolDataType type() {
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
     * Deserialize DISPLAY field.
     */
    private Object deserializeDisplay(byte[] data, CobolField field, Class<?> targetType, Charset charset) {
        if (field.signed() && field.signSeparate()) {
            return deserializeDisplayWithSeparateSign(data, field, targetType, charset);
        }

        if (field.signed() && !field.signSeparate() && isNumericType(targetType)) {
            return deserializeDisplayWithEmbeddedSign(data, field, targetType, charset);
        }

        if (!field.signed() && field.decimalDigits() > 0 && isNumericType(targetType)) {
            return deserializeDisplayWithImpliedDecimal(data, field, targetType, charset);
        }

        return deserializeDisplayString(data, targetType, charset);
    }

    private boolean isNumericType(Class<?> type) {
        return type == Integer.class || type == int.class ||
                type == Long.class || type == long.class ||
                type == BigDecimal.class || type == BigInteger.class;
    }

    private Object deserializeDisplayString(byte[] data, Class<?> targetType, Charset charset) {
        String strValue = new String(data, charset).trim();

        if (targetType == String.class) {
            return strValue;
        }

        if (strValue.isEmpty()) {
            return getDefaultValue(targetType);
        }

        return switch (targetType.getSimpleName()) {
            case "Integer", "int" -> Integer.parseInt(strValue);
            case "Long", "long" -> Long.parseLong(strValue);
            case "BigDecimal" -> new BigDecimal(strValue);
            case "BigInteger" -> new BigInteger(strValue);
            default -> strValue;
        };
    }

    /**
     * Deserialize DISPLAY field with implied decimal (V in PIC clause).
     */
    private Object deserializeDisplayWithImpliedDecimal(byte[] data, CobolField field, Class<?> targetType, Charset charset) {
        return switch (targetType.getSimpleName()) {
            case "Integer", "int" -> DisplayNumericUtil.parseUnsignedInt(data, charset, field.decimalDigits());
            case "Long", "long" -> DisplayNumericUtil.parseUnsignedLong(data, charset, field.decimalDigits());
            case "BigDecimal" ->
                    DisplayNumericUtil.parseUnsignedWithImpliedDecimal(data, charset, field.decimalDigits());
            case "BigInteger" ->
                    DisplayNumericUtil.parseUnsignedWithImpliedDecimal(data, charset, field.decimalDigits()).toBigInteger();
            default -> getDefaultValue(targetType);
        };
    }

    /**
     * Deserialize DISPLAY field with embedded sign (overpunch notation).
     */
    private Object deserializeDisplayWithEmbeddedSign(byte[] data, CobolField field, Class<?> targetType, Charset charset) {
        return switch (targetType.getSimpleName()) {
            case "Integer", "int" -> SignedNumericUtil.parseSignedInt(data, charset);
            case "Long", "long" -> SignedNumericUtil.parseSignedLong(data, charset);
            case "BigDecimal" -> SignedNumericUtil.parseSignedBigDecimal(data, charset, field.decimalDigits());
            case "BigInteger" -> SignedNumericUtil.parseSignedBigDecimal(data, charset, 0).toBigInteger();
            default -> getDefaultValue(targetType);
        };
    }

    /**
     * Deserialize DISPLAY field with SIGN LEADING/TRAILING SEPARATE.
     */
    private Object deserializeDisplayWithSeparateSign(byte[] data, CobolField field, Class<?> targetType, Charset charset) {
        if (data.length == 0) {
            return getDefaultValue(targetType);
        }

        SignAndDigits sad = extractSignAndDigits(data, field, charset);
        if (sad.digits.isEmpty()) {
            return getDefaultValue(targetType);
        }

        BigDecimal value = parseBigDecimalValue(sad.digits, field.decimalDigits());
        if (sad.isNegative) {
            value = value.negate();
        }

        return convertToTargetType(value, targetType);
    }

    private SignAndDigits extractSignAndDigits(byte[] data, CobolField field, Charset charset) {
        boolean isLeading = "LEADING".equalsIgnoreCase(field.signPosition());

        byte signByte = isLeading ? data[0] : data[data.length - 1];
        byte[] digitBytes = new byte[data.length - 1];

        if (isLeading) {
            System.arraycopy(data, 1, digitBytes, 0, digitBytes.length);
        } else {
            System.arraycopy(data, 0, digitBytes, 0, digitBytes.length);
        }

        boolean isNegative = new String(new byte[]{signByte}, charset).equals("-");
        String digits = new String(digitBytes, charset).trim();

        return new SignAndDigits(isNegative, digits);
    }

    private BigDecimal parseBigDecimalValue(String digits, int decimalDigits) {
        if (decimalDigits > 0) {
            return new BigDecimal(new BigInteger(digits), decimalDigits);
        }
        return new BigDecimal(digits);
    }

    private Object convertToTargetType(BigDecimal value, Class<?> targetType) {
        return switch (targetType.getSimpleName()) {
            case "BigDecimal" -> value;
            case "Integer", "int" -> value.intValue();
            case "Long", "long" -> value.longValue();
            case "Double", "double" -> value.doubleValue();
            case "Float", "float" -> value.floatValue();
            case "BigInteger" -> value.toBigInteger();
            case "String" -> value.toPlainString();
            default -> value;
        };
    }

    /**
     * Get default value for a target type.
     */
    private Object getDefaultValue(Class<?> targetType) {
        return switch (targetType.getSimpleName()) {
            case "String" -> "";
            case "Integer", "int" -> 0;
            case "Long", "long" -> 0L;
            case "BigDecimal" -> BigDecimal.ZERO;
            case "BigInteger" -> BigInteger.ZERO;
            default -> null;
        };
    }

    /**
     * Deserialize COMP/BINARY field.
     */
    private Object deserializeComp(byte[] data, Class<?> targetType) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        long value = switch (data.length) {
            case 2 -> buffer.getShort();
            case 4 -> buffer.getInt();
            default -> buffer.getLong();
        };

        return switch (targetType.getSimpleName()) {
            case "Short", "short" -> (short) value;
            case "Integer", "int" -> (int) value;
            default -> value;
        };
    }

    /**
     * Deserialize COMP-3 (packed decimal) field.
     */
    private Object deserializeComp3(byte[] data, CobolField field) {
        BigInteger bigIntValue = extractPackedDecimalValue(data, field);

        if (field.decimalDigits() == 0) {
            return convertToIntegerType(bigIntValue, field.integerDigits());
        }

        BigDecimal result = new BigDecimal(bigIntValue)
                .setScale(field.decimalDigits(), RoundingMode.UNNECESSARY)
                .divide(BigDecimal.TEN.pow(field.decimalDigits()), RoundingMode.UNNECESSARY);

        return result.stripTrailingZeros();
    }

    private BigInteger extractPackedDecimalValue(byte[] data, CobolField field) {
        StringBuilder digits = new StringBuilder();
        int totalDigits = field.integerDigits() + field.decimalDigits();
        boolean isOddDigits = (totalDigits % 2) != 0;

        for (int i = 0; i < data.length - 1; i++) {
            digits.append((data[i] >> 4) & 0x0F);
            digits.append(data[i] & 0x0F);
        }

        int lastDigit = (data[data.length - 1] >> 4) & 0x0F;
        int sign = data[data.length - 1] & 0x0F;

        if (isOddDigits) {
            digits.append(lastDigit);
        }

        BigInteger value = new BigInteger(digits.toString());
        return (sign == 0x0D || sign == 0x0B) ? value.negate() : value;
    }

    private Object convertToIntegerType(BigInteger value, int integerDigits) {
        if (integerDigits <= 9) return value.intValue();
        if (integerDigits <= 18) return value.longValue();
        return value;
    }

    /**
     * Deserialize ZONED-DECIMAL field.
     */
    private Object deserializeZonedDecimal(byte[] data, CobolField field) {
        StringBuilder digits = new StringBuilder();

        for (int i = 0; i < data.length - 1; i++) {
            digits.append(data[i] & 0x0F);
        }

        int lastDigit = data[data.length - 1] & 0x0F;
        int zone = (data[data.length - 1] >> 4) & 0x0F;
        digits.append(lastDigit);

        BigDecimal result = new BigDecimal(new BigInteger(digits.toString()))
                .setScale(field.decimalDigits(), RoundingMode.UNNECESSARY)
                .divide(BigDecimal.TEN.pow(field.decimalDigits()), RoundingMode.UNNECESSARY);

        if (zone == 0x0D || zone == 0x0B) {
            result = result.negate();
        }

        return result.stripTrailingZeros();
    }

    private record SignAndDigits(boolean isNegative, String digits) {
    }
}
