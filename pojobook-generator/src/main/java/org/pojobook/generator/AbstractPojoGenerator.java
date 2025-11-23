package org.pojobook.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.generator.common.ConditionNameMethodGenerator;
import org.pojobook.generator.common.FieldValidator;
import org.pojobook.generator.common.GetterSetterGenerator;
import org.pojobook.generator.common.ObjectMethodsGenerator;
import org.pojobook.generator.common.TypeResolver;
import org.pojobook.generator.context.GeneratorContext;
import org.pojobook.parser.CopybookDefinition;
import org.pojobook.parser.FieldDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Base class for POJO generators using composition of specialized helpers.
 * Uses GeneratorContext for centralized dependency management.
 */
public abstract class AbstractPojoGenerator {

    // Central context managing all dependencies
    protected final GeneratorContext context;
    
    // Convenience accessors (delegating to context)
    protected final FieldNameTracker fieldNameTracker;
    protected final TypeResolver typeResolver;
    protected final FieldValidator fieldValidator;
    protected final GetterSetterGenerator getterSetterGenerator;
    protected final ConditionNameMethodGenerator conditionNameMethodGenerator;
    protected final ObjectMethodsGenerator objectMethodsGenerator;

    /**
     * Constructor using default context.
     */
    protected AbstractPojoGenerator() {
        this(new GeneratorContext());
    }

    /**
     * Constructor with custom context (for dependency injection).
     */
    protected AbstractPojoGenerator(GeneratorContext context) {
        this.context = context;
        // Initialize convenience accessors
        this.fieldNameTracker = context.getFieldNameTracker();
        this.typeResolver = context.getTypeResolver();
        this.fieldValidator = context.getFieldValidator();
        this.getterSetterGenerator = context.getGetterSetterGenerator();
        this.conditionNameMethodGenerator = context.getConditionNameMethodGenerator();
        this.objectMethodsGenerator = context.getObjectMethodsGenerator();
    }

    public abstract String generate(CopybookDefinition definition);

    protected abstract TypeSpec buildNestedClass(String className, FieldNode node);

