package org.c4rth.pojobook.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.c4rth.pojobook.parser.CopybookDefinition;
import org.c4rth.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

public abstract class AbstractPojoGenerator {


    abstract public String generate(CopybookDefinition definition);

    abstract protected TypeSpec buildNestedClass(String className, FieldNode node);

    /**
     * Map to track field name usage counts for ensuring uniqueness.
     */
    private final Map<String, Integer> fieldNameCounts = new HashMap<>();

    /**
     * Map to store COBOL field name to unique Java field name mapping.
     */
    private final Map<String, String> cobolToJavaNameMap = new HashMap<>();

    /**
     * Stack to save/restore field name tracking state for nested classes.
     */
    private final Stack<Map<String, Integer>> fieldNameCountsStack = new Stack<>();
    private final Stack<Map<String, String>> cobolToJavaNameMapStack = new Stack<>();

    /**
     * Convert COBOL name to camelCase and ensure uniqueness.
     * This should be called during field creation phase with the FieldDefinition.
     */
    protected String toUniqueFieldName(FieldDefinition field) {
        String cobolName = field.getName();
        // Use cobolName + lineNumber as unique key to handle duplicate field names
        String uniqueKey = cobolName + "_" + field.getLineNumber();

        // Check if we already have a mapping for this specific field
        if (cobolToJavaNameMap.containsKey(uniqueKey)) {
            return cobolToJavaNameMap.get(uniqueKey);
        }

        String baseName = toCamelCase(cobolName);

        // Check if this name has been used before
        Integer count = fieldNameCounts.get(baseName);
        String uniqueName;
        if (count == null) {
            // First occurrence
            fieldNameCounts.put(baseName, 1);
            uniqueName = baseName;
        } else {
            // Duplicate - append counter
            count++;
            fieldNameCounts.put(baseName, count);
            uniqueName = baseName + count;
        }

        // Store the mapping
        cobolToJavaNameMap.put(uniqueKey, uniqueName);
        return uniqueName;
    }

    /**
     * Get the unique Java field name for a field (must have been created via toUniqueFieldName first).
     */
    protected String getJavaFieldName(FieldDefinition field) {
        String uniqueKey = field.getName() + "_" + field.getLineNumber();
        return cobolToJavaNameMap.getOrDefault(uniqueKey, toCamelCase(field.getName()));
    }

    /**
     * Reset field name tracking (call at start of each class generation).
     */
    protected void resetFieldNameTracking() {
        fieldNameCounts.clear();
        cobolToJavaNameMap.clear();
    }

    /**
     * Save current field name tracking state (for nested class generation).
     */
    protected void pushFieldNameTracking() {
        fieldNameCountsStack.push(new HashMap<>(fieldNameCounts));
        cobolToJavaNameMapStack.push(new HashMap<>(cobolToJavaNameMap));
        fieldNameCounts.clear();
        cobolToJavaNameMap.clear();
    }

    /**
     * Restore previous field name tracking state (after nested class generation).
     */
    protected void popFieldNameTracking() {
        if (!fieldNameCountsStack.isEmpty()) {
            fieldNameCounts.clear();
            fieldNameCounts.putAll(fieldNameCountsStack.pop());
        }
        if (!cobolToJavaNameMapStack.isEmpty()) {
            cobolToJavaNameMap.clear();
            cobolToJavaNameMap.putAll(cobolToJavaNameMapStack.pop());
        }
    }

