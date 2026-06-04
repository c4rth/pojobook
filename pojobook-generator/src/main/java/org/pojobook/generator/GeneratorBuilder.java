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
    private GeneratorType type = GeneratorType.STANDALONE;
    private boolean primitiveNumericFields;
    private boolean disableValidation;

    public enum GeneratorType {
        STANDALONE,
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
     * Disable input size validation on setters.
     */
    public GeneratorBuilder withDisableValidation(boolean disableValidation) {
        this.disableValidation = disableValidation;
        return this;
    }

    /**
     * Set generator type (standalone or annotation-based).
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
     * Enable primitive numeric field generation for standalone generator output.
     */
    public GeneratorBuilder withPrimitiveNumericFields(boolean primitiveNumericFields) {
        this.primitiveNumericFields = primitiveNumericFields;
        return this;
    }

    /**
     * Build the configured generator.
     */
    public Object build() {
        return switch (type) {
            case STANDALONE -> buildStandaloneGenerator();
            case ANNOTATION -> buildAnnotationGenerator();
        };
    }

    /**
     * Build an standalone serialization generator.
     */
    public StandaloneSerializationPojoGenerator buildStandaloneGenerator() {
        StandaloneSerializationPojoGenerator generator = new StandaloneSerializationPojoGenerator(context);
        return generator.withPackage(packageName)
                .withPrimitiveNumericFields(primitiveNumericFields)
                .withDisableValidation(disableValidation);
    }

    /**
     * Build an annotation-based generator.
     */
    public AnnotationPojoGenerator buildAnnotationGenerator() {
        AnnotationPojoGenerator generator = new AnnotationPojoGenerator(context);
        return generator.withPackage(packageName)
                .withDisableValidation(disableValidation);
    }

    /**
     * Static factory method for fluent API.
     */
    public static GeneratorBuilder create() {
        return new GeneratorBuilder();
    }

    /**
     * Quick factory for standalone generator.
     */
    public static StandaloneSerializationPojoGenerator standaloneGenerator() {
        return new GeneratorBuilder()
                .withType(GeneratorType.STANDALONE)
                .buildStandaloneGenerator();
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
    public static StandaloneSerializationPojoGenerator standaloneGenerator(String packageName) {
        return new GeneratorBuilder()
                .withType(GeneratorType.STANDALONE)
                .withPackageName(packageName)
                .buildStandaloneGenerator();
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
