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
import java.math.RoundingMode;
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

        addValidations(setter, field, fieldName, javaType);
        addFieldAssignment(setter, field, fieldName, javaType);

        return setter.build();
    }

    private void addValidations(MethodSpec.Builder setter, FieldDefinition field,
                                String fieldName, String javaType) {
        if (javaType.equals("java.lang.String") && field.getIntegerDigits() > 0) {
            addStringValidation(setter, field, fieldName);
        }

        addNumericValidation(setter, field, fieldName, javaType);

        if (javaType.contains("String") && javaType.endsWith("[]")) {
            addStringArrayValidation(setter, field, fieldName);
        }

        addNumericArrayValidation(setter, field, fieldName, javaType);

        if (javaType.endsWith("[]") && field.getOccurs() > 1) {
            addArrayLengthValidation(setter, field, fieldName);
        }
    }

    private void addStringValidation(MethodSpec.Builder setter, FieldDefinition field, String fieldName) {
        setter.beginControlFlow("if ($L != null && $L.length() > $L)",
                fieldName, fieldName, field.getIntegerDigits());
        setter.addStatement("throw new $T($S)", IllegalArgumentException.class,
                "Field " + field.getName() + " exceeds maximum length of " + field.getIntegerDigits());
        setter.endControlFlow();
    }

    private void addStringArrayValidation(MethodSpec.Builder setter, FieldDefinition field, String fieldName) {
        int maxLength = field.getIntegerDigits() > 0 ? field.getIntegerDigits() : 1;
        setter.beginControlFlow("if ($L != null)", fieldName);
        setter.beginControlFlow("for (int i = 0; i < $L.length; i++)", fieldName);
        setter.beginControlFlow("if ($L[i] != null && $L[i].length() > $L)",
                fieldName, fieldName, maxLength);
        setter.addStatement("throw new $T($S + i + $S)", IllegalArgumentException.class,
                "Field " + field.getName() + "[", "] exceeds maximum length of " + maxLength);
        setter.endControlFlow();
        setter.endControlFlow();
        setter.endControlFlow();
    }

    private void addArrayLengthValidation(MethodSpec.Builder setter, FieldDefinition field, String fieldName) {
        setter.beginControlFlow("if ($L != null && $L.length != $L)",
                fieldName, fieldName, field.getOccurs());
        setter.addStatement("throw new $T($S + $L.length)", IllegalArgumentException.class,
                "Field " + field.getName() + " array length must be exactly " + field.getOccurs() + " but was ",
                fieldName);
        setter.endControlFlow();
    }

    private void addFieldAssignment(MethodSpec.Builder setter, FieldDefinition field,
                                    String fieldName, String javaType) {
        if (javaType.equals("java.math.BigDecimal") || javaType.equals("BigDecimal")) {
            addBigDecimalAssignment(setter, fieldName);
        } else if (javaType.equals("java.math.BigDecimal[]") || javaType.equals("BigDecimal[]")) {
            addBigDecimalArrayAssignment(setter, fieldName);
        } else {
            setter.addStatement("this.$L = $L", fieldName, fieldName);
        }
    }

    private void addBigDecimalAssignment(MethodSpec.Builder setter, String fieldName) {
        setter.beginControlFlow("if ($L != null)", fieldName);
        setter.addStatement("this.$L = $L.stripTrailingZeros()", fieldName, fieldName);
        setter.nextControlFlow("else");
        setter.addStatement("this.$L = $L", fieldName, fieldName);
        setter.endControlFlow();
    }

    private void addBigDecimalArrayAssignment(MethodSpec.Builder setter, String fieldName) {
        setter.beginControlFlow("if ($L != null)", fieldName);
        setter.addStatement("this.$L = new $T[$L.length]", fieldName, BigDecimal.class, fieldName);
        setter.beginControlFlow("for (int i = 0; i < $L.length; i++)", fieldName);
        setter.beginControlFlow("if ($L[i] != null)", fieldName);
        setter.addStatement("this.$L[i] = $L[i].stripTrailingZeros()", fieldName, fieldName);
        setter.nextControlFlow("else");
        setter.addStatement("this.$L[i] = null", fieldName);
        setter.endControlFlow();
        setter.endControlFlow();
        setter.nextControlFlow("else");
        setter.addStatement("this.$L = null", fieldName);
        setter.endControlFlow();
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
     * Add numeric validation for a single numeric field.
     */
    private void addNumericValidation(MethodSpec.Builder setter, FieldDefinition field,
                                      String fieldName, String javaType) {
        if (field.getIntegerDigits() == 0 || !isNumericType(javaType)) {
            return;
        }

        ValidationContext ctx = new ValidationContext(field, fieldName, javaType);

        if (javaType.contains("BigDecimal")) {
            addBigDecimalValidation(setter, ctx);
        } else if (javaType.contains("BigInteger")) {
            addBigIntegerValidation(setter, ctx);
        } else {
            addPrimitiveValidation(setter, ctx);
        }
    }

    private boolean isNumericType(String javaType) {
        return javaType.matches(".*(Integer|Long|Short|BigInteger|BigDecimal).*");
    }

    private void addBigDecimalValidation(MethodSpec.Builder setter, ValidationContext ctx) {
        setter.beginControlFlow("if ($L != null)", ctx.fieldName);
        setter.addStatement("$T integerPart = $L.abs().setScale(0, $T.DOWN)",
                BigDecimal.class, ctx.fieldName, RoundingMode.class);
        setter.addStatement("$T maxAllowed = new $T($S)",
                BigDecimal.class, BigDecimal.class, String.valueOf(ctx.maxValue));
        setter.beginControlFlow("if (integerPart.compareTo(maxAllowed) > 0)");
        setter.addStatement("throw new $T($S)", IllegalArgumentException.class,
                "Field " + ctx.field.getName() + " exceeds maximum integer digits of " + ctx.field.getIntegerDigits());
        setter.endControlFlow();
        setter.endControlFlow();
    }

    private void addBigIntegerValidation(MethodSpec.Builder setter, ValidationContext ctx) {
        setter.beginControlFlow("if ($L != null)", ctx.fieldName);
        setter.addStatement("$T maxAllowed = new $T($S)",
                BigInteger.class, BigInteger.class, String.valueOf(ctx.maxValue));
        setter.addStatement("$T minAllowed = new $T($S)",
                BigInteger.class, BigInteger.class, String.valueOf(ctx.minValue));
        setter.beginControlFlow("if ($L.compareTo(maxAllowed) > 0 || $L.compareTo(minAllowed) < 0)",
                ctx.fieldName, ctx.fieldName);
        setter.addStatement("throw new $T($S)", IllegalArgumentException.class,
                "Field " + ctx.field.getName() + " value out of range for " + ctx.field.getIntegerDigits() + " digits");
        setter.endControlFlow();
        setter.endControlFlow();
    }

    private void addPrimitiveValidation(MethodSpec.Builder setter, ValidationContext ctx) {
        setter.beginControlFlow("if ($L != null)", ctx.fieldName);
        if (ctx.javaType.contains("Long")) {
            setter.beginControlFlow("if ($L > $LL || $L < $LL)",
                    ctx.fieldName, ctx.maxValue, ctx.fieldName, ctx.minValue);
        } else {
            setter.beginControlFlow("if ($L > $L || $L < $L)",
                    ctx.fieldName, (int) ctx.maxValue, ctx.fieldName, (int) ctx.minValue);
        }
        setter.addStatement("throw new $T($S)", IllegalArgumentException.class,
                "Field " + ctx.field.getName() + " value out of range for " + ctx.field.getIntegerDigits() + " digits");
        setter.endControlFlow();
        setter.endControlFlow();
    }

    /**
     * Add numeric validation for numeric arrays.
     */
    private void addNumericArrayValidation(MethodSpec.Builder setter, FieldDefinition field,
                                           String fieldName, String javaType) {
        if (field.getIntegerDigits() == 0 || !javaType.endsWith("[]")) {
            return;
        }

        String baseType = javaType.substring(0, javaType.length() - 2);
        if (!isNumericType(baseType)) {
            return;
        }

        ValidationContext ctx = new ValidationContext(field, fieldName, baseType);

        setter.beginControlFlow("if ($L != null)", fieldName);
        setter.beginControlFlow("for (int i = 0; i < $L.length; i++)", fieldName);

        if (baseType.contains("BigDecimal")) {
            addBigDecimalArrayElementValidation(setter, ctx);
        } else if (baseType.contains("BigInteger")) {
            addBigIntegerArrayElementValidation(setter, ctx);
        } else {
            addPrimitiveArrayElementValidation(setter, ctx);
        }

        setter.endControlFlow();
        setter.endControlFlow();
    }

    private void addBigDecimalArrayElementValidation(MethodSpec.Builder setter, ValidationContext ctx) {
        setter.beginControlFlow("if ($L[i] != null)", ctx.fieldName);
        setter.addStatement("$T integerPart = $L[i].abs().setScale(0, $T.DOWN)",
                BigDecimal.class, ctx.fieldName, RoundingMode.class);
        setter.addStatement("$T maxAllowed = new $T($S)",
                BigDecimal.class, BigDecimal.class, String.valueOf(ctx.maxValue));
        setter.beginControlFlow("if (integerPart.compareTo(maxAllowed) > 0)");
        setter.addStatement("throw new $T($S + i + $S)", IllegalArgumentException.class,
                "Field " + ctx.field.getName() + "[", "] exceeds maximum integer digits of " + ctx.field.getIntegerDigits());
        setter.endControlFlow();
        setter.endControlFlow();
    }

    private void addBigIntegerArrayElementValidation(MethodSpec.Builder setter, ValidationContext ctx) {
        setter.beginControlFlow("if ($L[i] != null)", ctx.fieldName);
        setter.addStatement("$T maxAllowed = new $T($S)",
                BigInteger.class, BigInteger.class, String.valueOf(ctx.maxValue));
        setter.addStatement("$T minAllowed = new $T($S)",
                BigInteger.class, BigInteger.class, String.valueOf(ctx.minValue));
        setter.beginControlFlow("if ($L[i].compareTo(maxAllowed) > 0 || $L[i].compareTo(minAllowed) < 0)",
                ctx.fieldName, ctx.fieldName);
        setter.addStatement("throw new $T($S + i + $S)", IllegalArgumentException.class,
                "Field " + ctx.field.getName() + "[", "] value out of range for " + ctx.field.getIntegerDigits() + " digits");
        setter.endControlFlow();
        setter.endControlFlow();
    }

    private void addPrimitiveArrayElementValidation(MethodSpec.Builder setter, ValidationContext ctx) {
        setter.beginControlFlow("if ($L[i] != null)", ctx.fieldName);
        if (ctx.javaType.contains("Long")) {
            setter.beginControlFlow("if ($L[i] > $LL || $L[i] < $LL)",
                    ctx.fieldName, ctx.maxValue, ctx.fieldName, ctx.minValue);
        } else {
            setter.beginControlFlow("if ($L[i] > $L || $L[i] < $L)",
                    ctx.fieldName, (int) ctx.maxValue, ctx.fieldName, (int) ctx.minValue);
        }
        setter.addStatement("throw new $T($S + i + $S)", IllegalArgumentException.class,
                "Field " + ctx.field.getName() + "[", "] value out of range for " + ctx.field.getIntegerDigits() + " digits");
        setter.endControlFlow();
        setter.endControlFlow();
    }

    /**
     * Calculate the maximum integer value based on number of digits.
     */
    private long calculateMaxIntegerValue(int digits) {
        if (digits == 0) return 0;
        long max = 1;
        for (int i = 0; i < digits; i++) {
            max *= 10;
        }
        return max - 1;
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
     * Context for validation operations.
     */
    private class ValidationContext {
        final FieldDefinition field;
        final String fieldName;
        final String javaType;
        final long maxValue;
        final long minValue;

        ValidationContext(FieldDefinition field, String fieldName, String javaType) {
            this.field = field;
            this.fieldName = fieldName;
            this.javaType = javaType;
            this.maxValue = calculateMaxIntegerValue(field.getIntegerDigits());
            this.minValue = field.isSigned() ? -maxValue : 0;
        }
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
