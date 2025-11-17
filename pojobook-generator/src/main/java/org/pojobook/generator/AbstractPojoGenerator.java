package org.pojobook.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.parser.CopybookDefinition;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class AbstractPojoGenerator {

    protected final FieldNameTracker fieldNameTracker = new FieldNameTracker();

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
     * Get base Java type for a field.
     */
    protected TypeName getBaseJavaType(FieldDefinition field) {
        return switch (field.getType()) {
            case DISPLAY -> getDisplayType(field);
            case COMP, COMP_5 -> getCompType(field);
            case COMP_1 -> ClassName.get(Float.class);
            case COMP_2 -> ClassName.get(Double.class);
            case COMP_3, PACKED_DECIMAL -> getPackedDecimalType(field);
            case ZONED_DECIMAL -> ClassName.get(BigDecimal.class);
        };
    }

    private TypeName getDisplayType(FieldDefinition field) {
        if (!isNumericField(field)) {
            return ClassName.get(String.class);
        }
        return field.getDecimalDigits() > 0
                ? ClassName.get(BigDecimal.class)
                : getIntegerType(field.getIntegerDigits());
    }

    private boolean isNumericField(FieldDefinition field) {
        if (field.getIntegerDigits() == 0 || field.getPicture() == null) {
            return false;
        }
        String pic = field.getPicture();
        return pic.startsWith("9") || (field.isSigned() && pic.startsWith("S9"));
    }

    private TypeName getCompType(FieldDefinition field) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        if (totalDigits <= 4) return ClassName.get(Short.class);
        if (totalDigits <= 9) return ClassName.get(Integer.class);
        return ClassName.get(Long.class);
    }

    private TypeName getPackedDecimalType(FieldDefinition field) {
        return field.getDecimalDigits() > 0
                ? ClassName.get(BigDecimal.class)
                : getIntegerType(field.getIntegerDigits());
    }

    private TypeName getIntegerType(int digits) {
        if (digits <= 9) return ClassName.get(Integer.class);
        if (digits <= 18) return ClassName.get(Long.class);
        return ClassName.get(BigInteger.class);
    }

    /**
     * Add initialization statement in constructor.
     */
    protected void addConstructorInitialization(MethodSpec.Builder constructor, FieldNode node) {
        FieldDefinition field = node.getField();

        if (shouldGenerateNestedClass(field, node)) {
            initializeNestedClassArray(constructor, field);
        } else if (shouldFlattenGroup(field)) {
            node.getChildren().forEach(child -> addConstructorInitialization(constructor, child));
        } else {
            initializeSimpleField(constructor, field);
        }
    }

    private void initializeNestedClassArray(MethodSpec.Builder constructor, FieldDefinition field) {
        String fieldName = fieldNameTracker.getJavaFieldName(field);
        String className = NamingUtils.toPascalCase(field.getName());
        constructor.addStatement("this.$L = new $L[$L]", fieldName, className, field.getOccurs());
        constructor.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
        constructor.addStatement("this.$L[i] = new $L()", fieldName, className);
        constructor.endControlFlow();
    }

    private void initializeSimpleField(MethodSpec.Builder constructor, FieldDefinition field) {
        String fieldName = fieldNameTracker.getJavaFieldName(field);
        String defaultValue = getDefaultValue(field);
        constructor.addStatement("this.$L = $L", fieldName, defaultValue);
    }

    /**
     * Get default value for a field.
     */
    private String getDefaultValue(FieldDefinition field) {
        if (field.getOccurs() > 1) {
            String typeName = getBaseJavaType(field).toString();
            return String.format("new %s[%d]", typeName, field.getOccurs());
        }

        return switch (getBaseJavaType(field).toString()) {
            case "java.lang.String" -> "\"\"";
            case "java.lang.Integer" -> "0";
            case "java.lang.Long" -> "0L";
            case "java.lang.Short" -> "(short) 0";
            case "java.lang.Float" -> "0.0f";
            case "java.lang.Double" -> "0.0";
            case "java.math.BigDecimal" -> "java.math.BigDecimal.ZERO";
            case "java.math.BigInteger" -> "java.math.BigInteger.ZERO";
            default -> "null";
        };
    }

    /**
     * Add getters and setters.
     */
    protected void addGettersSetters(TypeSpec.Builder builder, FieldNode node) {
        FieldDefinition field = node.getField();

        if (shouldGenerateNestedClass(field, node)) {
            String arrayType = NamingUtils.toPascalCase(field.getName()) + "[]";
            addGetterSetterForField(builder, field, arrayType);
        } else if (shouldFlattenGroup(field)) {
            node.getChildren().forEach(child -> addGettersSetters(builder, child));
        } else {
            addGetterSetterForField(builder, field, getJavaType(field).toString());
        }
    }

    /**
     * Add getter and setter for a field.
     */
    private void addGetterSetterForField(TypeSpec.Builder builder, FieldDefinition field, String javaType) {
        String fieldName = fieldNameTracker.getJavaFieldName(field);
        String methodName = capitalizeFieldName(fieldName);
        TypeName type = parseTypeName(javaType);

        builder.addMethod(createGetter(methodName, fieldName, type));
        builder.addMethod(createSetter(methodName, fieldName, type, field, javaType));
    }

    private String capitalizeFieldName(String fieldName) {
        return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private MethodSpec createGetter(String methodName, String fieldName, TypeName type) {
        return MethodSpec.methodBuilder("get" + methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addStatement("return $L", fieldName)
                .build();
    }

    private MethodSpec createSetter(String methodName, String fieldName, TypeName type,
                                    FieldDefinition field, String javaType) {
        MethodSpec.Builder setter = MethodSpec.methodBuilder("set" + methodName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(type, fieldName)
                .returns(void.class);

        addValidationAndAssignment(setter, field, fieldName, javaType);

        return setter.build();
    }

    private void addValidationAndAssignment(MethodSpec.Builder setter, FieldDefinition field,
                                           String fieldName, String javaType) {
        ClassName utilClass = ClassName.get("org.pojobook.util", "FieldLengthUtil");

        // Add assignment with validation inline
        if (javaType.equals("java.math.BigDecimal") || javaType.equals("BigDecimal")) {
            addBigDecimalValidationAndAssignment(setter, field, fieldName, utilClass);
        } else if (javaType.equals("java.math.BigDecimal[]") || javaType.equals("BigDecimal[]")) {
            addBigDecimalArrayValidationAndAssignment(setter, field, fieldName, utilClass);
        } else {
            // For all other types, generate a single statement with inline validation
            addSimpleValidationAndAssignment(setter, field, fieldName, javaType, utilClass);
        }
    }

    private void addSimpleValidationAndAssignment(MethodSpec.Builder setter, FieldDefinition field,
                                                  String fieldName, String javaType, ClassName utilClass) {
        // String validation
        if (javaType.equals("java.lang.String") && field.getIntegerDigits() > 0) {
            setter.addStatement("this.$L = $T.checkStringLength($L, $L, $S)",
                    fieldName, utilClass, fieldName, field.getIntegerDigits(), field.getName());
            return;
        }

        // String array validation
        if (javaType.contains("String") && javaType.endsWith("[]")) {
            int maxLength = field.getIntegerDigits() > 0 ? field.getIntegerDigits() : 1;
            setter.addStatement("this.$L = $T.checkStringArrayLength($L, $L, $S)",
                    fieldName, utilClass, fieldName, maxLength, field.getName());
            return;
        }

        // Numeric validation (non-BigDecimal)
        if (field.getIntegerDigits() > 0 && isNumericType(javaType) && !javaType.contains("BigDecimal")) {
            if (javaType.contains("BigInteger")) {
                setter.addStatement("this.$L = $T.checkBigIntegerRange($L, $L, $S)",
                        fieldName, utilClass, fieldName, field.getIntegerDigits(), field.getName());
            } else if (javaType.contains("Integer")) {
                setter.addStatement("this.$L = $T.checkIntegerRange($L, $L, $S)",
                        fieldName, utilClass, fieldName, field.getIntegerDigits(), field.getName());
            } else if (javaType.contains("Long")) {
                setter.addStatement("this.$L = $T.checkLongRange($L, $L, $S)",
                        fieldName, utilClass, fieldName, field.getIntegerDigits(), field.getName());
            } else if (javaType.contains("Short")) {
                setter.addStatement("this.$L = $T.checkShortRange($L, $L, $S)",
                        fieldName, utilClass, fieldName, field.getIntegerDigits(), field.getName());
            }
            return;
        }

        // Numeric array validation (non-BigDecimal)
        if (field.getIntegerDigits() > 0 && javaType.endsWith("[]")) {
            String baseType = javaType.substring(0, javaType.length() - 2);
            if (isNumericType(baseType) && !baseType.contains("BigDecimal")) {
                if (baseType.contains("BigInteger")) {
                    setter.addStatement("this.$L = $T.checkBigIntegerArrayRange($L, $L, $S)",
                            fieldName, utilClass, fieldName, field.getIntegerDigits(), field.getName());
                } else if (baseType.contains("Integer")) {
                    setter.addStatement("this.$L = $T.checkIntegerArrayRange($L, $L, $S)",
                            fieldName, utilClass, fieldName, field.getIntegerDigits(), field.getName());
                } else if (baseType.contains("Long")) {
                    setter.addStatement("this.$L = $T.checkLongArrayRange($L, $L, $S)",
                            fieldName, utilClass, fieldName, field.getIntegerDigits(), field.getName());
                } else if (baseType.contains("Short")) {
                    setter.addStatement("this.$L = $T.checkShortArrayRange($L, $L, $S)",
                            fieldName, utilClass, fieldName, field.getIntegerDigits(), field.getName());
                }
                return;
            }
        }

        // Array length validation
        if (javaType.endsWith("[]") && field.getOccurs() > 1) {
            setter.addStatement("this.$L = $T.checkArrayLength($L, $L, $S)",
                    fieldName, utilClass, fieldName, field.getOccurs(), field.getName());
            return;
        }

        // No validation needed
        setter.addStatement("this.$L = $L", fieldName, fieldName);
    }

    private void addBigDecimalValidationAndAssignment(MethodSpec.Builder setter, FieldDefinition field,
                                                      String fieldName, ClassName utilClass) {
        if (field.getIntegerDigits() > 0) {
            // Store validated value once
            setter.addStatement("$T validated = $T.checkBigDecimalRange($L, $L, $S)",
                    BigDecimal.class, utilClass, fieldName, field.getIntegerDigits(), field.getName());
            setter.beginControlFlow("if (validated != null)");
            setter.addStatement("this.$L = validated.stripTrailingZeros()", fieldName);
            setter.nextControlFlow("else");
            setter.addStatement("this.$L = validated", fieldName);
            setter.endControlFlow();
        } else {
            // No validation, just stripTrailingZeros
            setter.beginControlFlow("if ($L != null)", fieldName);
            setter.addStatement("this.$L = $L.stripTrailingZeros()", fieldName, fieldName);
            setter.nextControlFlow("else");
            setter.addStatement("this.$L = $L", fieldName, fieldName);
            setter.endControlFlow();
        }
    }

    private void addBigDecimalArrayValidationAndAssignment(MethodSpec.Builder setter, FieldDefinition field,
                                                           String fieldName, ClassName utilClass) {
        if (field.getIntegerDigits() > 0) {
            // Store validated array once
            setter.addStatement("$T[] validated = $T.checkBigDecimalArrayRange($L, $L, $S)",
                    BigDecimal.class, utilClass, fieldName, field.getIntegerDigits(), field.getName());
        } else {
            setter.addStatement("$T[] validated = $L", BigDecimal.class, fieldName);
        }

        setter.beginControlFlow("if (validated != null)");
        setter.addStatement("this.$L = new $T[validated.length]", fieldName, BigDecimal.class);
        setter.beginControlFlow("for (int i = 0; i < validated.length; i++)");
        setter.beginControlFlow("if (validated[i] != null)");
        setter.addStatement("this.$L[i] = validated[i].stripTrailingZeros()", fieldName);
        setter.nextControlFlow("else");
        setter.addStatement("this.$L[i] = null", fieldName);
        setter.endControlFlow();
        setter.endControlFlow();
        setter.nextControlFlow("else");
        setter.addStatement("this.$L = null", fieldName);
        setter.endControlFlow();
    }


    private boolean isNumericType(String javaType) {
        return javaType.matches(".*(Integer|Long|Short|BigInteger|BigDecimal).*");
    }

    /**
     * Parse type name from string.
     */
    private TypeName parseTypeName(String typeName) {
        if (typeName.endsWith("[]")) {
            String elementType = typeName.substring(0, typeName.length() - 2);
            return ArrayTypeName.of(parseTypeName(elementType));
        }

        return switch (typeName) {
            case "java.lang.String", "String" -> ClassName.get(String.class);
            case "java.lang.Integer", "Integer" -> ClassName.get(Integer.class);
            case "java.lang.Long", "Long" -> ClassName.get(Long.class);
            case "java.lang.Short", "Short" -> ClassName.get(Short.class);
            case "java.lang.Float", "Float" -> ClassName.get(Float.class);
            case "java.lang.Double", "Double" -> ClassName.get(Double.class);
            case "java.math.BigDecimal", "BigDecimal" -> ClassName.get(BigDecimal.class);
            case "java.math.BigInteger", "BigInteger" -> ClassName.get(BigInteger.class);
            default -> ClassName.bestGuess(typeName);
        };
    }

    /**
     * Get Java type for a field.
     */
    protected TypeName getJavaType(FieldDefinition field) {
        TypeName baseType = getBaseJavaType(field);
        return field.getOccurs() > 1 ? ArrayTypeName.of(baseType) : baseType;
    }

    /**
     * Add toString method.
     */
    protected void addToString(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class);

        CodeBlock.Builder code = CodeBlock.builder();
        code.add("return $S + ", className + "{");

        List<CodeBlock> parts = new ArrayList<>();
        fieldTree.forEach(field -> collectToStringParts(field, parts));

        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) code.add("$S + ", ", ");
            code.add(parts.get(i));
        }

        code.add("$S;", "}");
        method.addCode(code.build());
        builder.addMethod(method.build());
    }

    /**
     * Add fields to toString.
     */
    private void collectToStringParts(FieldNode node, List<CodeBlock> parts) {
        FieldDefinition field = node.getField();

        if (shouldFlattenGroup(field)) {
            node.getChildren().forEach(child -> collectToStringParts(child, parts));
        } else {
            String fieldName = fieldNameTracker.getJavaFieldName(field);
            if (getJavaType(field) instanceof ArrayTypeName) {
                parts.add(CodeBlock.of("$S + $T.toString($L) +", fieldName + "=", Arrays.class, fieldName));
            } else {
                parts.add(CodeBlock.of("$S + $L +", fieldName + "=", fieldName));
            }
        }
    }

    /**
     * Add equals method.
     */
    protected void addEquals(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree) {
        MethodSpec.Builder equals = MethodSpec.methodBuilder("equals")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(Object.class, "o");

        equals.addStatement("if (this == o) return true");
        equals.addStatement("if (o == null || getClass() != o.getClass()) return false");
        equals.addStatement("$L that = ($L) o", className, className);

        List<String> fieldNames = new ArrayList<>();
        fieldTree.forEach(node -> collectFieldNames(node, fieldNames));

        if (fieldNames.isEmpty()) {
            equals.addStatement("return true");
        } else {
            List<CodeBlock> comparisons = fieldNames.stream()
                    .map(name -> createComparison(name, fieldTree))
                    .toList();

            CodeBlock joined = comparisons.stream()
                    .reduce((a, b) -> CodeBlock.of("$L &&\n $L", a, b))
                    .orElse(CodeBlock.of("true"));

            equals.addStatement("return $L", joined);
        }

        builder.addMethod(equals.build());
    }

    private CodeBlock createComparison(String fieldName, List<FieldNode> fieldTree) {
        FieldDefinition fd = findFieldInTree(fieldTree, fieldName);
        if (fd != null && getJavaType(fd) instanceof ArrayTypeName) {
            return CodeBlock.of("$T.equals($L, that.$L)", Arrays.class, fieldName, fieldName);
        }
        return CodeBlock.of("$T.equals($L, that.$L)", Objects.class, fieldName, fieldName);
    }

    /**
     * Add hashCode method.
     */
    protected void addHashCode(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        MethodSpec.Builder hashCode = MethodSpec.methodBuilder("hashCode")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class);

        List<String> allFieldNames = new ArrayList<>();
        fieldTree.forEach(node -> collectFieldNames(node, allFieldNames));

        if (allFieldNames.isEmpty()) {
            hashCode.addStatement("return 0");
        } else {
            List<String> arrayFields = allFieldNames.stream()
                    .filter(name -> isArrayField(name, fieldTree))
                    .toList();

            List<String> normalFields = allFieldNames.stream()
                    .filter(name -> !isArrayField(name, fieldTree))
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

    private boolean isArrayField(String fieldName, List<FieldNode> fieldTree) {
        FieldDefinition fd = findFieldInTree(fieldTree, fieldName);
        return fd != null && getJavaType(fd) instanceof ArrayTypeName;
    }

    /**
     * Collect field names from a node.
     */
    private void collectFieldNames(FieldNode node, List<String> names) {
        FieldDefinition field = node.getField();

        if (shouldFlattenGroup(field)) {
            node.getChildren().forEach(child -> collectFieldNames(child, names));
        } else {
            names.add(fieldNameTracker.getJavaFieldName(field));
        }
    }

    /**
     * Find field in tree.
     */
    private FieldDefinition findFieldInTree(List<FieldNode> nodes, String fieldName) {
        return nodes.stream()
                .map(node -> findFieldDefinition(node, fieldName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Find field definition by name.
     */
    private FieldDefinition findFieldDefinition(FieldNode node, String fieldName) {
        if (fieldNameTracker.getJavaFieldName(node.getField()).equals(fieldName)) {
            return node.getField();
        }
        return node.getChildren().stream()
                .map(child -> findFieldDefinition(child, fieldName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
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
