package org.pojobook.util;

import org.pojobook.CobolDataType;
import org.pojobook.annotation.CobolField;

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
}
