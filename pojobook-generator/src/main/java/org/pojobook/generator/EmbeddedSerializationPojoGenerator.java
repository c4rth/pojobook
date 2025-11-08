package org.pojobook.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.exception.DeserializationException;
import org.pojobook.exception.SerializationException;
import org.pojobook.parser.CopybookDefinition;
import org.pojobook.parser.FieldDefinition;
import org.pojobook.util.DisplayNumericUtil;
import org.pojobook.util.SignedNumericUtil;

import javax.lang.model.element.Modifier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Generates POJO classes with embedded serialization and deserialization methods.
 * Unlike the annotation-based PojoGenerator, this generator creates POJOs that
 * don't require reflection at runtime - all serialization logic is embedded in the class.
 */
public class EmbeddedSerializationPojoGenerator extends AbstractPojoGenerator {

    private final Map<FieldDefinition, Integer> fieldSizeCache = new HashMap<>();

    private String packageName = "org.pojobook.generated";

    public EmbeddedSerializationPojoGenerator() {
    }

    public EmbeddedSerializationPojoGenerator withPackage(String packageName) {
        this.packageName = packageName;
        return this;
    }

    /**
     * Generate a POJO class from a copybook definition.
     */
    @Override
    public String generate(CopybookDefinition definition) {
        // Reset state
        fieldNameTracker.reset();
        fieldSizeCache.clear();

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

        // Apply all builders
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
                },
                this::addSerializeMethodsToBuilder,
                context -> addDeserializeMethods(context.builder, className, context.fieldTree)
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
     * Build a nested class for a group field with OCCURS.
     */
    @Override
    protected TypeSpec buildNestedClass(String className, FieldNode node) {
        fieldNameTracker.push();

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        List<FieldNode> children = node.getChildren();
        Map<String, TypeSpec> childNestedClasses = generateNestedClassesForChildren(node);

        // Apply builders
        applyBuilders(builder,
                className,
                children,
                this::addFieldsToBuilder,
                this::addConstructorForNestedClassToBuilder,
                this::addGettersSettersToBuilder,
                context -> {
                    addEquals(context.builder, className, context.fieldTree);
                    addHashCode(context.builder, context.fieldTree);
                },
                this::addSerializeMethodsToBuilder,
                context -> addDeserializeMethods(context.builder, className, context.fieldTree)
        );

        childNestedClasses.values().forEach(builder::addType);

        fieldNameTracker.pop();
        return builder.build();
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
     * Add fields to builder.
     */
    private void addFieldsToBuilder(BuilderContext context) {
        context.fieldTree.stream()
                .flatMap(this::expandFieldNode)
                .forEach(context.builder::addField);
    }

    /**
     * Create simple field spec with Javadoc.
     */
    @Override
    protected FieldSpec createSimpleField(FieldDefinition field) {
        String fieldName = fieldNameTracker.toUniqueFieldName(field);
        return FieldSpec.builder(getJavaType(field), fieldName, Modifier.PRIVATE)
                .addJavadoc(buildFieldComment(field))
                .build();
    }

    /**
     * Create array field spec.
     */
    @Override
    protected FieldSpec createArrayField(FieldDefinition field) {
        String fieldName = fieldNameTracker.toUniqueFieldName(field);
        String className = NamingUtils.toPascalCase(field.getName());
        TypeName fieldType = ArrayTypeName.of(ClassName.bestGuess(className));

        return FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE)
                .addJavadoc(buildFieldComment(field))
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
     * Add constructor for nested class to builder.
     */
    private void addConstructorForNestedClassToBuilder(BuilderContext context) {
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
     * Add serialization methods to builder.
     */
    private void addSerializeMethodsToBuilder(BuilderContext context) {
        addSerializeMethods(context.builder, context.fieldTree);
    }

    /**
     * Build a comment describing the COBOL field definition.
     */
    private String buildFieldComment(FieldDefinition field) {
        StringBuilder comment = new StringBuilder(127);

        comment.append("COBOL: ").append(field.getName());
        comment.append(" - Level: ").append(String.format("%02d", field.getLevel()));

        // Add optional picture clause
        Optional.ofNullable(field.getPicture())
                .filter(pic -> !pic.isEmpty())
                .ifPresent(pic -> comment.append(" - PIC ").append(pic));

        comment.append(" - Type: ").append(field.getType());

        // Add sign information if present
        if (field.isSigned()) {
            comment.append(" - SIGNED");
            Optional.ofNullable(field.getSignPosition())
                    .filter(pos -> !pos.isEmpty())
                    .ifPresent(pos -> comment.append(" ").append(pos));
            if (field.isSignSeparate()) {
                comment.append(" SEPARATE");
            }
        }

        // Add occurs information
        if (field.getOccurs() > 1) {
            comment.append(" - OCCURS ").append(field.getOccurs());
            Optional.ofNullable(field.getDependingOn())
                    .filter(dep -> !dep.isEmpty())
                    .ifPresent(dep -> comment.append(" DEPENDING ON ").append(dep));
        }

        // Add redefines information
        Optional.ofNullable(field.getRedefines())
                .filter(ref -> !ref.isEmpty())
                .ifPresent(ref -> comment.append(" - REDEFINES ").append(ref));

        // Add value information
        Optional.ofNullable(field.getValue())
                .filter(val -> !val.isEmpty())
                .ifPresent(val -> comment.append(" - VALUE ").append(val));

        comment.append("\n");
        return comment.toString();
    }


    /**
     * Add serialize method to the class.
     */
    private void addSerializeMethods(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        // Calculate total size for pre-allocation
        int totalSize = calculateNestedClassSize(fieldTree);

        MethodSpec.Builder method1 = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(byte[].class)
                .addException(SerializationException.class)
                .addJavadoc("Serialize this object to COBOL binary format using CP1047 charset.\n")
                .addJavadoc("@return byte array containing the serialized data\n")
                .addJavadoc("@throws Serialization if an I/O error occurs\n");

        method1.addStatement("return this.serialize(Charset.forName(\"CP1047\"))");

        builder.addMethod(method1.build());

        MethodSpec.Builder method2 = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Charset.class, "charset")
                .returns(byte[].class)
                .addException(SerializationException.class)
                .addJavadoc("Serialize this object to COBOL binary format.\n")
                .addJavadoc("@return byte array containing the serialized data\n")
                .addJavadoc("@throws SerializationException if an I/O error occurs\n");

        method2.beginControlFlow("try")
                .addStatement("$T baos = new $T($L)", ByteArrayOutputStream.class, ByteArrayOutputStream.class, totalSize);

        for (FieldNode node : fieldTree) {
            addSerializationCode(method2, node, "this");
        }

        method2.addStatement("return baos.toByteArray()")
                .nextControlFlow("catch ($T e)", IOException.class)
                .addStatement("throw new $T(\"Serialization failed\", e)", SerializationException.class)
                .endControlFlow();

        builder.addMethod(method2.build());
    }

    /**
     * Add serialization code for a field.
     */
    private void addSerializationCode(MethodSpec.Builder method, FieldNode node, String objectRef) {
        FieldDefinition field = node.getField();
        String fieldName = fieldNameTracker.getJavaFieldName(field);

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Nested class array
            method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs())
                    .addStatement("byte[] elementBytes = $L.$L[i].serialize(charset)", objectRef, fieldName)
                    .addStatement("baos.write(elementBytes)")
                    .endControlFlow();

        } else if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
            // Flattened group
            for (FieldNode child : node.getChildren()) {
                addSerializationCode(method, child, objectRef);
            }

        } else {
            // Simple field or array
            if (field.getOccurs() > 1) {
                // Array field
                method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
                addFieldSerializationCode(method, field, objectRef + "." + fieldName + "[i]");
                method.endControlFlow();
            } else {
                // Single field
                addFieldSerializationCode(method, field, objectRef + "." + fieldName);
            }
        }
    }

    /**
     * Add serialization code for a single field value.
     */
    private void addFieldSerializationCode(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        switch (field.getType()) {
            case DISPLAY -> addDisplaySerialization(method, field, valueRef);
            case COMP, COMP_5 -> addCompSerialization(method, field, valueRef);
            case COMP_1 -> addComp1Serialization(method, valueRef);
            case COMP_2 -> addComp2Serialization(method, valueRef);
            case COMP_3, PACKED_DECIMAL -> addComp3Serialization(method, field, valueRef);
            case ZONED_DECIMAL -> addZonedDecimalSerialization(method, field, valueRef);
        }
    }

    private void addDisplaySerialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int length = calculateFieldSize(field);
        boolean isNumeric = isNumericPicture(field);
        boolean isSignedEmbedded = isSignedEmbedded(field);

        if (isSignedEmbedded && isNumeric) {
            // Signed numeric - needs formatting utility
            String tempValue = "strVal_" + System.identityHashCode(valueRef);
            method.addStatement("String $L = $T.formatSignedNumeric($L, $L, $L)",
                    tempValue, SignedNumericUtil.class, valueRef, length, field.getDecimalDigits())
                    .addStatement("baos.write($L.getBytes(charset))", tempValue);
        } else if (isNumeric && field.getDecimalDigits() > 0) {
            // Unsigned numeric with decimals - needs formatting utility
            String tempValue = "strVal_" + System.identityHashCode(valueRef);
            method.addStatement("String $L = $T.formatUnsignedNumericString($L, $L, $L)",
                    tempValue, DisplayNumericUtil.class, valueRef, length, field.getDecimalDigits())
                    .addStatement("baos.write($L.getBytes(charset))", tempValue);
        } else {
            // Simple string or numeric without decimals - inline for performance
            addInlineDisplaySerialization(method, valueRef, length, isNumeric);
        }
    }

    /**
     * Add inline DISPLAY serialization without control flow blocks for better performance.
     */
    private void addInlineDisplaySerialization(MethodSpec.Builder method, String valueRef, int length, boolean isNumeric) {
        // Generate optimized inline code that avoids String.format overhead
        String tempValue = "strVal_" + System.identityHashCode(valueRef);

        method.addStatement("String $L = $L != null ? $L.toString() : \"\"", tempValue, valueRef, valueRef);

        if (isNumeric) {
            // Numeric: pad left with zeros, truncate from right
            method.beginControlFlow("if ($L.length() < $L)", tempValue, length)
                    .addStatement("$L = \"0\".repeat($L - $L.length()) + $L", tempValue, length, tempValue, tempValue)
                    .nextControlFlow("else if ($L.length() > $L)", tempValue, length)
                    .addStatement("$L = $L.substring(0, $L)", tempValue, tempValue, length)
                    .endControlFlow();
        } else {
            // Alphanumeric: pad right with spaces, truncate from right
            method.beginControlFlow("if ($L.length() < $L)", tempValue, length)
                    .addStatement("$L = $L + \" \".repeat($L - $L.length())", tempValue, tempValue, length, tempValue)
                    .nextControlFlow("else if ($L.length() > $L)", tempValue, length)
                    .addStatement("$L = $L.substring(0, $L)", tempValue, tempValue, length)
                    .endControlFlow();
        }

        method.addStatement("baos.write($L.getBytes(charset))", tempValue);
    }

    private void addCompSerialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();

        String longVal = "longVal_" + System.identityHashCode(valueRef);
        method.addStatement("long $L = $L != null ? ((Number) $L).longValue() : 0L", longVal, valueRef, valueRef);

        if (totalDigits <= 4) {
            method.addStatement("baos.write($T.allocate(2).putShort((short) $L).array())", ByteBuffer.class, longVal);
        } else if (totalDigits <= 9) {
            method.addStatement("baos.write($T.allocate(4).putInt((int) $L).array())", ByteBuffer.class, longVal);
        } else {
            method.addStatement("baos.write($T.allocate(8).putLong($L).array())", ByteBuffer.class, longVal);
        }
    }

    private void addComp1Serialization(MethodSpec.Builder method, String valueRef) {
        method.addStatement("float floatValue = $L != null ? ((Number) $L).floatValue() : 0.0f", valueRef, valueRef)
                .addStatement("baos.write($T.allocate(4).putFloat(floatValue).array())", ByteBuffer.class);
    }

    private void addComp2Serialization(MethodSpec.Builder method, String valueRef) {
        method.addStatement("double doubleValue = $L != null ? ((Number) $L).doubleValue() : 0.0", valueRef, valueRef)
                .addStatement("baos.write($T.allocate(8).putDouble(doubleValue).array())", ByteBuffer.class);
    }

    private void addComp3Serialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        int byteLength = calculateFieldSize(field);

        method.beginControlFlow("");
        method.addStatement("$T bdValue = $L != null ? new $T($L.toString()) : $T.ZERO", BigDecimal.class, valueRef, BigDecimal.class, valueRef, BigDecimal.class);

        if (field.getDecimalDigits() > 0) {
            method.addStatement("bdValue = bdValue.multiply($T.TEN.pow($L))", BigDecimal.class, field.getDecimalDigits());
        }

        method.addStatement("$T biValue = bdValue.setScale(0, $T.HALF_UP).toBigInteger()", BigInteger.class, RoundingMode.class)
                .addStatement("String digits = String.format(\"%0$Ld\", biValue.abs().longValue())", totalDigits)
                .addStatement("byte[] packed = new byte[$L]", byteLength)
                .addStatement("int digitIndex = 0")
                .beginControlFlow("for (int i = 0; i < $L - 1; i++)", byteLength)
                .addStatement("int high = digits.charAt(digitIndex++) - '0'")
                .addStatement("int low = digits.charAt(digitIndex++) - '0'")
                .addStatement("packed[i] = (byte) ((high << 4) | low)")
                .endControlFlow()
                .addStatement("int lastDigit = ($L % 2 != 0) ? (digits.charAt(digitIndex) - '0') : 0", totalDigits)
                .addStatement("int sign = biValue.signum() < 0 ? 0x0D : 0x0C")
                .addStatement("packed[$L] = (byte) ((lastDigit << 4) | sign)", byteLength - 1)
                .addStatement("baos.write(packed)");
        method.endControlFlow();
    }

    private void addZonedDecimalSerialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int length = field.getIntegerDigits() + field.getDecimalDigits();

        method.addStatement("$T bdValue = $L != null ? new $T($L.toString()) : $T.ZERO", BigDecimal.class, valueRef, BigDecimal.class, valueRef, BigDecimal.class);

        if (field.getDecimalDigits() > 0) {
            method.addStatement("bdValue = bdValue.multiply($T.TEN.pow($L))", BigDecimal.class, field.getDecimalDigits());
        }

        method.addStatement("long longValue = bdValue.setScale(0, $T.HALF_UP).longValue()", RoundingMode.class)
                .addStatement("String digits = String.format(\"%0\" + $L + \"d\", Math.abs(longValue))", length)
                .addStatement("byte[] zoned = new byte[$L]", length)
                .beginControlFlow("for (int i = 0; i < $L; i++)", length)
                .addStatement("byte digit = (byte) (digits.charAt(i) - '0')")
                .beginControlFlow("if (i == $L - 1 && $L)", length, field.isSigned())
                .addStatement("zoned[i] = (byte) ((longValue < 0 ? 0xD0 : 0xC0) | digit)")
                .nextControlFlow("else")
                .addStatement("zoned[i] = (byte) (0xF0 | digit)")
                .endControlFlow()
                .endControlFlow();

        method.addStatement("baos.write(zoned)");
    }

    /**
     * Add deserialize method to the class.
     */
    private void addDeserializeMethods(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree) {
        MethodSpec.Builder method1 = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.bestGuess(className))
                .addParameter(byte[].class, "data")
                .addException(DeserializationException.class)
                .addJavadoc("Deserialize COBOL binary data to create an instance of this class using CP1047 charset.\n")
                .addJavadoc("@param data byte array containing the serialized data\n")
                .addJavadoc("@return deserialized instance\n")
                .addJavadoc("@throws DeserializationException if deserialization fails\n");

        method1.addStatement("return $L.deserialize(data, Charset.forName(\"CP1047\"))", className);

        builder.addMethod(method1.build());

        MethodSpec.Builder method2 = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.bestGuess(className))
                .addParameter(byte[].class, "data")
                .addParameter(Charset.class, "charset")
                .addException(DeserializationException.class)
                .addJavadoc("Deserialize COBOL binary data to create an instance of this class.\n")
                .addJavadoc("@param data byte array containing the serialized data\n")
                .addJavadoc("@return deserialized instance\n")
                .addJavadoc("@throws DeserializationException if deserialization fails\n");

        method2.beginControlFlow("try")
                .addStatement("$L instance = new $L()", className, className)
                .addStatement("int offset = 0");

        for (FieldNode node : fieldTree) {
            addDeserializationCode(method2, node, "instance");
        }

        method2.addStatement("return instance")
                .nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("throw new $T(\"Serialization failed\", e)", DeserializationException.class)
                .endControlFlow();

        builder.addMethod(method2.build());
    }

    /**
     * Add deserialization code for a field.
     */
    private void addDeserializationCode(MethodSpec.Builder method, FieldNode node, String instanceRef) {
        FieldDefinition field = node.getField();
        String fieldName = fieldNameTracker.getJavaFieldName(field);

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Nested class array - calculate element size
            int elementSize = calculateNestedClassSize(node.getChildren());

            method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs())
                    .addStatement("byte[] elementData = $T.copyOfRange(data, offset, offset + $L)", Arrays.class, elementSize)
                    .addStatement("$L.$L[i] = $L.deserialize(elementData, charset)", instanceRef, fieldName, NamingUtils.toPascalCase(field.getName()))
                    .addStatement("offset += $L", elementSize)
                    .endControlFlow();

        } else if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
            // Flattened group
            for (FieldNode child : node.getChildren()) {
                addDeserializationCode(method, child, instanceRef);
            }

        } else {
            // Simple field or array
            if (field.getOccurs() > 1) {
                // Array field
                int elementSize = calculateFieldSize(field);
                method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
                addFieldDeserializationCode(method, field, instanceRef + "." + fieldName + "[i]", "data", "offset");
                method.addStatement("offset += $L", elementSize)
                        .endControlFlow();
            } else {
                // Single field
                int fieldSize = calculateFieldSize(field);
                addFieldDeserializationCode(method, field, instanceRef + "." + fieldName, "data", "offset");
                method.addStatement("offset += $L", fieldSize);
            }
        }
    }

    /**
     * Add deserialization code for a single field value.
     */
    private void addFieldDeserializationCode(MethodSpec.Builder method, FieldDefinition field, String targetRef, String dataRef, String offsetRef) {
        switch (field.getType()) {
            case DISPLAY -> addDisplayDeserialization(method, field, targetRef, dataRef, offsetRef);
            case COMP, COMP_5 -> addCompDeserialization(method, field, targetRef, dataRef, offsetRef);
            case COMP_1 -> addComp1Deserialization(method, targetRef, dataRef, offsetRef);
            case COMP_2 -> addComp2Deserialization(method, targetRef, dataRef, offsetRef);
            case COMP_3, PACKED_DECIMAL -> addComp3Deserialization(method, field, targetRef, dataRef, offsetRef);
            case ZONED_DECIMAL -> addZonedDecimalDeserialization(method, field, targetRef, dataRef, offsetRef);
        }
    }

    private void addDisplayDeserialization(MethodSpec.Builder method, FieldDefinition field, String targetRef, String dataRef, String offsetRef) {
        int length = calculateFieldSize(field);
        TypeName javaType = getBaseJavaType(field);

        if (javaType.equals(ClassName.get(String.class))) {
            // Simple string assignment - no block needed
            method.addStatement("$L = new String($L, $L, $L, charset).trim()", targetRef, dataRef, offsetRef, length);
        } else {
            // Numeric parsing needs block for local variables
            if (isSignedEmbedded(field)) {
                deserializeSignedEmbedded(method, javaType, targetRef, dataRef, offsetRef, field);
            } else {
                deserializeUnsignedNumeric(method, javaType, targetRef, dataRef, offsetRef, field);
            }
        }
    }

    private void deserializeSignedEmbedded(MethodSpec.Builder method, TypeName javaType, String targetRef, String dataRef, String offsetRef, FieldDefinition field) {
        int length = calculateFieldSize(field);

        if (javaType.equals(ClassName.get(Integer.class))) {
            method.addStatement("$L = $T.parseSignedInt($L, $L, $L, charset)", targetRef, SignedNumericUtil.class, dataRef, offsetRef, length);
        } else if (javaType.equals(ClassName.get(Long.class))) {
            method.addStatement("$L = $T.parseSignedLong($L, $L, $L, charset)", targetRef, SignedNumericUtil.class, dataRef, offsetRef, length);
        } else if (javaType.equals(ClassName.get(BigDecimal.class))) {
            method.addStatement("$L = $T.parseSignedBigDecimal($L, $L, $L, charset, $L)", targetRef, SignedNumericUtil.class, dataRef, offsetRef, length, field.getDecimalDigits());
        } else if (javaType.equals(ClassName.get(BigInteger.class))) {
            method.addStatement("$L = $T.parseSignedBigDecimal($L, $L, $L, charset, 0).toBigInteger()", targetRef, SignedNumericUtil.class, dataRef, offsetRef, length);
        }
    }

    private void deserializeUnsignedNumeric(MethodSpec.Builder method, TypeName javaType, String targetRef, String dataRef, String offsetRef, FieldDefinition field) {
        int length = calculateFieldSize(field);

        if (isNumericWithDecimals(javaType, field)) {
            if (javaType.equals(ClassName.get(Integer.class))) {
                method.addStatement("$L = $T.parseUnsignedInt($L, $L, $L, charset, $L)", targetRef, DisplayNumericUtil.class, dataRef, offsetRef, length, field.getDecimalDigits());
            } else if (javaType.equals(ClassName.get(Long.class))) {
                method.addStatement("$L = $T.parseUnsignedLong($L, $L, $L, charset, $L)", targetRef, DisplayNumericUtil.class, dataRef, offsetRef, length, field.getDecimalDigits());
            } else if (javaType.equals(ClassName.get(BigDecimal.class))) {
                method.addStatement("$L = $T.parseUnsignedWithImpliedDecimal($L, $L, $L, charset, $L)", targetRef, DisplayNumericUtil.class, dataRef, offsetRef, length, field.getDecimalDigits());
            }
        } else {
            deserializeSimpleNumeric(method, javaType, targetRef, dataRef, offsetRef, length);
        }
    }

    private void deserializeSimpleNumeric(MethodSpec.Builder method, TypeName javaType, String targetRef, String dataRef, String offsetRef, int length) {
        String tempValue = "strVal_" + System.identityHashCode(targetRef);
        method.addStatement("String $L = new String($L, $L, $L, charset).trim()", tempValue, dataRef, offsetRef, length);

        if (javaType.equals(ClassName.get(Integer.class))) {
            method.addStatement("$L = $L.isEmpty() ? 0 : Integer.parseInt($L)", targetRef, tempValue, tempValue);
        } else if (javaType.equals(ClassName.get(Long.class))) {
            method.addStatement("$L = $L.isEmpty() ? 0L : Long.parseLong($L)", targetRef, tempValue, tempValue);
        } else if (javaType.equals(ClassName.get(BigDecimal.class))) {
            method.addStatement("$L = $L.isEmpty() ? $T.ZERO : new $T($L)", targetRef, tempValue, BigDecimal.class, BigDecimal.class, tempValue);
        } else if (javaType.equals(ClassName.get(BigInteger.class))) {
            method.addStatement("$L = $L.isEmpty() ? $T.ZERO : new $T($L)", targetRef, tempValue, BigInteger.class, BigInteger.class, tempValue);
        }
    }

    private void addCompDeserialization(MethodSpec.Builder method, FieldDefinition field, String targetRef, String dataRef, String offsetRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        int size = calculateCompSize(totalDigits);

        TypeName javaType = getBaseJavaType(field);
        if (totalDigits <= 4) {
            if (javaType.equals(ClassName.get(Short.class))) {
                method.addStatement("$L = $T.wrap($L, $L, $L).getShort()", targetRef, ByteBuffer.class, dataRef, offsetRef, size);
            } else {
                method.addStatement("$L = (int) $T.wrap($L, $L, $L).getShort()", targetRef, ByteBuffer.class, dataRef, offsetRef, size);
            }
        } else if (totalDigits <= 9) {
            method.addStatement("$L = $T.wrap($L, $L, $L).getInt()", targetRef, ByteBuffer.class, dataRef, offsetRef, size);
        } else {
            if (javaType.equals(ClassName.get(Long.class))) {
                method.addStatement("$L = $T.wrap($L, $L, $L).getLong()", targetRef, ByteBuffer.class, dataRef, offsetRef, size);
            } else {
                method.addStatement("$L = $T.valueOf($T.wrap($L, $L, $L).getLong())", targetRef, BigInteger.class, ByteBuffer.class, dataRef, offsetRef, size);
            }
        }
    }

    private void addComp1Deserialization(MethodSpec.Builder method, String targetRef, String dataRef, String offsetRef) {
        method.addStatement("$L = $T.wrap($L, $L, 4).getFloat()", targetRef, ByteBuffer.class, dataRef, offsetRef);
    }

    private void addComp2Deserialization(MethodSpec.Builder method, String targetRef, String dataRef, String offsetRef) {
        method.addStatement("$L = $T.wrap($L, $L, 8).getDouble()", targetRef, ByteBuffer.class, dataRef, offsetRef);
    }

    private void addComp3Deserialization(MethodSpec.Builder method, FieldDefinition field,
                                         String targetRef, String dataRef, String offsetRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        int length = calculateFieldSize(field);

        method.beginControlFlow("");
        method.addStatement("$T digits = new $T($L)", StringBuilder.class, StringBuilder.class, totalDigits)
                .beginControlFlow("for (int i = 0; i < $L - 1; i++)", length)
                .addStatement("int highNibble = ($L[$L + i] >> 4) & 0x0F", dataRef, offsetRef)
                .addStatement("int lowNibble = $L[$L + i] & 0x0F", dataRef, offsetRef)
                .addStatement("digits.append(highNibble)")
                .addStatement("digits.append(lowNibble)")
                .endControlFlow()
                .addStatement("int lastDigit = ($L[$L + $L - 1] >> 4) & 0x0F", dataRef, offsetRef, length)
                .addStatement("int sign = $L[$L + $L - 1] & 0x0F", dataRef, offsetRef, length)
                .beginControlFlow("if ($L % 2 != 0)", totalDigits)
                .addStatement("digits.append(lastDigit)")
                .endControlFlow()
                .addStatement("boolean isNegative = (sign == 0x0D || sign == 0x0B)")
                .addStatement("$T biValue = new $T(digits.toString())", BigInteger.class, BigInteger.class)
                .beginControlFlow("if (isNegative)")
                .addStatement("biValue = biValue.negate()")
                .endControlFlow();

        TypeName javaType = getBaseJavaType(field);
        if (field.getDecimalDigits() > 0) {
            method.addStatement("$T bdValue = new $T(biValue)", BigDecimal.class, BigDecimal.class)
                    .addStatement("$L = bdValue.divide($T.TEN.pow($L), $L, $T.HALF_UP).stripTrailingZeros()",
                            targetRef, BigDecimal.class, field.getDecimalDigits(),
                            field.getDecimalDigits(), RoundingMode.class);
        } else {
            if (javaType.equals(ClassName.get(Integer.class))) {
                method.addStatement("$L = biValue.intValue()", targetRef);
            } else if (javaType.equals(ClassName.get(Long.class))) {
                method.addStatement("$L = biValue.longValue()", targetRef);
            } else {
                method.addStatement("$L = biValue", targetRef);
            }
        }
        method.endControlFlow();
    }

    private void addZonedDecimalDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                                String targetRef, String dataRef, String offsetRef) {
        int length = field.getIntegerDigits() + field.getDecimalDigits();

        method.addStatement("$T digits = new $T($L)", StringBuilder.class, StringBuilder.class, length)
                .addStatement("boolean isNegative = false")
                .beginControlFlow("for (int i = 0; i < $L; i++)", length)
                .addStatement("int digit = $L[$L + i] & 0x0F", dataRef, offsetRef)
                .addStatement("digits.append(digit)")
                .beginControlFlow("if (i == $L - 1)", length)
                .addStatement("int zone = $L[$L + i] & 0xF0", dataRef, offsetRef)
                .addStatement("isNegative = (zone == 0xD0 || zone == 0xB0)")
                .endControlFlow()
                .endControlFlow()
                .addStatement("$T biValue = new $T(digits.toString())", BigInteger.class, BigInteger.class)
                .beginControlFlow("if (isNegative)")
                .addStatement("biValue = biValue.negate()")
                .endControlFlow();

        if (field.getDecimalDigits() > 0) {
            method.addStatement("$T bdValue = new $T(biValue)", BigDecimal.class, BigDecimal.class);
            method.addStatement("$L = bdValue.divide($T.TEN.pow($L), $L, $T.HALF_UP).stripTrailingZeros()",
                    targetRef, BigDecimal.class, field.getDecimalDigits(),
                    field.getDecimalDigits(), RoundingMode.class);
        } else {
            method.addStatement("$L = new $T(biValue)", targetRef, BigDecimal.class);
        }
    }

    /**
     * Calculate the size in bytes of a field.
     */
    private int calculateFieldSize(FieldDefinition field) {
        return fieldSizeCache.computeIfAbsent(field, f ->
                switch (f.getType()) {
                    case DISPLAY -> calculateDisplayLength(f);
                    case COMP, COMP_5 -> calculateCompSize(f.getIntegerDigits() + f.getDecimalDigits());
                    case COMP_1 -> 4;
                    case COMP_2 -> 8;
                    case COMP_3, PACKED_DECIMAL ->
                            calculatePackedDecimalSize(f.getIntegerDigits() + f.getDecimalDigits());
                    case ZONED_DECIMAL -> f.getIntegerDigits() + f.getDecimalDigits();
                });
    }

    /**
     * Calculate the display field length.
     */
    private int calculateDisplayLength(FieldDefinition field) {
        int length = field.getIntegerDigits() + field.getDecimalDigits();
        if (length == 0 && field.getPicture() != null) {
            // Try to calculate from picture
            length = field.getPicture().replaceAll("[^X9]", "").length();
        }
        return length;
    }

    /**
     * Calculate the size in bytes needed for a COMP/COMP-5 field.
     */
    private int calculateCompSize(int totalDigits) {
        if (totalDigits <= 4) return 2;
        if (totalDigits <= 9) return 4;
        return 8;
    }

    /**
     * Calculate the size in bytes needed for a COMP-3/PACKED-DECIMAL field.
     */
    private int calculatePackedDecimalSize(int totalDigits) {
        return (totalDigits / 2) + 1;
    }

    /**
     * Calculate the total size in bytes of a nested class.
     */
    private int calculateNestedClassSize(List<FieldNode> children) {
        int totalSize = 0;
        for (FieldNode child : children) {
            FieldDefinition field = child.getField();

            if (field.isGroup() && field.getOccurs() > 1 && !child.getChildren().isEmpty()) {
                int elementSize = calculateNestedClassSize(child.getChildren());
                totalSize += elementSize * field.getOccurs();
            } else if (field.isGroup() && field.getOccurs() == 1 && !child.getChildren().isEmpty()) {
                totalSize += calculateNestedClassSize(child.getChildren());
            } else {
                int fieldSize = calculateFieldSize(field);
                totalSize += fieldSize * field.getOccurs();
            }
        }
        return totalSize;
    }

    private boolean isSignedEmbedded(FieldDefinition field) {
        return field.isSigned() && !field.isSignSeparate();
    }

    /**
     * Check if field is numeric with decimals.
     */
    private boolean isNumericWithDecimals(TypeName javaType, FieldDefinition field) {
        return (javaType.equals(ClassName.get(BigDecimal.class)) ||
                javaType.equals(ClassName.get(Integer.class)) ||
                javaType.equals(ClassName.get(Long.class))) &&
                field.getDecimalDigits() > 0;
    }

    /**
     * Check if picture represents numeric.
     */
    private boolean isNumericPicture(FieldDefinition field) {
        return Optional.ofNullable(field.getPicture())
                .map(pic -> pic.startsWith("9") || pic.startsWith("S9"))
                .orElse(false);
    }
}

