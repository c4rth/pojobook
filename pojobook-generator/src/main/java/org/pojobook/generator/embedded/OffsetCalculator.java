package org.pojobook.generator.embedded;

import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.generator.FieldNameTracker;
import org.pojobook.generator.FieldNode;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates field sizes and generates offset constants for efficient serialization.
 */
public class OffsetCalculator {

    private final Map<FieldDefinition, Integer> fieldSizeCache = new HashMap<>();
    private final Map<String, String> offsetConstantNameCache = new HashMap<>();
    private final Map<String, String> sizeConstantNameCache = new HashMap<>();
    private final FieldNameTracker fieldNameTracker;

    public OffsetCalculator(FieldNameTracker fieldNameTracker) {
        this.fieldNameTracker = fieldNameTracker;
    }

    /**
     * Clear the size cache.
     */
    public void clearCache() {
        fieldSizeCache.clear();
        offsetConstantNameCache.clear();
        sizeConstantNameCache.clear();
    }

    public String offsetConstantName(String fieldName) {
        return offsetConstantNameCache.computeIfAbsent(fieldName, name -> "OFFSET_" + toConstantToken(name));
    }

    public String sizeConstantName(String fieldName) {
        return sizeConstantNameCache.computeIfAbsent(fieldName, name -> "SIZE_" + toConstantToken(name));
    }

    /**
     * Add static offset fields for efficient serialization/deserialization.
     */
    public void addOffsetFields(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        addOffsetFieldsWithStartOffset(builder, fieldTree, 0);
    }

    /**
     * Add offset fields starting from a given offset (used for both top-level and nested fields).
     */
    private int addOffsetFieldsWithStartOffset(TypeSpec.Builder builder, List<FieldNode> fieldTree, int startOffset) {
        int currentOffset = startOffset;

        for (FieldNode node : fieldTree) {
            FieldDefinition field = node.getField();

            // Skip 88-level condition names - they don't have offsets
            if (field.getLevel() == 88) {
                continue;
            }

            String fieldName = fieldNameTracker.toUniqueFieldName(field);

            if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
                // Flattened group - process children recursively
                currentOffset = addOffsetFieldsWithStartOffset(builder, node.getChildren(), currentOffset);
            } else if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
                // Nested class array
                int elementSize = calculateNestedClassSize(node.getChildren());
                addOffsetConstant(builder, fieldName, currentOffset);
                addSizeConstant(builder, fieldName, elementSize);
                currentOffset += elementSize * field.getOccurs();
            } else {
                // Simple field or primitive array
                int fieldSize = calculateFieldSize(field);
                addOffsetConstant(builder, fieldName, currentOffset);

                if (field.getOccurs() > 1) {
                    addSizeConstant(builder, fieldName, fieldSize);
                }

                currentOffset += fieldSize * Math.max(1, field.getOccurs());
            }
        }

        return currentOffset;
    }

    /**
     * Add an offset constant field.
     */
    private void addOffsetConstant(TypeSpec.Builder builder, String fieldName, int offset) {
        String constantName = offsetConstantName(fieldName);
        builder.addField(FieldSpec.builder(int.class, constantName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", offset)
                .build());
    }

    /**
     * Add a size constant field.
     */
    private void addSizeConstant(TypeSpec.Builder builder, String fieldName, int size) {
        String constantName = sizeConstantName(fieldName);
        builder.addField(FieldSpec.builder(int.class, constantName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", size)
                .build());
    }

    private String toConstantToken(String fieldName) {
        return fieldName.replace("-", "_").toUpperCase();
    }

    /**
     * Calculate the size in bytes of a field.
     */
    public int calculateFieldSize(FieldDefinition field) {
        return fieldSizeCache.computeIfAbsent(field, f ->
                switch (f.getType()) {
                    case DISPLAY -> calculateDisplayLength(f);
                    case COMP, COMP_5 -> calculateCompSize(f.getIntegerDigits() + f.getDecimalDigits());
                    case COMP_1 -> 4;
                    case COMP_2 -> 8;
                    case COMP_3, PACKED_DECIMAL ->
                            calculatePackedDecimalSize(f.getIntegerDigits() + f.getDecimalDigits());
                    case ZONED_DECIMAL -> f.getIntegerDigits() + f.getDecimalDigits();
                });
    }

    /**
     * Calculate the display field length.
     */
    private int calculateDisplayLength(FieldDefinition field) {
        int length = field.getIntegerDigits() + field.getDecimalDigits();
        if (length == 0 && field.getPicture() != null) {
            // Fallback: count display characters directly without regex allocations.
            String picture = field.getPicture();
            for (int i = 0; i < picture.length(); i++) {
                char c = picture.charAt(i);
                if (c == 'X' || c == '9') {
                    length++;
                }
            }
        }
        return length;
    }

    /**
     * Calculate the size in bytes needed for a COMP/COMP-5 field.
     */
    private int calculateCompSize(int totalDigits) {
        if (totalDigits <= 4) return 2;
        if (totalDigits <= 9) return 4;
        return 8;
    }

    /**
     * Calculate the size in bytes needed for a COMP-3/PACKED-DECIMAL field.
     */
    private int calculatePackedDecimalSize(int totalDigits) {
        return (totalDigits / 2) + 1;
    }

    /**
     * Calculate the total size in bytes of a nested class.
     */
    public int calculateNestedClassSize(List<FieldNode> children) {
        int totalSize = 0;
        for (FieldNode child : children) {
            FieldDefinition field = child.getField();

            if (field.isGroup() && field.getOccurs() > 1 && !child.getChildren().isEmpty()) {
                int elementSize = calculateNestedClassSize(child.getChildren());
                totalSize += elementSize * field.getOccurs();
            } else if (field.isGroup() && field.getOccurs() == 1 && !child.getChildren().isEmpty()) {
                totalSize += calculateNestedClassSize(child.getChildren());
            } else {
                int fieldSize = calculateFieldSize(field);
                totalSize += fieldSize * field.getOccurs();
            }
        }
        return totalSize;
    }
}

