package org.pojobook.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.generator.context.GeneratorContext;
import org.pojobook.generator.embedded.ConstructorGenerator;
import org.pojobook.generator.embedded.DeserializationMethodsGenerator;
import org.pojobook.generator.embedded.OffsetCalculator;
import org.pojobook.generator.embedded.SerializationMethodsGenerator;
import org.pojobook.parser.CopybookDefinition;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Generates POJO classes with embedded serialization and deserialization methods.
 * Uses GeneratorContext for centralized dependency management.
 */
public class EmbeddedSerializationPojoGenerator extends AbstractPojoGenerator {

    private String packageName = "org.pojobook.generated";
    
    // Embedded-specific generators (from context)
    private final OffsetCalculator offsetCalculator;
    private final ConstructorGenerator constructorGenerator;
    private final SerializationMethodsGenerator serializationGenerator;
    private final DeserializationMethodsGenerator deserializationGenerator;

    /**
     * Constructor using default context.
     * Package-private - use GeneratorBuilder to create instances.
     */
    EmbeddedSerializationPojoGenerator() {
        this(new GeneratorContext());
    }

    /**
     * Constructor with custom context (for dependency injection).
     * Package-private - use GeneratorBuilder to create instances.
     */
    EmbeddedSerializationPojoGenerator(GeneratorContext context) {
        super(context);
        // Get embedded-specific helpers from context
        this.offsetCalculator = context.getOffsetCalculator();
        this.constructorGenerator = context.getConstructorGenerator();
        this.serializationGenerator = context.getSerializationGenerator();
        this.deserializationGenerator = context.getDeserializationGenerator(this);
    }

    public EmbeddedSerializationPojoGenerator withPackage(String packageName) {
        this.packageName = packageName;
        return this;
    }

    /**
     * Enable primitive numeric field generation for non-nullable numerics.
     * Default is false to preserve existing generated API compatibility.
     */
    public EmbeddedSerializationPojoGenerator withPrimitiveNumericFields(boolean enabled) {
        this.typeResolver.withPrimitiveNumericFields(enabled);
        return this;
    }

    /**
     * Generate a POJO class from a copybook definition.
     */
    @Override
    public String generate(CopybookDefinition definition) {
        // Reset state using context
        context.reset();

        String className = resolveClassName(definition.getRecordName());
        List<FieldNode> fieldTree = FieldNode.buildTree(definition.getFields());

        TypeSpec classSpec = buildClassSpec(className, fieldTree);

        return JavaFile.builder(packageName, classSpec)
                .build()
                .toString();
    }

