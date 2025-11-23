package org.pojobook.generator.common;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.generator.FieldNode;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Generates equals, hashCode, and toString methods for POJOs.
 */
public class ObjectMethodsGenerator {

    private final TypeResolver typeResolver;

    public ObjectMethodsGenerator(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    /**
     * Add toString method.
     */
    public void addToString(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree,
                           java.util.function.Function<FieldDefinition, String> fieldNameResolver) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class);

        CodeBlock.Builder code = CodeBlock.builder();
        code.add("return $S + ", className + "{");

        List<CodeBlock> parts = new ArrayList<>();
        fieldTree.forEach(field -> collectToStringParts(field, parts, fieldNameResolver));

        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) code.add("$S + ", ", ");
            code.add(parts.get(i));
        }

        code.add("$S;", "}");
        method.addCode(code.build());
        builder.addMethod(method.build());
    }

    private void collectToStringParts(FieldNode node, List<CodeBlock> parts,
                                      java.util.function.Function<FieldDefinition, String> fieldNameResolver) {
        FieldDefinition field = node.getField();

        // Skip 88-level condition names
        if (field.getLevel() == 88) {
            return;
        }

        if (shouldFlattenGroup(field)) {
            node.getChildren().forEach(child -> collectToStringParts(child, parts, fieldNameResolver));
        } else {
            String fieldName = fieldNameResolver.apply(field);
            TypeName fieldType = getFieldType(field);
            
            if (fieldType instanceof ArrayTypeName) {
                parts.add(CodeBlock.of("$S + $T.toString($L) +", fieldName + "=", Arrays.class, fieldName));
            } else {
                parts.add(CodeBlock.of("$S + $L +", fieldName + "=", fieldName));
            }
        }
    }

    /**
     * Add equals method.
     */
    public void addEquals(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree,
                         java.util.function.Function<FieldDefinition, String> fieldNameResolver) {
        MethodSpec.Builder equals = MethodSpec.methodBuilder("equals")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(Object.class, "o");

        equals.addStatement("if (this == o) return true");
        equals.addStatement("if (o == null || getClass() != o.getClass()) return false");
        equals.addStatement("$L that = ($L) o", className, className);

        List<String> fieldNames = new ArrayList<>();
        fieldTree.forEach(node -> collectFieldNames(node, fieldNames, fieldNameResolver));

        if (fieldNames.isEmpty()) {
            equals.addStatement("return true");
        } else {
            List<CodeBlock> comparisons = fieldNames.stream()
                    .map(name -> createComparison(name, fieldTree, fieldNameResolver))
                    .toList();

            CodeBlock joined = comparisons.stream()
                    .reduce((a, b) -> CodeBlock.of("$L &&\n $L", a, b))
                    .orElse(CodeBlock.of("true"));

            equals.addStatement("return $L", joined);
        }

        builder.addMethod(equals.build());
    }

    private CodeBlock createComparison(String fieldName, List<FieldNode> fieldTree,
                                       java.util.function.Function<FieldDefinition, String> fieldNameResolver) {
        FieldDefinition fd = findFieldInTree(fieldTree, fieldName, fieldNameResolver);
        if (fd != null && getFieldType(fd) instanceof ArrayTypeName) {
            return CodeBlock.of("$T.equals($L, that.$L)", Arrays.class, fieldName, fieldName);
        }
        return CodeBlock.of("$T.equals($L, that.$L)", Objects.class, fieldName, fieldName);
    }

    /**
     * Add hashCode method.
     */
    public void addHashCode(TypeSpec.Builder builder, List<FieldNode> fieldTree,
                           java.util.function.Function<FieldDefinition, String> fieldNameResolver) {
        MethodSpec.Builder hashCode = MethodSpec.methodBuilder("hashCode")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class);

        List<String> allFieldNames = new ArrayList<>();
        fieldTree.forEach(node -> collectFieldNames(node, allFieldNames, fieldNameResolver));

        if (allFieldNames.isEmpty()) {
            hashCode.addStatement("return 0");
        } else {
            List<String> arrayFields = allFieldNames.stream()
                    .filter(name -> isArrayField(name, fieldTree, fieldNameResolver))
                    .toList();

            List<String> normalFields = allFieldNames.stream()
                    .filter(name -> !isArrayField(name, fieldTree, fieldNameResolver))
                    .toList();

            if (normalFields.isEmpty()) {
                hashCode.addStatement("int result = 1");
            } else {
                String args = String.join(",\n ", normalFields);
                hashCode.addStatement("int result = $T.hash($L)", Objects.class, args);
            }

            arrayFields.forEach(field ->
                    hashCode.addStatement("result = 31 * result + $T.hashCode($L)", Arrays.class, field));

            hashCode.addStatement("return result");
        }

        builder.addMethod(hashCode.build());
    }

    private void collectFieldNames(FieldNode node, List<String> names,
                                   java.util.function.Function<FieldDefinition, String> fieldNameResolver) {
        FieldDefinition field = node.getField();

        // Skip 88-level condition names
        if (field.getLevel() == 88) {
            return;
        }

        if (shouldFlattenGroup(field)) {
            node.getChildren().forEach(child -> collectFieldNames(child, names, fieldNameResolver));
        } else {
            names.add(fieldNameResolver.apply(field));
        }
    }

    private boolean isArrayField(String fieldName, List<FieldNode> fieldTree,
                                 java.util.function.Function<FieldDefinition, String> fieldNameResolver) {
        FieldDefinition fd = findFieldInTree(fieldTree, fieldName, fieldNameResolver);
        return fd != null && getFieldType(fd) instanceof ArrayTypeName;
    }

    private FieldDefinition findFieldInTree(List<FieldNode> nodes, String fieldName,
                                           java.util.function.Function<FieldDefinition, String> fieldNameResolver) {
        return nodes.stream()
                .map(node -> findFieldDefinition(node, fieldName, fieldNameResolver))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private FieldDefinition findFieldDefinition(FieldNode node, String fieldName,
                                               java.util.function.Function<FieldDefinition, String> fieldNameResolver) {
        if (fieldNameResolver.apply(node.getField()).equals(fieldName)) {
            return node.getField();
        }
        return node.getChildren().stream()
                .map(child -> findFieldDefinition(child, fieldName, fieldNameResolver))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private TypeName getFieldType(FieldDefinition field) {
        TypeName baseType = typeResolver.getBaseJavaType(field);
        return field.getOccurs() > 1 ? ArrayTypeName.of(baseType) : baseType;
    }

    private boolean shouldFlattenGroup(FieldDefinition field) {
        return field.isGroup() && field.getOccurs() == 1;
    }
}

