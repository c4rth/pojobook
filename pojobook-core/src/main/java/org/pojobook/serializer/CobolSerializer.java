package org.pojobook.serializer;

import org.pojobook.annotation.CobolField;
import org.pojobook.annotation.CobolRecord;
import org.pojobook.exception.SerializationException;
import org.pojobook.util.CobolFieldUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
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

    // Cache for nested class type detection to avoid repeated reflection scans
    private static final Map<Class<?>, Boolean> NESTED_CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * Cached field metadata for performance.
     *
     * @param scaleFactor pre-computed {@code BigDecimal.TEN.pow(decimalDigits)} for COMP-3 / ZONED-DECIMAL
     *                    fields with decimal digits, or {@code null} if no scaling is needed
     */
    private record FieldMetadata(Field field, CobolField annotation, int fieldSize, int baseLength,
                                 BigDecimal scaleFactor, boolean isNumericPicture) {
        public FieldMetadata {
            field.setAccessible(true);
        }
    }

    /**
     * Serialize a POJO to COBOL binary format.
     * <p>
     * Allocates a single {@code byte[]} of the exact required size and writes
     * all fields directly into it using {@code *Direct} methods, avoiding
     * intermediate byte[] allocations and ByteArrayOutputStream overhead.
     */
    public byte[] serialize(Object pojo, Charset charset) throws SerializationException {
        validatePojo(pojo);

        List<FieldMetadata> fields = getFieldMetadata(pojo.getClass());
        int totalSize = calculateTotalSize(fields);

        byte[] buffer = new byte[totalSize];
        serializeFields(pojo, fields, buffer, 0, charset);
        return buffer;
    }

    /**
     * Serialize all fields of a POJO into a buffer at the given offset.
     */
    private void serializeFields(Object pojo, List<FieldMetadata> fields, byte[] buffer,
                                 int startOffset, Charset charset) throws SerializationException {
        int offset = startOffset;
        for (FieldMetadata fieldMeta : fields) {
            writeFieldDirect(fieldMeta, pojo, buffer, offset, charset);
            offset += fieldMeta.fieldSize;
        }
    }

    /**
     * Get cached field metadata or compute and cache it.
     */
    private List<FieldMetadata> getFieldMetadata(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, this::computeFieldMetadata);
    }

    /**
     * Compute field metadata for a class.
     * Uses {@link CobolFieldUtil#calculateFieldLength} for correct byte sizes across all COBOL types.
     */
    private List<FieldMetadata> computeFieldMetadata(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(CobolField.class))
                .map(f -> {
                    CobolField annotation = f.getAnnotation(CobolField.class);

                    int baseLength;
                    Class<?> fieldType = f.getType();
                    if (fieldType.isArray() && isNestedClassType(fieldType.getComponentType())) {
                        baseLength = computeNestedClassSize(fieldType.getComponentType());
                    } else {
                        baseLength = CobolFieldUtil.calculateFieldLength(annotation);
                    }

                    int size = baseLength * Math.max(1, annotation.occurs());

                    // Pre-compute scale factor for COMP-3 / ZONED-DECIMAL with decimal digits
                    BigDecimal scaleFactor = null;
                    if (annotation.decimalDigits() > 0) {
                        var type = annotation.type();
                        if (type == org.pojobook.CobolDataType.COMP_3
                                || type == org.pojobook.CobolDataType.PACKED_DECIMAL
                                || type == org.pojobook.CobolDataType.ZONED_DECIMAL) {
                            scaleFactor = BigDecimal.TEN.pow(annotation.decimalDigits());
                        }
                    }

                    return new FieldMetadata(f, annotation, size, baseLength, scaleFactor,
                            isNumericPicture(annotation.picture()));
                })
                .toList();
    }

    /**
     * Compute the total byte size of a nested class by summing its COBOL field lengths.
     * Uses direct reflection instead of {@link #getFieldMetadata} to avoid recursive
     * {@code ConcurrentHashMap.computeIfAbsent} calls.
     */
    private int computeNestedClassSize(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(CobolField.class))
                .mapToInt(f -> {
                    CobolField ann = f.getAnnotation(CobolField.class);
                    int base;
                    if (f.getType().isArray() && isNestedClassType(f.getType().getComponentType())) {
                        base = computeNestedClassSize(f.getType().getComponentType());
                    } else {
                        base = CobolFieldUtil.calculateFieldLength(ann);
                    }
                    return base * Math.max(1, ann.occurs());
                })
                .sum();
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

    /**
     * Write a single field directly into the buffer at the given offset.
     */
    private void writeFieldDirect(FieldMetadata fieldMeta, Object pojo, byte[] buffer,
                                  int offset, Charset charset) throws SerializationException {
        try {
            Object value = fieldMeta.field.get(pojo);
            if (value == null) {
                // buffer is already zero-filled by Java, nothing to do
                return;
            }

            if (value.getClass().isArray()) {
                serializeArrayDirect(value, fieldMeta.annotation, fieldMeta.baseLength, buffer, offset, charset);
            } else {
                serializeSimpleFieldDirect(value, fieldMeta.annotation, fieldMeta.baseLength,
                        fieldMeta.scaleFactor, fieldMeta.isNumericPicture, buffer, offset, charset);
            }
        } catch (IllegalAccessException e) {
            logger.error("Error serializing field: {}", fieldMeta.field.getName(), e);
            throw new SerializationException("Serialization error field: " + fieldMeta.field.getName(), e);
        }
    }

    /**
     * Serialize a simple (non-array) field directly into the buffer.
     *
     * @param scaleFactor pre-computed scale factor for COMP-3/ZONED-DECIMAL, or {@code null}
     * @param isNumericPicture cached result of whether the picture is numeric
     */
    private void serializeSimpleFieldDirect(Object value, CobolField cobolField, int baseLength,
                                            BigDecimal scaleFactor, boolean isNumericPicture,
                                            byte[] buffer, int offset, Charset charset) {
        switch (cobolField.type()) {
            case DISPLAY -> serializeDisplayDirect(value, cobolField, baseLength, isNumericPicture, buffer, offset, charset);
            case COMP, COMP_5 -> CobolFieldSerializer.serializeCompDirect(buffer, offset, value,
                    cobolField.integerDigits() + cobolField.decimalDigits());
            case COMP_1 -> CobolFieldSerializer.serializeComp1Direct(buffer, offset, value);
            case COMP_2 -> CobolFieldSerializer.serializeComp2Direct(buffer, offset, value);
            case COMP_3, PACKED_DECIMAL -> CobolFieldSerializer.serializeComp3Direct(buffer, offset, value,
                    cobolField.integerDigits() + cobolField.decimalDigits(), scaleFactor);
            case ZONED_DECIMAL -> CobolFieldSerializer.serializeZonedDecimalDirect(buffer, offset, value,
                    cobolField.integerDigits() + cobolField.decimalDigits(), scaleFactor,
                    cobolField.signed());
        }
    }

    /**
     * Serialize a DISPLAY field directly into the buffer.
     */
    private void serializeDisplayDirect(Object value, CobolField field, int baseLength,
                                        boolean isNumericPicture, byte[] buffer, int offset, Charset charset) {
        int digitLength = baseLength;
        if (field.signed() && field.signSeparate()) {
            digitLength = baseLength - 1;
        }

        boolean isSignLeading = "LEADING".equalsIgnoreCase(field.signPosition());

        if (field.signed() && field.signSeparate() && value instanceof Number num) {
            CobolFieldSerializer.serializeDisplayWithSeparateSignDirect(buffer, offset, num,
                    digitLength, field.decimalDigits(), isSignLeading, charset);
        } else if (field.signed() && !field.signSeparate() && value instanceof Number num && isNumericPicture) {
            CobolFieldSerializer.serializeDisplayWithEmbeddedSignDirect(buffer, offset, num,
                    digitLength, field.decimalDigits(), charset);
        } else if (!field.signed() && field.decimalDigits() > 0 && value instanceof Number num && isNumericPicture) {
            CobolFieldSerializer.serializeDisplayWithImpliedDecimalDirect(buffer, offset, num,
                    digitLength, field.decimalDigits(), charset);
        } else {
            CobolFieldSerializer.serializeDisplayStringDirect(buffer, offset, value,
                    baseLength, isNumericPicture, charset);
        }
    }

    /**
     * Serialize an array field directly into the buffer.
     */
    private void serializeArrayDirect(Object arrayValue, CobolField cobolField, int elementSize,
                                      byte[] buffer, int offset, Charset charset) throws SerializationException {
        int length = Array.getLength(arrayValue);
        CobolField singleOccurField = getSingleOccurField(cobolField);

        // Pre-compute scale factor once for the whole array
        BigDecimal scaleFactor = null;
        if (singleOccurField.decimalDigits() > 0) {
            var type = singleOccurField.type();
            if (type == org.pojobook.CobolDataType.COMP_3
                    || type == org.pojobook.CobolDataType.PACKED_DECIMAL
                    || type == org.pojobook.CobolDataType.ZONED_DECIMAL) {
                scaleFactor = BigDecimal.TEN.pow(singleOccurField.decimalDigits());
            }
        }

        boolean isNumeric = isNumericPicture(singleOccurField.picture());

        for (int i = 0; i < length; i++) {
            Object element = Array.get(arrayValue, i);
            if (element == null) {
                continue; // buffer already zero-filled
            }

            int elementOffset = offset + i * elementSize;
            if (isNestedClassType(element.getClass())) {
                List<FieldMetadata> nestedFields = getFieldMetadata(element.getClass());
                serializeFields(element, nestedFields, buffer, elementOffset, charset);
            } else {
                serializeSimpleFieldDirect(element, singleOccurField, elementSize,
                        scaleFactor, isNumeric, buffer, elementOffset, charset);
            }
        }
    }

    /**
     * Get or create cached single-occur field annotation.
     */
    private CobolField getSingleOccurField(CobolField original) {
        return SINGLE_OCCUR_CACHE.computeIfAbsent(original, this::createSingleOccurField);
    }

    /**
     * Check if a type is a nested class with COBOL fields.
     */
    private boolean isNestedClassType(Class<?> type) {
        return NESTED_CLASS_CACHE.computeIfAbsent(type, t ->
                Arrays.stream(t.getDeclaredFields())
                        .anyMatch(f -> f.isAnnotationPresent(CobolField.class)));
    }

    private boolean isNumericPicture(String picture) {
        return picture.startsWith("9") || picture.startsWith("S9");
    }

    /**
     * Create a CobolField annotation with occurs=1 for array element processing.
     */
    private CobolField createSingleOccurField(CobolField original) {
        return CobolFieldUtil.withSingleOccur(original);
    }
}