    /**
     * Convert COBOL name to camelCase.
     */
    protected String toCamelCase(String name) {
        String[] parts = name.split("[-_]");
        StringBuilder result = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            result.append(Character.toUpperCase(parts[i].charAt(0)));
            result.append(parts[i].substring(1).toLowerCase());
        }
        return result.toString();
    }

    /**
     * Convert COBOL name to PascalCase.
     */
    protected String toPascalCase(String name) {
        String[] parts = name.split("[-_]");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                result.append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    /**
     * Generate and write class to file.
     */
    public void generateToFile(CopybookDefinition definition, Path outputPath) throws IOException {
        String code = generate(definition);

        // Create parent directories if they don't exist
        if (outputPath.getParent() != null) {
            java.nio.file.Files.createDirectories(outputPath.getParent());
        }

        java.nio.file.Files.writeString(outputPath, code);
    }

    /**
     * Generate nested classes for fields with OCCURS.
     */
    protected void generateNestedClasses(FieldNode node, Map<String, TypeSpec> nestedClasses) {
        FieldDefinition field = node.getField();

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            String className = toPascalCase(field.getName());

            if (!nestedClasses.containsKey(className)) {
                TypeSpec nestedClass = buildNestedClass(className, node);
                nestedClasses.put(className, nestedClass);
            }
        }

        if (field.isGroup() && field.getOccurs() == 1) {
            for (FieldNode child : node.getChildren()) {
                generateNestedClasses(child, nestedClasses);
            }
        }
    }

    /**
     * Generate nested classes for all children, including those in flattened groups.
     */
    protected void generateNestedClassesForChildren(FieldNode parentNode, Map<String, TypeSpec> nestedClasses) {
        for (FieldNode child : parentNode.getChildren()) {
            FieldDefinition childField = child.getField();

            if (childField.isGroup() && childField.getOccurs() > 1 && !child.getChildren().isEmpty()) {
                // This child needs a nested class
                String childClassName = toPascalCase(childField.getName());
                if (!nestedClasses.containsKey(childClassName)) {
                    TypeSpec nestedClass = buildNestedClass(childClassName, child);
                    nestedClasses.put(childClassName, nestedClass);
                }
            } else if (childField.isGroup() && childField.getOccurs() == 1 && !child.getChildren().isEmpty()) {
                // This is a flattened group, recursively check its children
                generateNestedClassesForChildren(child, nestedClasses);
            }
        }
    }

    /**
     * Get base Java type for a field.
     */
    protected TypeName getBaseJavaType(FieldDefinition field) {
        return switch (field.getType()) {
            case DISPLAY -> {
                if (field.getIntegerDigits() > 0 && field.getPicture() != null && field.getPicture().startsWith("9")) {
                    if (field.getDecimalDigits() > 0) {
                        yield ClassName.get(BigDecimal.class);
                    }
                    int totalDigits = field.getIntegerDigits();
                    if (totalDigits <= 9) {
                        yield ClassName.get(Integer.class);
                    } else if (totalDigits <= 18) {
                        yield ClassName.get(Long.class);
                    } else {
                        yield ClassName.get(BigInteger.class);
                    }
                }
                yield ClassName.get(String.class);
            }
            case COMP, COMP_5 -> {
                int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
                if (totalDigits <= 4) {
                    yield ClassName.get(Short.class);
                } else if (totalDigits <= 9) {
                    yield ClassName.get(Integer.class);
                } else {
                    yield ClassName.get(Long.class);
                }
            }
            case COMP_1 -> ClassName.get(Float.class);
            case COMP_2 -> ClassName.get(Double.class);
            case COMP_3, PACKED_DECIMAL -> {
                if (field.getDecimalDigits() > 0) {
                    yield ClassName.get(BigDecimal.class);
                } else {
                    int totalDigits = field.getIntegerDigits();
                    if (totalDigits <= 9) {
                        yield ClassName.get(Integer.class);
                    } else if (totalDigits <= 18) {
                        yield ClassName.get(Long.class);
                    } else {
                        yield ClassName.get(BigInteger.class);
                    }
                }
            }
            case ZONED_DECIMAL -> ClassName.get(BigDecimal.class);
        };
    }

    /**
     * Add initialization statement in constructor.
     */
    protected void addConstructorInitialization(MethodSpec.Builder constructor, FieldNode node) {
        FieldDefinition field = node.getField();

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Initialize array of nested class
            String fieldName = getJavaFieldName(field);
            String className = toPascalCase(field.getName());
            constructor.addStatement("this.$L = new $L[$L]", fieldName, className, field.getOccurs());
            constructor.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
            constructor.addStatement("this.$L[i] = new $L()", fieldName, className);
            constructor.endControlFlow();

        } else if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
            // Flatten - initialize children
            for (FieldNode child : node.getChildren()) {
                addConstructorInitialization(constructor, child);
            }

        } else {
            // Simple field
            String fieldName = getJavaFieldName(field);
            String defaultValue = getDefaultValue(field);
            constructor.addStatement("this.$L = $L", fieldName, defaultValue);
        }
    }

    /**
     * Get default value for a field.
     */
    private String getDefaultValue(FieldDefinition field) {
        if (field.getOccurs() > 1) {
            TypeName baseType = getBaseJavaType(field);
            String baseTypeName = baseType.toString();

            if (baseTypeName.equals("java.lang.String")) {
                return String.format("new String[%d]", field.getOccurs());
            } else {
                return String.format("new %s[%d]", baseTypeName, field.getOccurs());
            }
        }

        TypeName type = getBaseJavaType(field);
        String typeName = type.toString();

        return switch (typeName) {
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

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Group with OCCURS
            addGetterSetterForField(builder, field, toPascalCase(field.getName()) + "[]");

        } else if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
            // Flatten
            for (FieldNode child : node.getChildren()) {
                addGettersSetters(builder, child);
            }

        } else {
            // Simple field
            TypeName fieldType = getJavaType(field);
            addGetterSetterForField(builder, field, fieldType.toString());
        }
    }

    /**
     * Add getter and setter for a field.
     */
    private void addGetterSetterForField(TypeSpec.Builder builder, FieldDefinition field, String javaType) {
        String fieldName = getJavaFieldName(field);
        // Capitalize the first letter of the camelCase field name for method names
        String methodName = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        TypeName type = parseTypeName(javaType);

        // Getter
        MethodSpec getter = MethodSpec.methodBuilder("get" + methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addStatement("return $L", fieldName)
                .build();

        builder.addMethod(getter);

        // Setter with validation
        MethodSpec.Builder setter = MethodSpec.methodBuilder("set" + methodName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(type, fieldName)
                .returns(void.class);

        // Add validation for String fields
        if (javaType.equals("java.lang.String") && field.getIntegerDigits() > 0) {
            setter.beginControlFlow("if ($L != null && $L.length() > $L)", fieldName, fieldName, field.getIntegerDigits());
            setter.addStatement("throw new $T($S)", IllegalArgumentException.class,
                    "Field " + field.getName() + " exceeds maximum length of " + field.getIntegerDigits());
            setter.endControlFlow();
        }

        // Add validation for numeric fields
        addNumericValidation(setter, field, fieldName, javaType);

        // Add validation for String arrays
        if ((javaType.equals("java.lang.String[]") || (javaType.contains("String") && javaType.endsWith("[]")))) {
            int maxLength = field.getIntegerDigits() > 0 ? field.getIntegerDigits() : 1;
            setter.beginControlFlow("if ($L != null)", fieldName);
            setter.beginControlFlow("for (int i = 0; i < $L.length; i++)", fieldName);
            setter.beginControlFlow("if ($L[i] != null && $L[i].length() > $L)", fieldName, fieldName, maxLength);
            setter.addStatement("throw new $T($S + i + $S)", IllegalArgumentException.class,
                    "Field " + field.getName() + "[", "] exceeds maximum length of " + maxLength);
            setter.endControlFlow();
            setter.endControlFlow();
            setter.endControlFlow();
        }

        // Add validation for numeric arrays
        addNumericArrayValidation(setter, field, fieldName, javaType);

        // Add validation for array length
        if (javaType.endsWith("[]") && field.getOccurs() > 1) {
            setter.beginControlFlow("if ($L != null && $L.length != $L)", fieldName, fieldName, field.getOccurs());
            setter.addStatement("throw new $T($S + $L.length)", IllegalArgumentException.class,
                    "Field " + field.getName() + " array length must be exactly " + field.getOccurs() + " but was ", fieldName);
            setter.endControlFlow();
        }

        // Apply stripTrailingZeros for BigDecimal fields
        if (javaType.equals("java.math.BigDecimal") || javaType.equals("BigDecimal")) {
            setter.beginControlFlow("if ($L != null)", fieldName);
            setter.addStatement("this.$L = $L.stripTrailingZeros()", fieldName, fieldName);
            setter.nextControlFlow("else");
            setter.addStatement("this.$L = $L", fieldName, fieldName);
            setter.endControlFlow();
        } else if (javaType.equals("java.math.BigDecimal[]") || javaType.equals("BigDecimal[]")) {
            // Handle BigDecimal arrays
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
        } else {
            setter.addStatement("this.$L = $L", fieldName, fieldName);
        }

        builder.addMethod(setter.build());
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
    private void addNumericValidation(MethodSpec.Builder setter, FieldDefinition field, String fieldName, String javaType) {
        if (field.getIntegerDigits() == 0) {
            return; // No validation needed
        }

        boolean isNumeric = javaType.equals("java.lang.Integer") || javaType.equals("Integer") ||
                javaType.equals("java.lang.Long") || javaType.equals("Long") ||
                javaType.equals("java.lang.Short") || javaType.equals("Short") ||
                javaType.equals("java.math.BigInteger") || javaType.equals("BigInteger") ||
                javaType.equals("java.math.BigDecimal") || javaType.equals("BigDecimal");

        if (!isNumeric) {
            return;
        }

        // Calculate max value based on integer digits (considering sign)
        long maxValue = calculateMaxIntegerValue(field.getIntegerDigits());
        long minValue = field.isSigned() ? -maxValue : 0;

        if (javaType.contains("BigDecimal")) {
            setter.beginControlFlow("if ($L != null)", fieldName);
            setter.addStatement("$T integerPart = $L.abs().setScale(0, $T.DOWN)",
                    BigDecimal.class, fieldName, RoundingMode.class);
            setter.addStatement("$T maxAllowed = new $T($S)",
                    BigDecimal.class, BigDecimal.class, String.valueOf(maxValue));
            setter.beginControlFlow("if (integerPart.compareTo(maxAllowed) > 0)");
            setter.addStatement("throw new $T($S)", IllegalArgumentException.class,
                    "Field " + field.getName() + " exceeds maximum integer digits of " + field.getIntegerDigits());
            setter.endControlFlow();
            setter.endControlFlow();
        } else if (javaType.contains("BigInteger")) {
            setter.beginControlFlow("if ($L != null)", fieldName);
            setter.addStatement("$T maxAllowed = new $T($S)",
                    BigInteger.class, BigInteger.class, String.valueOf(maxValue));
            setter.addStatement("$T minAllowed = new $T($S)",
                    BigInteger.class, BigInteger.class, String.valueOf(minValue));
            setter.beginControlFlow("if ($L.compareTo(maxAllowed) > 0 || $L.compareTo(minAllowed) < 0)",
                    fieldName, fieldName);
            setter.addStatement("throw new $T($S)", IllegalArgumentException.class,
                    "Field " + field.getName() + " value out of range for " + field.getIntegerDigits() + " digits");
            setter.endControlFlow();
            setter.endControlFlow();
        } else {
            // For Integer, Long, Short
            setter.beginControlFlow("if ($L != null)", fieldName);
            if (javaType.contains("Long")) {
                setter.beginControlFlow("if ($L > $LL || $L < $LL)",
                        fieldName, maxValue, fieldName, minValue);
            } else {
                setter.beginControlFlow("if ($L > $L || $L < $L)",
                        fieldName, (int) maxValue, fieldName, (int) minValue);
            }
            setter.addStatement("throw new $T($S)", IllegalArgumentException.class,
                    "Field " + field.getName() + " value out of range for " + field.getIntegerDigits() + " digits");
            setter.endControlFlow();
            setter.endControlFlow();
        }
    }

    /**
     * Add numeric validation for numeric arrays.
     */
    private void addNumericArrayValidation(MethodSpec.Builder setter, FieldDefinition field, String fieldName, String javaType) {
        if (field.getIntegerDigits() == 0 || !javaType.endsWith("[]")) {
            return;
        }

        String baseType = javaType.substring(0, javaType.length() - 2);
        boolean isNumeric = baseType.equals("java.lang.Integer") || baseType.equals("Integer") ||
                baseType.equals("java.lang.Long") || baseType.equals("Long") ||
                baseType.equals("java.lang.Short") || baseType.equals("Short") ||
                baseType.equals("java.math.BigInteger") || baseType.equals("BigInteger") ||
                baseType.equals("java.math.BigDecimal") || baseType.equals("BigDecimal");

        if (!isNumeric) {
            return;
        }

        long maxValue = calculateMaxIntegerValue(field.getIntegerDigits());
        long minValue = field.isSigned() ? -maxValue : 0;

        setter.beginControlFlow("if ($L != null)", fieldName);
        setter.beginControlFlow("for (int i = 0; i < $L.length; i++)", fieldName);

        if (baseType.contains("BigDecimal")) {
            setter.beginControlFlow("if ($L[i] != null)", fieldName);
            setter.addStatement("$T integerPart = $L[i].abs().setScale(0, $T.DOWN)",
                    BigDecimal.class, fieldName, RoundingMode.class);
            setter.addStatement("$T maxAllowed = new $T($S)",
                    BigDecimal.class, BigDecimal.class, String.valueOf(maxValue));
            setter.beginControlFlow("if (integerPart.compareTo(maxAllowed) > 0)");
            setter.addStatement("throw new $T($S + i + $S)", IllegalArgumentException.class,
                    "Field " + field.getName() + "[", "] exceeds maximum integer digits of " + field.getIntegerDigits());
            setter.endControlFlow();
            setter.endControlFlow();
        } else if (baseType.contains("BigInteger")) {
            setter.beginControlFlow("if ($L[i] != null)", fieldName);
            setter.addStatement("$T maxAllowed = new $T($S)",
                    BigInteger.class, BigInteger.class, String.valueOf(maxValue));
            setter.addStatement("$T minAllowed = new $T($S)",
                    BigInteger.class, BigInteger.class, String.valueOf(minValue));
            setter.beginControlFlow("if ($L[i].compareTo(maxAllowed) > 0 || $L[i].compareTo(minAllowed) < 0)",
                    fieldName, fieldName);
            setter.addStatement("throw new $T($S + i + $S)", IllegalArgumentException.class,
                    "Field " + field.getName() + "[", "] value out of range for " + field.getIntegerDigits() + " digits");
            setter.endControlFlow();
            setter.endControlFlow();
        } else {
            setter.beginControlFlow("if ($L[i] != null)", fieldName);
            if (baseType.contains("Long")) {
                setter.beginControlFlow("if ($L[i] > $LL || $L[i] < $LL)",
                        fieldName, maxValue, fieldName, minValue);
            } else {
                setter.beginControlFlow("if ($L[i] > $L || $L[i] < $L)",
                        fieldName, (int) maxValue, fieldName, (int) minValue);
            }
            setter.addStatement("throw new $T($S + i + $S)", IllegalArgumentException.class,
                    "Field " + field.getName() + "[", "] value out of range for " + field.getIntegerDigits() + " digits");
            setter.endControlFlow();
            setter.endControlFlow();
        }

        setter.endControlFlow();
        setter.endControlFlow();
    }

    /**
     * Calculate the maximum integer value based on number of digits.
     */
    private long calculateMaxIntegerValue(int digits) {
        if (digits == 0) {
            return 0;
        }
        // For signed numbers, one position is used for sign, so max is 10^digits - 1
        // For unsigned, max is also 10^digits - 1
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

        if (field.getOccurs() > 1) {
            return ArrayTypeName.of(baseType);
        }

        return baseType;
    }

    /**
     * Add toString method.
     */
    protected void addToString(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class);

        CodeBlock.Builder code = CodeBlock.builder();
        code.add("return $S + ", "CobolRecord{");
        boolean first = true;
        for (FieldNode field : fieldTree) {
            addToStringFields(field, code, first);
            first = false;
        }
        code.add("$S;", "}");
        method.addCode(code.build());
        builder.addMethod(method.build());
    }

    /**
     * Add fields to toString.
     */

    private void addToStringFields(FieldNode node, CodeBlock.Builder code, boolean first) {
        FieldDefinition field = node.getField();

        if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
            for (FieldNode child : node.getChildren()) {
                addToStringFields(child, code, first);
                first = false;
            }
        } else {
            String prefix = first ? "" : ", ";
            String fieldName = getJavaFieldName(field);

            if (getJavaType(field) instanceof ArrayTypeName) {
                code.add("$S + $T.toString($L) +", prefix + fieldName + "=", Arrays.class, fieldName);
            } else {
                code.add("$S + $L +", prefix + fieldName + "=", fieldName);
            }
        }
    }

    /**
     * Add equals and hashCode methods.
     */
    protected void addEquals(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree) {
        // Equals
        MethodSpec.Builder equals = MethodSpec.methodBuilder("equals")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(Object.class, "o");

        equals.addStatement("if (this == o) return true");
        equals.addStatement("if (o == null || getClass() != o.getClass()) return false");
        equals.addStatement("$L that = ($L) o", className, className);

        CodeBlock.Builder codeEqual = CodeBlock.builder();

        List<CodeBlock> comparisons = new ArrayList<>();
        for (FieldNode node : fieldTree) {
            List<String> fieldNames = new ArrayList<>();
            collectFieldNames(node, fieldNames);
            for (String fieldName : fieldNames) {
                FieldDefinition fd = findFieldInTree(fieldTree, fieldName);
                if (fd != null && getJavaType(fd) instanceof ArrayTypeName) {
                    comparisons.add(CodeBlock.of("$T.equals($L, that.$L)", Arrays.class, fieldName, fieldName));
                } else {
                    comparisons.add(CodeBlock.of("$T.equals($L, that.$L)", Objects.class, fieldName, fieldName));
                }
            }
        }
        if (comparisons.isEmpty()) {
            codeEqual.addStatement("return true");
        } else {
            CodeBlock joined = comparisons.stream().reduce((a, b) -> CodeBlock.of("$L &&\n $L", a, b)).orElse(CodeBlock.of("true"));
            codeEqual.add("return $L", joined);
        }

        equals.addStatement(codeEqual.build());
        builder.addMethod(equals.build());
    }

    /**
     * Add equals and hashCode methods.
     */
    protected void addHashCode(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        // HashCode
        MethodSpec.Builder hashCode = MethodSpec.methodBuilder("hashCode")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class);

        CodeBlock.Builder codeHash = CodeBlock.builder();

        List<String> allFieldNames = new ArrayList<>();
        for (FieldNode node : fieldTree) {
            collectFieldNames(node, allFieldNames);
        }
        List<String> arrayFields = new ArrayList<>();
        List<String> normalFields = new ArrayList<>();
        for (String fieldName : allFieldNames) {
            FieldDefinition fd = findFieldInTree(fieldTree, fieldName);
            if (fd != null && getJavaType(fd) instanceof ArrayTypeName) {
                arrayFields.add(fieldName);
            } else {
                normalFields.add(fieldName);
            }
        }

        if (arrayFields.isEmpty() && normalFields.isEmpty()) {
            codeHash.addStatement("return 0");
        } else {
            if (normalFields.isEmpty()) {
                codeHash.addStatement("int result = 1");
            } else {
                String args = String.join(",\n ", normalFields);
                codeHash.addStatement("int result = $T.hash($L)", Objects.class, args);
            }
            for (String arrayField : arrayFields) {
                codeHash.addStatement("result = 31 * result + $T.hashCode($L)", Arrays.class, arrayField);
            }
            codeHash.addStatement("return result");
        }

        hashCode.addCode(codeHash.build());
        builder.addMethod(hashCode.build());
    }

    /**
     * Collect field names from a node.
     */
    private void collectFieldNames(FieldNode node, List<String> names) {
        FieldDefinition field = node.getField();

        if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
            for (FieldNode child : node.getChildren()) {
                collectFieldNames(child, names);
            }
        } else {
            names.add(getJavaFieldName(field));
        }
    }

    /**
     * Check if type is a nested class.
     */
    private boolean isNestedClass(String typeName) {
        // Simple heuristic: if it starts with uppercase and doesn't contain java., it's likely a nested class
        return !typeName.isEmpty() &&
                Character.isUpperCase(typeName.charAt(0)) &&
                !typeName.startsWith("java.");
    }

    /**
     * Find field in tree.
     */
    private FieldDefinition findFieldInTree(List<FieldNode> nodes, String fieldName) {
        for (FieldNode node : nodes) {
            FieldDefinition fd = findFieldDefinition(node, fieldName);
            if (fd != null) {
                return fd;
            }
        }
        return null;
    }

    /**
     * Find field definition by name.
     */
    private FieldDefinition findFieldDefinition(FieldNode node, String fieldName) {
        if (getJavaFieldName(node.getField()).equals(fieldName)) {
            return node.getField();
        }
        for (FieldNode child : node.getChildren()) {
            FieldDefinition fd = findFieldDefinition(child, fieldName);
            if (fd != null) {
                return fd;
            }
        }
        return null;
    }
}
