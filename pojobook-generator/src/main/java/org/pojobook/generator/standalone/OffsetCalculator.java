package org.pojobook.generator.standalone;

import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.generator.FieldNameTracker;
import org.pojobook.generator.FieldNode;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calculates field sizes and generates offset constants for efficient serialization.
 * <p>
 * REDEFINES-aware: fields declared with a {@code REDEFINES} clause reuse the storage
 * offset of the field they redefine instead of appending additional space. The overall
 * size of a redefined region is the maximum extent reached by any of its alternate views.
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
        layout(builder, fieldTree, 0, new HashMap<>());
    }

    /**
     * Lay out a list of sibling fields, optionally emitting offset/size constants to a builder.
     * <p>
     * REDEFINES fields rewind the layout cursor back to the offset of the field they redefine,
     * so alternate views share the same storage region instead of being appended sequentially.
     * The returned value is the maximum offset reached by any field (or nested REDEFINES branch)
     * processed, which represents the true end of this group of siblings.
     *
     * @param builder      target builder to receive offset/size constants, or {@code null} to skip emission
     * @param nodes        sibling field nodes to lay out, in declaration order
     * @param startOffset  offset at which the first field begins
     * @param nameToOffset shared map of COBOL field name (normalized) to its start offset, used to
     *                     resolve REDEFINES targets
     * @return the maximum offset reached (i.e. the end of the laid-out region)
     */
    private int layout(TypeSpec.Builder builder, List<FieldNode> nodes, int startOffset, Map<String, Integer> nameToOffset) {
        int cursor = startOffset;
        int maxEnd = startOffset;

        for (FieldNode node : nodes) {
            FieldDefinition field = node.getField();

            // Skip 88-level condition names - they don't have offsets
            if (field.getLevel() == 88) {
                continue;
            }

            if (field.getRedefines() != null && !field.getRedefines().isEmpty()) {
                cursor = nameToOffset.getOrDefault(normalizeName(field.getRedefines()), cursor);
            }

            int fieldOffset = cursor;
            int size;

            if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
                // Flattened group - process children recursively, starting at this field's offset
                int childEnd = layout(builder, node.getChildren(), fieldOffset, nameToOffset);
                size = childEnd - fieldOffset;
            } else if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
                // Nested class array
                int elementSize = calculateNestedClassSize(node.getChildren());
                if (builder != null) {
                    String fieldName = fieldNameTracker.toUniqueFieldName(field);
                    addOffsetConstant(builder, fieldName, fieldOffset);
                    addSizeConstant(builder, fieldName, elementSize);
                }
                size = elementSize * field.getOccurs();
            } else {
                // Simple field or primitive array
                int fieldSize = calculateFieldSize(field);
                if (builder != null) {
                    String fieldName = fieldNameTracker.toUniqueFieldName(field);
                    addOffsetConstant(builder, fieldName, fieldOffset);
                    if (field.getOccurs() > 1) {
                        addSizeConstant(builder, fieldName, fieldSize);
                    }
                }
                size = fieldSize * Math.max(1, field.getOccurs());
            }

            if (!field.isFiller() && field.getName() != null) {
                nameToOffset.put(normalizeName(field.getName()), fieldOffset);
            }

            cursor = fieldOffset + size;
            if (cursor > maxEnd) {
                maxEnd = cursor;
            }
        }

        return maxEnd;
    }

    private String normalizeName(String name) {
        return name.toUpperCase(Locale.ROOT);
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
     * Calculate the total size in bytes of a nested class (or of a full field tree, when
     * called with the top-level list). REDEFINES-aware: alternate views share storage
     * instead of accumulating additional space.
     */
    public int calculateNestedClassSize(List<FieldNode> children) {
        return layout(null, children, 0, new HashMap<>());
    }
}

