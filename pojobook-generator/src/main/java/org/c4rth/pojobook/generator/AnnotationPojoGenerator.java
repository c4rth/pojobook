package org.c4rth.pojobook.generator;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.c4rth.pojobook.CobolDataType;
import org.c4rth.pojobook.annotation.CobolField;
import org.c4rth.pojobook.annotation.CobolRecord;
import org.c4rth.pojobook.parser.CopybookDefinition;
import org.c4rth.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates POJO classes from COBOL copybook definitions using JavaPoet.
 */
public class AnnotationPojoGenerator extends AbstractPojoGenerator {

    private String packageName = "org.c4rth.generated";

    public AnnotationPojoGenerator() {
    }

    public AnnotationPojoGenerator withPackage(String packageName) {
        this.packageName = packageName;
        return this;
    }

    /**
     * Generate a POJO class from a copybook definition.
     */
    @Override
    public String generate(CopybookDefinition definition) {
        // Reset field name tracking to ensure uniqueness within this class
        resetFieldNameTracking();

        String recordName = definition.getRecordName() != null ? definition.getRecordName() : "CobolRecord";
        String className = (recordName.contains("-") || recordName.contains("_")) ?
                toPascalCase(recordName) : recordName;

        List<FieldDefinition> fields = definition.getFields();
        List<FieldNode> fieldTree = FieldNode.buildTree(fields);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC);

        classBuilder.addAnnotation(CobolRecord.class);

        // Generate nested classes first
        Map<String, TypeSpec> nestedClasses = new HashMap<>();
        for (FieldNode node : fieldTree) {
            generateNestedClasses(node, nestedClasses);
        }

        // Add fields
        for (FieldNode node : fieldTree) {
            addFieldFromNode(classBuilder, node);
        }

        // Add constructor
        addConstructor(classBuilder, fieldTree);

        // Add getters and setters
        for (FieldNode node : fieldTree) {
            addGettersSetters(classBuilder, node);
        }

        // Add toString
        addToString(classBuilder, fieldTree);

        // Add equals and hashCode
        addEquals(classBuilder, className, fieldTree);
        addHashCode(classBuilder, fieldTree);

        // Add nested classes
        for (TypeSpec nestedClass : nestedClasses.values()) {
            classBuilder.addType(nestedClass);
        }

        TypeSpec classSpec = classBuilder.build();

        JavaFile javaFile = JavaFile.builder(packageName, classSpec)
                .build();

        return javaFile.toString();
    }

    /**
     * Build a nested class for a group field with OCCURS.
     */
    @Override
    protected TypeSpec buildNestedClass(String className, FieldNode node) {
        // Save current tracking and start fresh for this nested class
        pushFieldNameTracking();

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        builder.addAnnotation(CobolRecord.class);

        // Generate nested classes for all descendants that need them
        // This includes children of flattened groups
        Map<String, TypeSpec> childNestedClasses = new HashMap<>();
        generateNestedClassesForChildren(node, childNestedClasses);

        // Add fields
        for (FieldNode child : node.getChildren()) {
            addFieldFromNode(builder, child);
        }

        // Add constructor
        addConstructorForNestedClass(builder, node.getChildren());

        // Add getters and setters
        for (FieldNode child : node.getChildren()) {
            addGettersSetters(builder, child);
        }

        // Add equals and hashCode
        addEquals(builder, className, node.getChildren());
        addHashCode(builder, node.getChildren());

        // Add child nested classes
        for (TypeSpec childNestedClass : childNestedClasses.values()) {
            builder.addType(childNestedClass);
        }

        TypeSpec result = builder.build();

        // Restore previous tracking state
        popFieldNameTracking();

        return result;
    }

    /**
     * Add a field from a FieldNode.
     */
    private void addFieldFromNode(TypeSpec.Builder builder, FieldNode node) {
        FieldDefinition field = node.getField();

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Group with OCCURS - use nested class array
            String fieldName = toUniqueFieldName(field);
            String className = toPascalCase(field.getName());
            TypeName fieldType = ArrayTypeName.of(ClassName.bestGuess(className));

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE);

            fieldBuilder.addAnnotation(createCobolFieldAnnotation(field));

            builder.addField(fieldBuilder.build());

        } else if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
            // Group without OCCURS - flatten
            for (FieldNode child : node.getChildren()) {
                addFieldFromNode(builder, child);
            }

        } else {
            // Simple field or array
            String fieldName = toUniqueFieldName(field);
            TypeName fieldType = getJavaType(field);

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE);

            fieldBuilder.addAnnotation(createCobolFieldAnnotation(field));

            builder.addField(fieldBuilder.build());
        }
    }

    /**
     * Create CobolField annotation.
     */
    private AnnotationSpec createCobolFieldAnnotation(FieldDefinition field) {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(CobolField.class)
                .addMember("level", "$L", field.getLevel())
                .addMember("name", "$S", field.getName());

        String picture = field.getPicture() != null ? field.getPicture() : "";
        builder.addMember("picture", "$S", picture);

        builder.addMember("type", "$T.$L", CobolDataType.class, field.getType());

        // Workaround for OCCURS with PIC on same line
        int integerDigits = field.getIntegerDigits();
        if (field.getOccurs() > 1 && (picture == null || picture.isEmpty()) && integerDigits == 0) {
            integerDigits = 1;
        }

        if (integerDigits > 0) {
            builder.addMember("integerDigits", "$L", integerDigits);
        }

        if (field.getDecimalDigits() > 0) {
            builder.addMember("decimalDigits", "$L", field.getDecimalDigits());
        }

        if (field.isSigned()) {
            builder.addMember("signed", "$L", true);
        }

        if (field.getSignPosition() != null && !field.getSignPosition().isEmpty()) {
            builder.addMember("signPosition", "$S", field.getSignPosition());
        }

        if (field.isSignSeparate()) {
            builder.addMember("signSeparate", "$L", true);
        }

        if (field.getOccurs() > 1) {
            builder.addMember("occurs", "$L", field.getOccurs());
        }

        return builder.build();
    }

    /**
     * Add constructor.
     */
    private void addConstructor(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        for (FieldNode node : fieldTree) {
            addConstructorInitialization(constructor, node);
        }

        builder.addMethod(constructor.build());
    }

    /**
     * Add constructor for nested class.
     */
    private void addConstructorForNestedClass(TypeSpec.Builder builder, List<FieldNode> children) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        for (FieldNode child : children) {
            addConstructorInitialization(constructor, child);
        }

        builder.addMethod(constructor.build());
    }

}

