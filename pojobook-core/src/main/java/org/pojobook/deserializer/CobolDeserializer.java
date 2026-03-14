package org.pojobook.deserializer;

import org.pojobook.annotation.CobolField;
import org.pojobook.annotation.CobolRecord;
import org.pojobook.exception.DeserializationException;
import org.pojobook.util.CharsetMode;
import org.pojobook.util.CobolFieldUtil;
import org.pojobook.util.DisplayNumericUtil;
import org.pojobook.util.SignedNumericUtil;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
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

    // Cache for nested class type detection to avoid repeated reflection scans
    private static final Map<Class<?>, Boolean> NESTED_CLASS_CACHE = new ConcurrentHashMap<>();

    // Cache for nested class length to avoid repeated reflection + calculation
    private static final Map<Class<?>, Integer> NESTED_CLASS_LENGTH_CACHE = new ConcurrentHashMap<>();

    // Type category constants for fast int-based dispatch (avoids getSimpleName() allocation)
    private static final int TYPE_INT = 1;
    private static final int TYPE_LONG = 2;
    private static final int TYPE_SHORT = 3;
    private static final int TYPE_BIG_DECIMAL = 4;
    private static final int TYPE_BIG_INTEGER = 5;
    private static final int TYPE_DOUBLE = 6;
    private static final int TYPE_FLOAT = 7;
    private static final int TYPE_STRING = 8;
    private static final int TYPE_OTHER = 0;

    private static int classifyType(Class<?> type) {
        if (type == int.class || type == Integer.class) return TYPE_INT;
        if (type == long.class || type == Long.class) return TYPE_LONG;
        if (type == String.class) return TYPE_STRING;
        if (type == BigDecimal.class) return TYPE_BIG_DECIMAL;
        if (type == BigInteger.class) return TYPE_BIG_INTEGER;
        if (type == short.class || type == Short.class) return TYPE_SHORT;
        if (type == double.class || type == Double.class) return TYPE_DOUBLE;
        if (type == float.class || type == Float.class) return TYPE_FLOAT;
        return TYPE_OTHER;
    }

    /**
     * Cached field metadata for performance.
     */
    private record FieldMetadata(Field field, CobolField annotation, int fieldSize, int baseLength,
                                 Class<?> fieldType, boolean isNumeric, boolean isString, int typeCategory) {
        public FieldMetadata {
            field.setAccessible(true);
        }
    }

    /**
     * Deserialize COBOL binary data to a POJO.
     */
    public <T> T deserialize(byte[] data, Class<T> clazz, Charset charset) throws DeserializationException {
        return deserializeInternal(data, 0, data.length, clazz, charset);
    }

    private <T> T deserializeInternal(byte[] data, int startOffset, int limit, Class<T> clazz, Charset charset)
            throws DeserializationException {
        validateClass(clazz);
        T instance = createInstance(clazz);

        List<FieldMetadata> fields = getFieldMetadata(clazz);
        int offset = startOffset;

        for (FieldMetadata fieldMeta : fields) {
            validateDataLength(data, offset, fieldMeta.fieldSize, limit, fieldMeta.field.getName());

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
            case COMP, COMP_5 -> deserializeCompDirect(data, offset, fieldMeta.baseLength, fieldMeta.typeCategory);
            case COMP_1 -> CobolFieldDeserializer.deserializeComp1(data, offset);
            case COMP_2 -> CobolFieldDeserializer.deserializeComp2(data, offset);
            case COMP_3, PACKED_DECIMAL ->
                    deserializeComp3Direct(data, offset, fieldMeta.baseLength, fieldMeta.annotation);
            case ZONED_DECIMAL ->
                    deserializeZonedDecimalDirect(data, offset, fieldMeta.baseLength, fieldMeta.annotation);
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
                    int typeCategory = classifyType(componentType);

                    return new FieldMetadata(f, annotation, size, baseLength, fieldType, isNumeric, isString, typeCategory);
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

        int typeCategory = classifyType(fieldType);
        return switch (cobolField.type()) {
            case DISPLAY -> deserializeDisplayDirect(data, offset, length, cobolField, typeCategory, charset);
            case COMP, COMP_5 -> deserializeCompDirect(data, offset, length, typeCategory);
            case COMP_1 -> CobolFieldDeserializer.deserializeComp1(data, offset);
            case COMP_2 -> CobolFieldDeserializer.deserializeComp2(data, offset);
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

    private void validateDataLength(byte[] data, int offset, int fieldLength, int limit, String fieldName) throws DeserializationException {
        int effectiveLimit = Math.min(limit, data.length);
        if ((long) offset + fieldLength > effectiveLimit) {
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
                                            int typeCategory, Charset charset) {
        if (field.signed() && field.signSeparate()) {
            return deserializeDisplayWithSeparateSign(data, offset, length, field, typeCategory, charset);
        }

        if (field.signed() && !field.signSeparate() && isNumericTypeCategory(typeCategory)) {
            return deserializeDisplayWithEmbeddedSignDirect(data, offset, length, field, typeCategory, charset);
        }

        if (!field.signed() && field.decimalDigits() > 0 && isNumericTypeCategory(typeCategory)) {
            return deserializeDisplayWithImpliedDecimalDirect(data, offset, length, field, typeCategory, charset);
        }

        return deserializeDisplayStringDirect(data, offset, length, typeCategory, charset);
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
            return deserializeDisplayWithSeparateSign(data, offset, length,
                    field, fieldMeta.typeCategory, charset);
        }

        if (field.signed() && !field.signSeparate() && fieldMeta.isNumeric) {
            return deserializeDisplayWithEmbeddedSignDirect(data, offset, length, field, fieldMeta.typeCategory, charset);
        }

        if (!field.signed() && field.decimalDigits() > 0 && fieldMeta.isNumeric) {
            return deserializeDisplayWithImpliedDecimalDirect(data, offset, length, field, fieldMeta.typeCategory, charset);
        }

        return deserializeDisplayStringDirect(data, offset, length, fieldMeta.typeCategory, charset);
    }

    /**
     * Fast path for simple string deserialization.
     */
    private String deserializeDisplayStringFast(byte[] data, int offset, int length, Charset charset) {
        return CobolFieldDeserializer.deserializeDisplayString(data, offset, length, charset);
    }

    /**
     * Deserialize DISPLAY string using direct array access.
     */
    private Object deserializeDisplayStringDirect(byte[] data, int offset, int length, int typeCategory, Charset charset) {
        String strValue = CobolFieldDeserializer.deserializeDisplayString(data, offset, length, charset);

        if (typeCategory == TYPE_STRING) {
            return strValue;
        }

        if (strValue.isEmpty()) {
            return getDefaultValue(typeCategory);
        }

        return switch (typeCategory) {
            case TYPE_INT -> Integer.parseInt(strValue);
            case TYPE_LONG -> Long.parseLong(strValue);
            case TYPE_BIG_DECIMAL -> new BigDecimal(strValue);
            case TYPE_BIG_INTEGER -> new BigInteger(strValue);
            default -> strValue;
        };
    }

    /**
     * Deserialize DISPLAY with implied decimal using direct array access.
     */
    private Object deserializeDisplayWithImpliedDecimalDirect(byte[] data, int offset, int length,
                                                              CobolField field, int typeCategory, Charset charset) {
        return switch (typeCategory) {
            case TYPE_INT ->
                    CobolFieldDeserializer.deserializeDisplayIntegerWithDecimal(data, offset, length, charset, field.decimalDigits());
            case TYPE_LONG ->
                    CobolFieldDeserializer.deserializeDisplayLongWithDecimal(data, offset, length, charset, field.decimalDigits());
            case TYPE_BIG_DECIMAL ->
                    CobolFieldDeserializer.deserializeDisplayBigDecimalWithDecimal(data, offset, length, charset, field.decimalDigits());
            case TYPE_BIG_INTEGER ->
                    CobolFieldDeserializer.deserializeDisplayBigDecimalWithDecimal(data, offset, length, charset, field.decimalDigits()).toBigInteger();
            default -> getDefaultValue(typeCategory);
        };
    }

    /**
     * Deserialize DISPLAY with embedded sign using direct array access.
     */
    private Object deserializeDisplayWithEmbeddedSignDirect(byte[] data, int offset, int length,
                                                            CobolField field, int typeCategory, Charset charset) {
        return switch (typeCategory) {
            case TYPE_INT ->
                    CobolFieldDeserializer.deserializeDisplaySignedInteger(data, offset, length, charset);
            case TYPE_LONG -> CobolFieldDeserializer.deserializeDisplaySignedLong(data, offset, length, charset);
            case TYPE_BIG_DECIMAL ->
                    CobolFieldDeserializer.deserializeDisplaySignedBigDecimal(data, offset, length, charset, field.decimalDigits());
            case TYPE_BIG_INTEGER ->
                    CobolFieldDeserializer.deserializeDisplaySignedBigInteger(data, offset, length, charset);
            default -> getDefaultValue(typeCategory);
        };
    }

    /**
     * Deserialize COMP using direct array access.
     */
    private Object deserializeCompDirect(byte[] data, int offset, int length, int typeCategory) {
        return switch (typeCategory) {
            case TYPE_SHORT -> CobolFieldDeserializer.deserializeCompShort(data, offset, length);
            case TYPE_INT -> CobolFieldDeserializer.deserializeCompInteger(data, offset, length);
            case TYPE_LONG -> CobolFieldDeserializer.deserializeCompLong(data, offset, length);
            case TYPE_BIG_INTEGER -> CobolFieldDeserializer.deserializeCompBigInteger(data, offset, length);
            default -> 0;
        };
    }

    /**
     * Deserialize COMP-3 using direct array access.
     */
    private Object deserializeComp3Direct(byte[] data, int offset, int length, CobolField field) {
        int totalDigits = field.integerDigits() + field.decimalDigits();

        if (field.decimalDigits() > 0) {
            return CobolFieldDeserializer.deserializeComp3BigDecimal(data, offset, length, totalDigits, field.decimalDigits());
        }

        // No decimal digits - convert to appropriate integer type
        return convertToIntegerType(CobolFieldDeserializer.deserializeComp3BigInteger(data, offset, length, totalDigits),
                field.integerDigits());
    }

    /**
     * Deserialize ZONED-DECIMAL using direct array access.
     */
    private Object deserializeZonedDecimalDirect(byte[] data, int offset, int length, CobolField field) {
        if (field.decimalDigits() > 0) {
            return CobolFieldDeserializer.deserializeZonedDecimalBigDecimal(data, offset, length, field.decimalDigits());
        }

        return CobolFieldDeserializer.deserializeZonedDecimalBigDecimalNoDecimals(data, offset, length);
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
            case COMP_1 -> CobolFieldDeserializer.deserializeComp1(data, 0);
            case COMP_2 -> CobolFieldDeserializer.deserializeComp2(data, 0);
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
        return NESTED_CLASS_CACHE.computeIfAbsent(type, t ->
                Arrays.stream(t.getDeclaredFields())
                        .anyMatch(f -> f.isAnnotationPresent(CobolField.class)));
    }

    /**
     * Calculate the total length of a nested class by summing all its fields.
     * Result is cached per class to avoid repeated reflection scans.
     */
    private int calculateNestedClassLength(Class<?> clazz) {
        Integer cached = NESTED_CLASS_LENGTH_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }
        int length = Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(CobolField.class))
                .mapToInt(field -> calculateFieldLength(field, field.getAnnotation(CobolField.class)))
                .sum();
        NESTED_CLASS_LENGTH_CACHE.put(clazz, length);
        return length;
    }

    /**
     * Create a CobolField annotation with occurs=1 for array element processing.
     */
    private CobolField createSingleOccurField(CobolField original) {
        return CobolFieldUtil.withSingleOccur(original);
    }

    /**
     * Deserialize DISPLAY field.
     */
    private Object deserializeDisplay(byte[] data, CobolField field, Class<?> targetType, Charset charset) {
        int typeCategory = classifyType(targetType);

        if (field.signed() && field.signSeparate()) {
            return deserializeDisplayWithSeparateSign(data, 0, data.length, field, typeCategory, charset);
        }

        if (field.signed() && !field.signSeparate() && isNumericType(targetType)) {
            return deserializeDisplayWithEmbeddedSign(data, field, typeCategory, charset);
        }

        if (!field.signed() && field.decimalDigits() > 0 && isNumericType(targetType)) {
            return deserializeDisplayWithImpliedDecimal(data, field, typeCategory, charset);
        }

        return deserializeDisplayString(data, typeCategory, charset);
    }

    private boolean isNumericType(Class<?> type) {
        return type == Integer.class || type == int.class ||
                type == Long.class || type == long.class ||
                type == BigDecimal.class || type == BigInteger.class;
    }

    private static boolean isNumericTypeCategory(int typeCategory) {
        return typeCategory == TYPE_INT || typeCategory == TYPE_LONG ||
                typeCategory == TYPE_BIG_DECIMAL || typeCategory == TYPE_BIG_INTEGER;
    }

    private Object deserializeDisplayString(byte[] data, int typeCategory, Charset charset) {
        String strValue = CobolFieldDeserializer.deserializeDisplayString(data, 0, data.length, charset);

        if (typeCategory == TYPE_STRING) {
            return strValue;
        }

        if (strValue.isEmpty()) {
            return getDefaultValue(typeCategory);
        }

        return switch (typeCategory) {
            case TYPE_INT -> Integer.parseInt(strValue);
            case TYPE_LONG -> Long.parseLong(strValue);
            case TYPE_BIG_DECIMAL -> new BigDecimal(strValue);
            case TYPE_BIG_INTEGER -> new BigInteger(strValue);
            default -> strValue;
        };
    }

    /**
     * Deserialize DISPLAY field with implied decimal (V in PIC clause).
     */
    private Object deserializeDisplayWithImpliedDecimal(byte[] data, CobolField field, int typeCategory, Charset charset) {
        return switch (typeCategory) {
            case TYPE_INT -> DisplayNumericUtil.parseUnsignedInt(data, charset, field.decimalDigits());
            case TYPE_LONG -> DisplayNumericUtil.parseUnsignedLong(data, charset, field.decimalDigits());
            case TYPE_BIG_DECIMAL ->
                    DisplayNumericUtil.parseUnsignedWithImpliedDecimal(data, charset, field.decimalDigits());
            case TYPE_BIG_INTEGER ->
                    DisplayNumericUtil.parseUnsignedWithImpliedDecimal(data, charset, field.decimalDigits()).toBigInteger();
            default -> getDefaultValue(typeCategory);
        };
    }

    /**
     * Deserialize DISPLAY field with embedded sign (overpunch notation).
     */
    private Object deserializeDisplayWithEmbeddedSign(byte[] data, CobolField field, int typeCategory, Charset charset) {
        return switch (typeCategory) {
            case TYPE_INT -> SignedNumericUtil.parseSignedInt(data, charset);
            case TYPE_LONG -> SignedNumericUtil.parseSignedLong(data, charset);
            case TYPE_BIG_DECIMAL -> SignedNumericUtil.parseSignedBigDecimal(data, charset, field.decimalDigits());
            case TYPE_BIG_INTEGER -> SignedNumericUtil.parseSignedBigDecimal(data, charset, 0).toBigInteger();
            default -> getDefaultValue(typeCategory);
        };
    }

    /**
     * Deserialize DISPLAY field with SIGN LEADING/TRAILING SEPARATE.
     */
    private Object deserializeDisplayWithSeparateSign(byte[] data, int offset, int length,
                                                     CobolField field, int typeCategory, Charset charset) {
        if (length == 0) {
            return getDefaultValue(typeCategory);
        }

        SignAndDigits sad = extractSignAndDigits(data, offset, length, field, charset);
        if (sad.digits.isEmpty()) {
            return getDefaultValue(typeCategory);
        }

        BigDecimal value = parseBigDecimalValue(sad.digits, field.decimalDigits());
        if (sad.isNegative) {
            value = value.negate();
        }

        return convertToTargetType(value, typeCategory);
    }

    // Sign byte constants for known charsets
    private static final byte ASCII_MINUS  = 0x2D;  // '-'
    private static final byte EBCDIC_MINUS = 0x60;  // '-' in EBCDIC

    private SignAndDigits extractSignAndDigits(byte[] data, int offset, int length, CobolField field, Charset charset) {
        boolean isLeading = "LEADING".equalsIgnoreCase(field.signPosition());

        byte signByte = isLeading ? data[offset] : data[offset + length - 1];

        int digitOffset;
        int digitLength = length - 1;
        if (isLeading) {
            digitOffset = offset + 1;
        } else {
            digitOffset = offset;
        }

        // Detect sign directly from the byte value, avoiding String allocation
        boolean isNegative;
        int mode = CharsetMode.detect(charset);
        if (mode == CharsetMode.ASCII) {
            isNegative = signByte == ASCII_MINUS;
        } else if (mode == CharsetMode.EBCDIC) {
            isNegative = signByte == EBCDIC_MINUS;
        } else {
            // Unknown charset fallback: decode the single byte via charset
            isNegative = new String(new byte[]{signByte}, charset).equals("-");
        }

        String digits = new String(data, digitOffset, digitLength, charset).trim();

        return new SignAndDigits(isNegative, digits);
    }

    private BigDecimal parseBigDecimalValue(String digits, int decimalDigits) {
        if (decimalDigits > 0) {
            return new BigDecimal(new BigInteger(digits), decimalDigits);
        }
        return new BigDecimal(digits);
    }

    private Object convertToTargetType(BigDecimal value, int typeCategory) {
        return switch (typeCategory) {
            case TYPE_BIG_DECIMAL -> value;
            case TYPE_INT -> value.intValue();
            case TYPE_LONG -> value.longValue();
            case TYPE_DOUBLE -> value.doubleValue();
            case TYPE_FLOAT -> value.floatValue();
            case TYPE_BIG_INTEGER -> value.toBigInteger();
            case TYPE_STRING -> value.toPlainString();
            default -> value;
        };
    }

    /**
     * Get default value for a target type.
     */
    private Object getDefaultValue(int typeCategory) {
        return switch (typeCategory) {
            case TYPE_STRING -> "";
            case TYPE_INT -> 0;
            case TYPE_LONG -> 0L;
            case TYPE_BIG_DECIMAL -> BigDecimal.ZERO;
            case TYPE_BIG_INTEGER -> BigInteger.ZERO;
            default -> null;
        };
    }

    /**
     * Deserialize COMP/BINARY field.
     */
    private Object deserializeComp(byte[] data, Class<?> targetType) {
        int typeCategory = classifyType(targetType);
        return switch (typeCategory) {
            case TYPE_SHORT -> CobolFieldDeserializer.deserializeCompShort(data, 0, data.length);
            case TYPE_INT -> CobolFieldDeserializer.deserializeCompInteger(data, 0, data.length);
            case TYPE_BIG_INTEGER -> CobolFieldDeserializer.deserializeCompBigInteger(data, 0, data.length);
            default -> CobolFieldDeserializer.deserializeCompLong(data, 0, data.length);
        };
    }

    /**
     * Deserialize COMP-3 (packed decimal) field.
     */
    private Object deserializeComp3(byte[] data, CobolField field) {
        int totalDigits = field.integerDigits() + field.decimalDigits();

        if (field.decimalDigits() == 0) {
            return convertToIntegerType(
                    CobolFieldDeserializer.deserializeComp3BigInteger(data, 0, data.length, totalDigits),
                    field.integerDigits());
        }

        return CobolFieldDeserializer.deserializeComp3BigDecimal(data, 0, data.length, totalDigits, field.decimalDigits());
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
        if (field.decimalDigits() > 0) {
            return CobolFieldDeserializer.deserializeZonedDecimalBigDecimal(data, 0, data.length, field.decimalDigits());
        }

        return CobolFieldDeserializer.deserializeZonedDecimalBigDecimalNoDecimals(data, 0, data.length);
    }

    private record SignAndDigits(boolean isNegative, String digits) {
    }
}
