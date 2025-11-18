package org.pojobook.serializer;

import org.pojobook.annotation.CobolField;
import org.pojobook.annotation.CobolRecord;
import org.pojobook.exception.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
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
     * Serialize a DISPLAY field.
     */
    private byte[] serializeDisplay(Object value, int length, int decimalDigits, boolean isNumeric,
                                    boolean signed, boolean signSeparate, boolean isSignLeading,
                                    Charset charset) {
        if (signed && signSeparate && value instanceof Number) {
            return CobolFieldSerializer.serializeDisplayWithSeparateSign((Number) value, length, decimalDigits, isSignLeading, charset);
        }

        if (signed && !signSeparate && value instanceof Number && isNumeric) {
            return CobolFieldSerializer.serializeDisplayWithEmbeddedSign((Number) value, length, decimalDigits, charset);
        }

        if (!signed && decimalDigits > 0 && value instanceof Number && isNumeric) {
            return CobolFieldSerializer.serializeDisplayWithImpliedDecimal((Number) value, length, decimalDigits, charset);
        }

        return CobolFieldSerializer.serializeDisplayString(value, length, isNumeric, charset);
    }

    /**
     * Serialize DISPLAY field.
     */
    private byte[] serializeDisplay(Object value, CobolField field, Charset charset) {
        int length = field.length() > 0 ? field.length() : field.integerDigits() + field.decimalDigits();

        return serializeDisplay(value, length, field.decimalDigits(), isNumericPicture(field.picture()),
                field.signed(), field.signSeparate(), "LEADING".equalsIgnoreCase(field.signPosition()), charset);
    }

    /**
     * Serialize DISPLAY field with pre-calculated length (optimized).
     */
    private byte[] serializeDisplayWithLength(Object value, CobolField field, int baseLength, Charset charset) {
        // If sign is separate, baseLength includes the sign byte, but we need just the digits
        int digitLength = baseLength;
        if (field.signed() && field.signSeparate()) {
            digitLength = baseLength - 1;
        }

        return serializeDisplay(value, digitLength, field.decimalDigits(), isNumericPicture(field.picture()),
                field.signed(), field.signSeparate(), "LEADING".equalsIgnoreCase(field.signPosition()), charset);
    }

    private boolean isNumericPicture(String picture) {
        return picture.startsWith("9") || picture.startsWith("S9");
    }

    /**
     * Serialize COMP/BINARY field.
     */
    private byte[] serializeComp(Object value, CobolField field) {
        return CobolFieldSerializer.serializeComp(value, field.integerDigits() + field.decimalDigits());
    }

    /**
     * Serialize COMP-1 (float) field.
     */
    private byte[] serializeComp1(Object value) {
        return CobolFieldSerializer.serializeComp1(value);
    }

    /**
     * Serialize COMP-2 (double) field.
     */
    private byte[] serializeComp2(Object value) {
        return CobolFieldSerializer.serializeComp2(value);
    }

    /**
     * Serialize COMP-3 (packed decimal) field.
     */
    private byte[] serializeComp3(Object value, CobolField field) {
        int totalDigits = field.integerDigits() + field.decimalDigits();
        return CobolFieldSerializer.serializeComp3(value, totalDigits, field.decimalDigits());
    }

    /**
     * Serialize ZONED-DECIMAL field.
     */
    private byte[] serializeZonedDecimal(Object value, CobolField field) {
        int totalDigits = field.integerDigits() + field.decimalDigits();
        return CobolFieldSerializer.serializeZonedDecimal(value, totalDigits, field.decimalDigits(), field.signed());
    }
}
