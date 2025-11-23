package org.pojobook.generator;

import org.pojobook.generator.context.GeneratorContext;

/**
 * Builder for constructing generators with custom configuration.
 * Implements Fluent Interface pattern for clean API.
 * Package-private constructors ensure this is the only way to create generators.
 */
public class GeneratorBuilder {

    private String packageName = "org.pojobook.generated";
    private GeneratorContext context;
    private GeneratorType type = GeneratorType.EMBEDDED;

    public enum GeneratorType {
        EMBEDDED,
        ANNOTATION
    }

    public GeneratorBuilder() {
        this.context = new GeneratorContext();
    }

    /**
     * Set the package name for generated classes.
     */
    public GeneratorBuilder withPackageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    /**
     * Set generator type (embedded or annotation-based).
     */
    public GeneratorBuilder withType(GeneratorType type) {
        this.type = type;
        return this;
    }

    /**
     * Use a custom generator context.
     */
    public GeneratorBuilder withContext(GeneratorContext context) {
        this.context = context;
        return this;
    }

    /**
     * Build the configured generator.
     */
    public Object build() {
        return switch (type) {
            case EMBEDDED -> buildEmbeddedGenerator();
            case ANNOTATION -> buildAnnotationGenerator();
        };
    }

    /**
     * Build an embedded serialization generator.
     */
    public EmbeddedSerializationPojoGenerator buildEmbeddedGenerator() {
        EmbeddedSerializationPojoGenerator generator = new EmbeddedSerializationPojoGenerator(context);
        return generator.withPackage(packageName);
    }

    /**
     * Build an annotation-based generator.
     */
    public AnnotationPojoGenerator buildAnnotationGenerator() {
        AnnotationPojoGenerator generator = new AnnotationPojoGenerator(context);
        return generator.withPackage(packageName);
    }

    /**
     * Static factory method for fluent API.
     */
    public static GeneratorBuilder create() {
        return new GeneratorBuilder();
    }

    /**
     * Quick factory for embedded generator.
     */
    public static EmbeddedSerializationPojoGenerator embeddedGenerator() {
        return new GeneratorBuilder()
                .withType(GeneratorType.EMBEDDED)
                .buildEmbeddedGenerator();
    }

    /**
     * Quick factory for annotation generator.
     */
    public static AnnotationPojoGenerator annotationGenerator() {
        return new GeneratorBuilder()
                .withType(GeneratorType.ANNOTATION)
                .buildAnnotationGenerator();
    }

    /**
     * Quick factory with package name.
     */
    public static EmbeddedSerializationPojoGenerator embeddedGenerator(String packageName) {
        return new GeneratorBuilder()
                .withType(GeneratorType.EMBEDDED)
                .withPackageName(packageName)
                .buildEmbeddedGenerator();
    }

    /**
     * Quick factory with package name.
     */
    public static AnnotationPojoGenerator annotationGenerator(String packageName) {
        return new GeneratorBuilder()
                .withType(GeneratorType.ANNOTATION)
                .withPackageName(packageName)
                .buildAnnotationGenerator();
    }
}

