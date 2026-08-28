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

/**
 * Serializes POJO instances to COBOL binary format.
 * Optimized with field metadata caching and pre-allocated buffers.
 */
public class CobolSerializer {

    private static final Logger logger = LoggerFactory.getLogger(CobolSerializer.class);

    /**
     * Cached, REDEFINES-aware layout for a class: field metadata (including offset) plus
     * the total serialized size of the class.
     */
    private record ClassLayout(List<FieldMetadata> fields, int totalSize) {
    }

    // Cache for field metadata/layout to avoid repeated reflection and prevent classloader leaks
    private static final ClassValue<ClassLayout> FIELD_CACHE = new ClassValue<>() {
        @Override
        protected ClassLayout computeValue(Class<?> clazz) {
            return computeClassLayoutStatic(clazz);
        }
    };

    // Cache for single-occur field annotations to avoid expensive proxy creation and prevent classloader leaks
    private static final Map<CobolField, CobolField> SINGLE_OCCUR_CACHE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    // Cache for nested class type detection to avoid repeated reflection scans and prevent classloader leaks
    private static final ClassValue<Boolean> NESTED_CLASS_CACHE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return Arrays.stream(type.getDeclaredFields())
                    .anyMatch(f -> f.isAnnotationPresent(CobolField.class));
        }
    };

    /**
     * Cached field metadata for performance.
     *
     * @param offset       start offset of this field within its enclosing class/record, computed
     *                     with REDEFINES awareness (alternate views share the same offset)
     * @param scaleFactor pre-computed {@code BigDecimal.TEN.pow(decimalDigits)} for COMP-3 / ZONED-DECIMAL
     *                    fields with decimal digits, or {@code null} if no scaling is needed
     */
    private record FieldMetadata(Field field, CobolField annotation, int fieldSize, int baseLength, int offset,
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

        ClassLayout layout = getClassLayout(pojo.getClass());

        byte[] buffer = new byte[layout.totalSize()];
        serializeFields(pojo, layout.fields(), buffer, 0, charset);
        return buffer;
    }

    /**
     * Serialize all fields of a POJO into a buffer at the given offset.
     */
    private void serializeFields(Object pojo, List<FieldMetadata> fields, byte[] buffer,
                                 int startOffset, Charset charset) throws SerializationException {
        for (FieldMetadata fieldMeta : fields) {
            writeFieldDirect(fieldMeta, pojo, buffer, startOffset + fieldMeta.offset, charset);
        }
    }

    /**
     * Get cached class layout or compute and cache it.
     */
    private ClassLayout getClassLayout(Class<?> clazz) {
        return FIELD_CACHE.get(clazz);
    }

    /**
     * Get cached field metadata or compute and cache it.
     */
    private List<FieldMetadata> getFieldMetadata(Class<?> clazz) {
        return getClassLayout(clazz).fields();
    }

    /**
     * Compute field metadata and REDEFINES-aware layout for a class.
     * Uses {@link CobolFieldUtil#calculateFieldLength} for correct byte sizes across all COBOL types.
     */
    private static ClassLayout computeClassLayoutStatic(Class<?> clazz) {
        Field[] declaredFields = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(CobolField.class))
                .toArray(Field[]::new);

        int n = declaredFields.length;
        CobolField[] annotations = new CobolField[n];
        int[] baseLengths = new int[n];
        int[] sizes = new int[n];
        String[] names = new String[n];
        String[] redefinesTargets = new String[n];

        for (int i = 0; i < n; i++) {
            Field f = declaredFields[i];
            CobolField annotation = f.getAnnotation(CobolField.class);
            annotations[i] = annotation;

            int baseLength;
            Class<?> fieldType = f.getType();
            if (fieldType.isArray() && isNestedClassType(fieldType.getComponentType())) {
                baseLength = computeNestedClassSize(fieldType.getComponentType());
            } else {
                baseLength = CobolFieldUtil.calculateFieldLength(annotation);
            }
            baseLengths[i] = baseLength;
            sizes[i] = baseLength * Math.max(1, annotation.occurs());
            names[i] = annotation.name().isEmpty() ? f.getName() : annotation.name();
            redefinesTargets[i] = annotation.redefines();
        }

        CobolFieldUtil.LayoutResult layout = CobolFieldUtil.computeLayout(names, redefinesTargets, sizes);

        List<FieldMetadata> fields = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CobolField annotation = annotations[i];

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

            fields.add(new FieldMetadata(declaredFields[i], annotation, sizes[i], baseLengths[i], layout.offsets()[i],
                    scaleFactor, isNumericPicture(annotation.picture())));
        }

        return new ClassLayout(fields, layout.totalSize());
    }

    /**
     * Compute the total byte size of a nested class, honoring REDEFINES so that alternate
     * views share storage instead of accumulating additional space.
     */
    private static int computeNestedClassSize(Class<?> clazz) {
        return FIELD_CACHE.get(clazz).totalSize();
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
     * @param scaleFactor      pre-computed scale factor for COMP-3/ZONED-DECIMAL, or {@code null}
     * @param isNumericPicture cached result of whether the picture is numeric
     */
    private void serializeSimpleFieldDirect(Object value, CobolField cobolField, int baseLength,
                                            BigDecimal scaleFactor, boolean isNumericPicture,
                                            byte[] buffer, int offset, Charset charset) {
        switch (cobolField.type()) {
            case DISPLAY ->
                    serializeDisplayDirect(value, cobolField, baseLength, isNumericPicture, buffer, offset, charset);
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
        CobolField cached = SINGLE_OCCUR_CACHE.get(original);
        if (cached == null) {
            synchronized (SINGLE_OCCUR_CACHE) {
                cached = SINGLE_OCCUR_CACHE.get(original);
                if (cached == null) {
                    cached = createSingleOccurField(original);
                    SINGLE_OCCUR_CACHE.put(original, cached);
                }
            }
        }
        return cached;
    }

    /**
     * Check if a type is a nested class with COBOL fields.
     */
    private static boolean isNestedClassType(Class<?> type) {
        return NESTED_CLASS_CACHE.get(type);
    }

    private static boolean isNumericPicture(String picture) {
        return picture.startsWith("9") || picture.startsWith("S9");
    }

    /**
     * Create a CobolField annotation with occurs=1 for array element processing.
     */
    private CobolField createSingleOccurField(CobolField original) {
        return CobolFieldUtil.withSingleOccur(original);
    }
}
