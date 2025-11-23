package org.pojobook.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.annotation.CobolRecord;
import org.pojobook.generator.annotation.CobolAnnotationGenerator;
import org.pojobook.generator.context.GeneratorContext;
import org.pojobook.parser.CopybookDefinition;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Generates POJO classes from COBOL copybook definitions using annotations.
 * Uses GeneratorContext for centralized dependency management.
 */
public class AnnotationPojoGenerator extends AbstractPojoGenerator {

    private String packageName = "org.pojobook.generated";
    
    // Annotation-specific helper
    private final CobolAnnotationGenerator annotationGenerator;

    /**
     * Constructor using default context.
     * Package-private - use GeneratorBuilder to create instances.
     */
    AnnotationPojoGenerator() {
        this(new GeneratorContext());
    }

    /**
     * Constructor with custom context (for dependency injection).
     * Package-private - use GeneratorBuilder to create instances.
     */
    AnnotationPojoGenerator(GeneratorContext context) {
        super(context);
        this.annotationGenerator = new CobolAnnotationGenerator();
    }

    public AnnotationPojoGenerator withPackage(String packageName) {
        this.packageName = packageName;
        return this;
    }

    @Override
    public String generate(CopybookDefinition definition) {
        fieldNameTracker.reset();

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
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CobolRecord.class);

        // Generate nested classes first
        Map<String, TypeSpec> nestedClasses = generateNestedClasses(fieldTree);

        // Add all components
        applyBuilders(classBuilder,
                className,
                fieldTree,
                this::addFieldsToBuilder,
                this::addConstructorToBuilder,
                this::addGettersSettersToBuilder,
                this::addToStringToBuilder,
                context -> {
                    addEquals(context.builder, className, context.fieldTree);
                    addHashCode(context.builder, context.fieldTree);
                }
        );

        // Add nested classes
        nestedClasses.values().forEach(classBuilder::addType);

        return classBuilder.build();
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

    @Override
    protected TypeSpec buildNestedClass(String className, FieldNode node) {
        fieldNameTracker.push();

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(CobolRecord.class);

        List<FieldNode> children = node.getChildren();
        Map<String, TypeSpec> childNestedClasses = generateNestedClasses(children);

        // Add components
        applyBuilders(builder,
                className,
                children,
                this::addFieldsToBuilder,
                this::addConstructorToBuilder,
                this::addGettersSettersToBuilder,
                context -> {
                    addEquals(context.builder, className, context.fieldTree);
                    addHashCode(context.builder, context.fieldTree);
                }
        );

        childNestedClasses.values().forEach(builder::addType);

        fieldNameTracker.pop();
        return builder.build();
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
     * Create simple field spec with COBOL annotation.
     * Fields are initialized at declaration.
     */
    @Override
    protected FieldSpec createSimpleField(FieldDefinition field) {
        String fieldName = fieldNameTracker.toUniqueFieldName(field);
        String defaultValue = getDefaultValue(field);

        return FieldSpec.builder(getJavaType(field), fieldName, Modifier.PRIVATE)
                .addAnnotation(annotationGenerator.createCobolFieldAnnotation(field))
                .initializer(defaultValue)
                .build();
    }

    /**
     * Create array field spec with COBOL annotation.
     * Array fields are initialized at declaration.
     */
    @Override
    protected FieldSpec createArrayField(FieldDefinition field) {
        String fieldName = fieldNameTracker.toUniqueFieldName(field);
        String className = NamingUtils.toPascalCase(field.getName());
        TypeName fieldType = ArrayTypeName.of(ClassName.bestGuess(className));

        return FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE)
                .addAnnotation(annotationGenerator.createCobolFieldAnnotation(field))
                .initializer("new $L[$L]", className, field.getOccurs())
                .build();
    }


    /**
     * Add constructor to builder.
     */
    private void addConstructorToBuilder(BuilderContext context) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        context.fieldTree.forEach(node ->
                addConstructorInitialization(constructor, node));

        context.builder.addMethod(constructor.build());
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
        return nodes.stream()
                .reduce(
                        new HashMap<>(),
                        (map, node) -> {
                            generateNestedClasses(node, map);
                            return map;
                        },
                        (map1, map2) -> {
                            map1.putAll(map2);
                            return map1;
                        }
                );
    }

}
