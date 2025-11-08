package org.pojobook.annotation;

import org.pojobook.CobolDataType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a field as a COBOL field with specific attributes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CobolField {
    /**
     * The level number in the COBOL copybook (e.g., 01, 05, 10).
     */
    int level();

    /**
     * The field name in the COBOL copybook.
     * If not specified, uses the Java field name.
     */
    String name() default "";

    /**
     * The picture clause (e.g., "9(5)", "X(20)", "S9(7)V99").
     */
    String picture() default "";

    /**
     * The data type (DISPLAY, COMP-3, etc.).
     */
    CobolDataType type() default CobolDataType.DISPLAY;

    /**
     * The total length in bytes.
     */
    int length() default -1;

    /**
     * Number of integer digits.
     */
    int integerDigits() default 0;

    /**
     * Number of decimal digits.
     */
    int decimalDigits() default 0;

    /**
     * Whether the field is signed.
     */
    boolean signed() default false;

    /**
     * Sign position: "LEADING" or "TRAILING".
     * Only applicable for signed DISPLAY fields.
     */
    String signPosition() default "";

    /**
     * Whether the sign is in a separate character.
     * If true, adds 1 byte to field length for DISPLAY fields.
     */
    boolean signSeparate() default false;

    /**
     * For arrays, the number of occurrences.
     */
    int occurs() default 1;

    /**
     * Minimum occurrences for OCCURS DEPENDING ON.
     */
    int minOccurs() default -1;

    /**
     * Maximum occurrences for OCCURS DEPENDING ON.
     */
    int maxOccurs() default -1;

    /**
     * For arrays with OCCURS DEPENDING ON.
     */
    String dependingOn() default "";

    /**
     * Field position/order in the copybook.
     */
    int position() default -1;

    /**
     * Name of the field this REDEFINES.
     */
    String redefines() default "";

    /**
     * Whether this field is a FILLER.
     */
    boolean filler() default false;

    /**
     * Initial VALUE clause content.
     */
    String value() default "";

    /**
     * Whether the field has JUSTIFIED RIGHT.
     */
    boolean justifiedRight() default false;

    /**
     * Whether the field has BLANK WHEN ZERO.
     */
    boolean blankWhenZero() default false;

    /**
     * SYNC/SYNCHRONIZED alignment.
     */
    String sync() default "";

    /**
     * Index names for INDEXED BY clause.
     */
    String[] indexedBy() default {};

    /**
     * KEY IS clause for sorting.
     */
    String[] keys() default {};

    /**
     * Whether this is an ASCENDING KEY.
     */
    boolean ascendingKey() default false;

    /**
     * Whether this is a DESCENDING KEY.
     */
    boolean descendingKey() default false;
}

