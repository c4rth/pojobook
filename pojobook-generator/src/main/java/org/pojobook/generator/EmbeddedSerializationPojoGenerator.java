package org.pojobook.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.deserializer.CobolFieldDeserializer;
import org.pojobook.exception.DeserializationException;
import org.pojobook.exception.SerializationException;
import org.pojobook.parser.CopybookDefinition;
import org.pojobook.parser.FieldDefinition;
import org.pojobook.serializer.CobolFieldSerializer;

import javax.lang.model.element.Modifier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
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
                this::addStaticVariables,
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
     * Add static variables to the class.
     */
    private void addStaticVariables(BuilderContext context) {
        // Add any static variables if needed in future
        context.builder
                .addField(FieldSpec.builder(Charset.class, "CHARSET_CP1047", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("Charset.forName(\"CP1047\")")
                        .build());
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
                .addJavadoc("@throws SerializationException if an I/O error occurs\n");

        method1.addStatement("return this.serialize(CHARSET_CP1047)");

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
        boolean signed = field.isSigned();
        boolean signSeparate = field.isSignSeparate();
        int decimalDigits = field.getDecimalDigits();
        boolean isLeadingSign = field.getSignPosition() != null && !field.getSignPosition().isEmpty() && "LEADING".equalsIgnoreCase(field.getSignPosition());

        // Call the specific serialization method based on field characteristics
        if (signed && signSeparate) {
            method.addStatement("baos.write($T.serializeDisplayWithSeparateSign((Number) $L, $L, $L, $L, charset))",
                    CobolFieldSerializer.class, valueRef, length, decimalDigits, isLeadingSign);
        } else if (signed && !signSeparate && isNumeric) {
            method.addStatement("baos.write($T.serializeDisplayWithEmbeddedSign((Number) $L, $L, $L, charset))",
                    CobolFieldSerializer.class, valueRef, length, decimalDigits);
        } else if (!signed && decimalDigits > 0 && isNumeric) {
            method.addStatement("baos.write($T.serializeDisplayWithImpliedDecimal((Number) $L, $L, $L, charset))",
                    CobolFieldSerializer.class, valueRef, length, decimalDigits);
        } else {
            method.addStatement("baos.write($T.serializeDisplayString($L, $L, $L, charset))",
                    CobolFieldSerializer.class, valueRef, length, isNumeric);
        }
    }

    private void addCompSerialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        method.addStatement("baos.write($T.serializeComp($L, $L))",
                CobolFieldSerializer.class, valueRef, totalDigits);
    }

    private void addComp1Serialization(MethodSpec.Builder method, String valueRef) {
        method.addStatement("baos.write($T.serializeComp1($L))",
                CobolFieldSerializer.class, valueRef);
    }

    private void addComp2Serialization(MethodSpec.Builder method, String valueRef) {
        method.addStatement("baos.write($T.serializeComp2($L))",
                CobolFieldSerializer.class, valueRef);
    }

    private void addComp3Serialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        method.addStatement("baos.write($T.serializeComp3($L, $L, $L))",
                CobolFieldSerializer.class, valueRef, totalDigits, field.getDecimalDigits());
    }

    private void addZonedDecimalSerialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        method.addStatement("baos.write($T.serializeZonedDecimal($L, $L, $L, $L))",
                CobolFieldSerializer.class, valueRef, totalDigits, field.getDecimalDigits(), field.isSigned());
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

        method1.addStatement("return $L.deserialize(data, CHARSET_CP1047)", className);

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
            // Simple string assignment using helper
            method.addStatement("$L = $T.deserializeDisplayString($L, $L, $L, charset)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length);
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
            method.addStatement("$L = $T.deserializeDisplaySignedInteger($L, $L, $L, charset)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length);
        } else if (javaType.equals(ClassName.get(Long.class))) {
            method.addStatement("$L = $T.deserializeDisplaySignedLong($L, $L, $L, charset)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length);
        } else if (javaType.equals(ClassName.get(BigDecimal.class))) {
            method.addStatement("$L = $T.deserializeDisplaySignedBigDecimal($L, $L, $L, charset, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length, field.getDecimalDigits());
        } else if (javaType.equals(ClassName.get(BigInteger.class))) {
            method.addStatement("$L = $T.deserializeDisplaySignedBigInteger($L, $L, $L, charset)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length);
        }
    }

    private void deserializeUnsignedNumeric(MethodSpec.Builder method, TypeName javaType, String targetRef, String dataRef, String offsetRef, FieldDefinition field) {
        int length = calculateFieldSize(field);

        if (isNumericWithDecimals(javaType, field)) {
            if (javaType.equals(ClassName.get(Integer.class))) {
                method.addStatement("$L = $T.deserializeDisplayIntegerWithDecimal($L, $L, $L, charset, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length, field.getDecimalDigits());
            } else if (javaType.equals(ClassName.get(Long.class))) {
                method.addStatement("$L = $T.deserializeDisplayLongWithDecimal($L, $L, $L, charset, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length, field.getDecimalDigits());
            } else if (javaType.equals(ClassName.get(BigDecimal.class))) {
                method.addStatement("$L = $T.deserializeDisplayBigDecimalWithDecimal($L, $L, $L, charset, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length, field.getDecimalDigits());
            }
        } else {
            deserializeSimpleNumeric(method, javaType, targetRef, dataRef, offsetRef, length);
        }
    }

    private void deserializeSimpleNumeric(MethodSpec.Builder method, TypeName javaType, String targetRef, String dataRef, String offsetRef, int length) {
        String tempValue = "strVal_" + System.identityHashCode(targetRef);
        method.addStatement("String $L = $T.deserializeDisplayString($L, $L, $L, charset)",
                tempValue, CobolFieldDeserializer.class, dataRef, offsetRef, length);

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
        if (javaType.equals(ClassName.get(Short.class))) {
            method.addStatement("$L = $T.deserializeCompShort($L, $L, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, size);
        } else if (javaType.equals(ClassName.get(Integer.class))) {
            method.addStatement("$L = $T.deserializeCompInteger($L, $L, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, size);
        } else if (javaType.equals(ClassName.get(Long.class))) {
            method.addStatement("$L = $T.deserializeCompLong($L, $L, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, size);
        } else if (javaType.equals(ClassName.get(BigInteger.class))) {
            method.addStatement("$L = $T.deserializeCompBigInteger($L, $L, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, size);
        }
    }

    private void addComp1Deserialization(MethodSpec.Builder method, String targetRef, String dataRef, String offsetRef) {
        method.addStatement("$L = $T.deserializeComp1($L, $L)",
                targetRef, CobolFieldDeserializer.class, dataRef, offsetRef);
    }

    private void addComp2Deserialization(MethodSpec.Builder method, String targetRef, String dataRef, String offsetRef) {
        method.addStatement("$L = $T.deserializeComp2($L, $L)",
                targetRef, CobolFieldDeserializer.class, dataRef, offsetRef);
    }

    private void addComp3Deserialization(MethodSpec.Builder method, FieldDefinition field,
                                         String targetRef, String dataRef, String offsetRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        int length = calculateFieldSize(field);

        TypeName javaType = getBaseJavaType(field);
        if (field.getDecimalDigits() > 0) {
            method.addStatement("$L = $T.deserializeComp3BigDecimal($L, $L, $L, $L, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length,
                    totalDigits, field.getDecimalDigits());
        } else {
            if (javaType.equals(ClassName.get(Integer.class))) {
                method.addStatement("$L = $T.deserializeComp3Integer($L, $L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length, totalDigits);
            } else if (javaType.equals(ClassName.get(Long.class))) {
                method.addStatement("$L = $T.deserializeComp3Long($L, $L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length, totalDigits);
            } else {
                method.addStatement("$L = $T.deserializeComp3BigInteger($L, $L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length, totalDigits);
            }
        }
    }

    private void addZonedDecimalDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                                String targetRef, String dataRef, String offsetRef) {
        int length = field.getIntegerDigits() + field.getDecimalDigits();

        if (field.getDecimalDigits() > 0) {
            method.addStatement("$L = $T.deserializeZonedDecimalBigDecimal($L, $L, $L, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length, field.getDecimalDigits());
        } else {
            method.addStatement("$L = $T.deserializeZonedDecimalBigDecimalNoDecimals($L, $L, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetRef, length);
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

