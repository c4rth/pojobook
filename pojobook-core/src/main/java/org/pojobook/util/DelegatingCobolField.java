package org.pojobook.util;

import org.pojobook.CobolDataType;
import org.pojobook.annotation.CobolField;

import java.lang.annotation.Annotation;

/**
 * A {@link CobolField} implementation that delegates all methods to an original
 * instance but overrides {@link #occurs()} to return 1.
 * <p>
 * Used to process individual array elements without the per-element cost of
 * creating an anonymous class. Instances are typically cached per original
 * {@code CobolField} annotation.
 */
public record DelegatingCobolField(CobolField original) implements CobolField {

    @Override
    public Class<? extends Annotation> annotationType() {
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
}

