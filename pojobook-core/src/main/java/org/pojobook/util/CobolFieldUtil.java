package org.pojobook.util;

import org.pojobook.CobolDataType;
import org.pojobook.annotation.CobolField;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CobolFieldUtil {

    private CobolFieldUtil() {
        // Prevent instantiation
    }

    /**
     * Return a {@link CobolField} that delegates all methods to the original
     * but returns {@code occurs() == 1}. Useful for processing individual
     * elements of an array field.
     */
    public static CobolField withSingleOccur(CobolField original) {
        return new DelegatingCobolField(original);
    }

    /**
     * Calculate the byte length of a field.
     */
    public static int calculateFieldLength(CobolField field) {
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
        if (field.type() == CobolDataType.DISPLAY && field.signed() && field.signSeparate()) {
            baseLength += 1;
        }

        return baseLength;
    }

    /**
     * Result of laying out a sequence of sibling fields, accounting for REDEFINES.
     *
     * @param offsets   the start offset of each field, parallel to the input arrays
     * @param totalSize the total storage size occupied by all fields, taking into account that
     *                  REDEFINES fields share storage with the field they redefine rather than
     *                  appending additional space
     */
    public record LayoutResult(int[] offsets, int totalSize) {
    }

    /**
     * Compute the start offset of each field in a flat, ordered sequence of sibling fields,
     * honoring {@link CobolField#redefines()}. A field with a non-empty {@code redefines()}
     * value reuses the start offset of the field it redefines (rewinding the layout cursor)
     * instead of being appended after the preceding field. The total size returned is the
     * maximum extent reached by any field, so overlapping REDEFINES regions are only counted
     * once (based on the largest alternate view).
     *
     * @param names  COBOL name of each field (as declared via {@link CobolField#name()}), parallel to sizes
     * @param redefinesTargets the {@link CobolField#redefines()} value of each field (empty/null if none), parallel to sizes
     * @param sizes  total byte size of each field (including any {@code occurs} multiplier), parallel to names
     */
    public static LayoutResult computeLayout(String[] names, String[] redefinesTargets, int[] sizes) {
        int n = sizes.length;
        int[] offsets = new int[n];
        Map<String, Integer> nameToOffset = new HashMap<>();
        int cursor = 0;
        int maxEnd = 0;

        for (int i = 0; i < n; i++) {
            String redefines = redefinesTargets[i];
            if (redefines != null && !redefines.isEmpty()) {
                cursor = nameToOffset.getOrDefault(normalize(redefines), cursor);
            }

            offsets[i] = cursor;

            String name = names[i];
            if (name != null && !name.isEmpty()) {
                nameToOffset.put(normalize(name), cursor);
            }

            cursor += sizes[i];
            if (cursor > maxEnd) {
                maxEnd = cursor;
            }
        }

        return new LayoutResult(offsets, maxEnd);
    }

    private static String normalize(String name) {
        return name.toUpperCase(Locale.ROOT);
    }
}

