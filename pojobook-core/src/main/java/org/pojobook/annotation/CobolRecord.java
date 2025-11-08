package org.pojobook.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a COBOL record.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CobolRecord {
    /**
     * The record name in the COBOL copybook.
     * If not specified, uses the Java class name.
     */
    String name() default "";

}