    /**
     * Build complete class specification.
     */
    private TypeSpec buildClassSpec(String className, List<FieldNode> fieldTree) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC);

        // Generate nested classes first
        Map<String, TypeSpec> nestedClasses = generateNestedClasses(fieldTree);

        // Apply all builders using composed generators
        applyBuilders(classBuilder,
                className,
                fieldTree,
                this::addStaticVariables,
                this::addFieldsToBuilder,
                context -> context.builder.addMethod(constructorGenerator.generateConstructor(context.fieldTree)),
                this::addGettersSettersToBuilder,
                this::addToStringToBuilder,
                context -> {
                    addEquals(context.builder, className, context.fieldTree);
                    addHashCode(context.builder, context.fieldTree);
                    serializationGenerator.addSerializeMethods(context.builder, context.fieldTree);
                    deserializationGenerator.addDeserializeMethods(context.builder, className, context.fieldTree);
                }
        );

        // Add nested classes
        nestedClasses.values().forEach(classBuilder::addType);

        return classBuilder.build();
    }

    /**
     * Build a nested class for a group field with OCCURS.
     */
    @Override
    protected TypeSpec buildNestedClass(String className, FieldNode node) {
        fieldNameTracker.push();

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        List<FieldNode> children = node.getChildren();
        Map<String, TypeSpec> childNestedClasses = generateNestedClassesForChildren(node);

        // Apply builders using composed generators
        applyBuilders(builder,
                className,
                children,
                this::addStaticVariablesForNestedClass,
                this::addFieldsToBuilder,
                context -> context.builder.addMethod(constructorGenerator.generateConstructor(context.fieldTree)),
                this::addGettersSettersToBuilder,
                context -> {
                    addEquals(context.builder, className, context.fieldTree);
                    addHashCode(context.builder, context.fieldTree);
                    serializationGenerator.addSerializeMethods(context.builder, context.fieldTree);
                    deserializationGenerator.addDeserializeMethods(context.builder, className, context.fieldTree);
                }
        );

        childNestedClasses.values().forEach(builder::addType);

        fieldNameTracker.pop();
        return builder.build();
    }

    /**
     * Apply multiple builders to class in sequence.
     */
    @SafeVarargs
    private void applyBuilders(TypeSpec.Builder classBuilder,
                               String className,
                               List<FieldNode> fieldTree,
                               Consumer<BuilderContext>... builders) {
        BuilderContext context = new BuilderContext(classBuilder, className, fieldTree);
        Stream.of(builders).forEach(builder -> builder.accept(context));
    }

    /**
     * Add static variables to the class (charset + offsets).
     */
    private void addStaticVariables(BuilderContext context) {
        // Add charset constant
        context.builder
                .addField(FieldSpec.builder(Charset.class, "CHARSET_CP1047", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("Charset.forName(\"CP1047\")")
                        .build());

        // Delegate to offsetCalculator for offset/size constants
        offsetCalculator.addOffsetFields(context.builder, context.fieldTree);
    }

    /**
     * Add static variables to nested classes (offset/size constants only, no CHARSET).
     */
    private void addStaticVariablesForNestedClass(BuilderContext context) {
        offsetCalculator.addOffsetFields(context.builder, context.fieldTree);
    }

    /**
     * Add fields to builder.
     */
    private void addFieldsToBuilder(BuilderContext context) {
        context.fieldTree.stream()
                .flatMap(this::expandFieldNode)
                .forEach(context.builder::addField);
    }

    /**
     * Add getters and setters to builder.
     */
    private void addGettersSettersToBuilder(BuilderContext context) {
        context.fieldTree.forEach(node ->
                addGettersSetters(context.builder, node));
    }

    /**
     * Add toString to builder.
     */
    private void addToStringToBuilder(BuilderContext context) {
        addToString(context.builder, context.className, context.fieldTree);
    }

    /**
     * Create simple field spec with Javadoc.
     * Fields are initialized at declaration.
     */
    @Override
    protected FieldSpec createSimpleField(FieldDefinition field) {
        String fieldName = fieldNameTracker.toUniqueFieldName(field);
        String defaultValue = getDefaultValue(field);

        return FieldSpec.builder(getJavaType(field), fieldName, Modifier.PRIVATE)
                .addJavadoc(buildFieldComment(field))
                .initializer(defaultValue)
                .build();
    }

    /**
     * Create array field spec.
     * Array fields are initialized at declaration.
     */
    @Override
    protected FieldSpec createArrayField(FieldDefinition field) {
        String fieldName = fieldNameTracker.toUniqueFieldName(field);
        String className = NamingUtils.toPascalCase(field.getName());
        TypeName fieldType = ArrayTypeName.of(ClassName.bestGuess(className));

        return FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE)
                .addJavadoc(buildFieldComment(field))
                .initializer("new $L[$L]", className, field.getOccurs())
                .build();
    }

    /**
     * Resolve class name from record name.
     */
    private String resolveClassName(String recordName) {
        return Optional.ofNullable(recordName)
                .map(name -> name.contains("-") || name.contains("_")
                        ? NamingUtils.toPascalCase(name)
                        : name)
                .orElse("CobolRecord");
    }

    /**
     * Generate nested classes.
     */
    private Map<String, TypeSpec> generateNestedClasses(List<FieldNode> nodes) {
        Map<String, TypeSpec> nestedClasses = new HashMap<>();
        nodes.forEach(node -> generateNestedClasses(node, nestedClasses));
        return nestedClasses;
    }

    /**
     * Generate nested classes for children.
     */
    private Map<String, TypeSpec> generateNestedClassesForChildren(FieldNode parentNode) {
        Map<String, TypeSpec> nestedClasses = new HashMap<>();
        generateNestedClassesForChildren(parentNode, nestedClasses);
        return nestedClasses;
    }

    /**
     * Build a comment describing the COBOL field definition.
     */
    private String buildFieldComment(FieldDefinition field) {
        StringBuilder comment = new StringBuilder(127);

        comment.append("COBOL: ").append(field.getName());
        comment.append(" - Level: ").append(String.format("%02d", field.getLevel()));

        Optional.ofNullable(field.getPicture())
                .filter(pic -> !pic.isEmpty())
                .ifPresent(pic -> comment.append(" - PIC ").append(pic));

        comment.append(" - Type: ").append(field.getType());

        if (field.isSigned()) {
            comment.append(" - SIGNED");
            Optional.ofNullable(field.getSignPosition())
                    .filter(pos -> !pos.isEmpty())
                    .ifPresent(pos -> comment.append(" ").append(pos));
            if (field.isSignSeparate()) {
                comment.append(" SEPARATE");
            }
        }

        if (field.getOccurs() > 1) {
            comment.append(" - OCCURS ").append(field.getOccurs());
            Optional.ofNullable(field.getDependingOn())
                    .filter(dep -> !dep.isEmpty())
                    .ifPresent(dep -> comment.append(" DEPENDING ON ").append(dep));
        }

        Optional.ofNullable(field.getRedefines())
                .filter(ref -> !ref.isEmpty())
                .ifPresent(ref -> comment.append(" - REDEFINES ").append(ref));

        Optional.ofNullable(field.getValue())
                .filter(val -> !val.isEmpty())
                .ifPresent(val -> comment.append(" - VALUE ").append(val));

        comment.append("\n");
        return comment.toString();
    }
}