    /**
     * Generate and write class to file.
     */
    public void generateToFile(CopybookDefinition definition, Path outputPath) throws IOException {
        String code = generate(definition);
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, code);
    }

    /**
     * Generate nested classes for fields with OCCURS.
     */
    protected void generateNestedClasses(FieldNode node, Map<String, TypeSpec> nestedClasses) {
        FieldDefinition field = node.getField();

        if (shouldGenerateNestedClass(field, node)) {
            String className = NamingUtils.toPascalCase(field.getName());
            nestedClasses.computeIfAbsent(className, k -> buildNestedClass(className, node));
        }

        if (shouldFlattenGroup(field)) {
            node.getChildren().forEach(child -> generateNestedClasses(child, nestedClasses));
        }
    }

    /**
     * Generate nested classes for all children, including those in flattened groups.
     */
    protected void generateNestedClassesForChildren(FieldNode parentNode, Map<String, TypeSpec> nestedClasses) {
        parentNode.getChildren().forEach(child -> processChildNode(child, nestedClasses));
    }

    private void processChildNode(FieldNode child, Map<String, TypeSpec> nestedClasses) {
        FieldDefinition field = child.getField();

        if (shouldGenerateNestedClass(field, child)) {
            String className = NamingUtils.toPascalCase(field.getName());
            nestedClasses.computeIfAbsent(className, k -> buildNestedClass(className, child));
        } else if (shouldFlattenGroup(field)) {
            generateNestedClassesForChildren(child, nestedClasses);
        }
    }

    private boolean shouldGenerateNestedClass(FieldDefinition field, FieldNode node) {
        return field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty();
    }

    private boolean shouldFlattenGroup(FieldDefinition field) {
        return field.isGroup() && field.getOccurs() == 1;
    }

    /**
     * Get base Java type for a field (delegates to TypeResolver).
     */
    public TypeName getBaseJavaType(FieldDefinition field) {
        return typeResolver.getBaseJavaType(field);
    }

    /**
     * Get default value for a field (delegates to TypeResolver).
     */
    protected String getDefaultValue(FieldDefinition field) {
        TypeName baseType = getBaseJavaType(field);
        return typeResolver.getDefaultValue(field, baseType);
    }

    /**
     * Get Java type for a field (with array support).
     */
    public TypeName getJavaType(FieldDefinition field) {
        TypeName baseType = getBaseJavaType(field);
        return field.getOccurs() > 1 ? ArrayTypeName.of(baseType) : baseType;
    }

    /**
     * Add initialization statement in constructor.
     * Note: Most fields are now initialized at declaration.
     * Constructor initialization is only needed for nested class arrays.
     */
    protected void addConstructorInitialization(MethodSpec.Builder constructor, FieldNode node) {
        FieldDefinition field = node.getField();

        // Skip 88-level condition names
        if (field.getLevel() == 88) {
            return;
        }

        if (shouldGenerateNestedClass(field, node)) {
            // Nested class arrays need initialization in constructor
            initializeNestedClassArray(constructor, field);
        } else if (shouldFlattenGroup(field)) {
            node.getChildren().forEach(child -> addConstructorInitialization(constructor, child));
        }
    }

    private void initializeNestedClassArray(MethodSpec.Builder constructor, FieldDefinition field) {
        String fieldName = fieldNameTracker.getJavaFieldName(field);
        String className = NamingUtils.toPascalCase(field.getName());
        constructor.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
        constructor.addStatement("this.$L[i] = new $L()", fieldName, className);
        constructor.endControlFlow();
    }


    /**
     * Add getters and setters (delegates to GetterSetterGenerator).
     */
    protected void addGettersSetters(TypeSpec.Builder builder, FieldNode node) {
        FieldDefinition field = node.getField();

        // Skip 88-level fields
        if (field.getLevel() == 88) {
            return;
        }

        if (shouldGenerateNestedClass(field, node)) {
            String fieldName = fieldNameTracker.getJavaFieldName(field);
            String arrayType = NamingUtils.toPascalCase(field.getName());
            TypeName fieldType = ArrayTypeName.of(ClassName.bestGuess(arrayType));
            getterSetterGenerator.addGetterSetter(builder, field, fieldName, fieldType);
        } else if (shouldFlattenGroup(field)) {
            node.getChildren().forEach(child -> addGettersSetters(builder, child));
        } else {
            String fieldName = fieldNameTracker.getJavaFieldName(field);
            TypeName fieldType = getJavaType(field);
            getterSetterGenerator.addGetterSetter(builder, field, fieldName, fieldType);
        }

        // Add condition name methods for this field
        addConditionNameMethods(builder, field);
    }

    /**
     * Add condition name (88-level) checker methods (delegates to ConditionNameMethodGenerator).
     */
    protected void addConditionNameMethods(TypeSpec.Builder builder, FieldDefinition field) {
        String fieldName = fieldNameTracker.getJavaFieldName(field);
        conditionNameMethodGenerator.addConditionNameMethods(builder, field, fieldName);
    }

    /**
     * Add toString method (delegates to ObjectMethodsGenerator).
     */
    protected void addToString(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree) {
        objectMethodsGenerator.addToString(builder, className, fieldTree, fieldNameTracker::getJavaFieldName);
    }

    /**
     * Add equals method (delegates to ObjectMethodsGenerator).
     */
    protected void addEquals(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree) {
        objectMethodsGenerator.addEquals(builder, className, fieldTree, fieldNameTracker::getJavaFieldName);
    }

    /**
     * Add hashCode method (delegates to ObjectMethodsGenerator).
     */
    protected void addHashCode(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        objectMethodsGenerator.addHashCode(builder, fieldTree, fieldNameTracker::getJavaFieldName);
    }

    /**
     * Create simple field spec.
     */
    protected abstract FieldSpec createSimpleField(FieldDefinition field);

    /**
     * Create array field spec.
     */
    protected abstract FieldSpec createArrayField(FieldDefinition field);

    /**
     * Expand field node to field specs.
     */
    protected Stream<FieldSpec> expandFieldNode(FieldNode node) {
        FieldDefinition field = node.getField();

        // Skip 88-level condition names - they are handled as methods
        if (field.getLevel() == 88) {
            return Stream.empty();
        }

        if (isGroupWithOccurs(field, node)) {
            return Stream.of(createArrayField(field));
        } else if (isFlattenedGroup(field, node)) {
            return node.getChildren().stream()
                    .flatMap(this::expandFieldNode);
        } else {
            return Stream.of(createSimpleField(field));
        }
    }

    /**
     * Predicates for field types.
     */
    private boolean isGroupWithOccurs(FieldDefinition field, FieldNode node) {
        return field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty();
    }

    private boolean isFlattenedGroup(FieldDefinition field, FieldNode node) {
        return field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty();
    }


    /**
     * Context holder for builder operations.
     */
    protected static class BuilderContext {
        final TypeSpec.Builder builder;
        final String className;
        final List<FieldNode> fieldTree;

        BuilderContext(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree) {
            this.builder = builder;
            this.className = className;
            this.fieldTree = fieldTree;
        }
    }
}
