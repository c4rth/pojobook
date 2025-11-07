package org.c4rth.pojobook.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.c4rth.pojobook.parser.CopybookDefinition;
import org.c4rth.pojobook.parser.FieldDefinition;

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

/**
 * Generates POJO classes with embedded serialization and deserialization methods.
 * Unlike the annotation-based PojoGenerator, this generator creates POJOs that
 * don't require reflection at runtime - all serialization logic is embedded in the class.
 */
public class EmbeddedSerializationPojoGenerator extends AbstractPojoGenerator {

    private String packageName = "org.c4rth.generated";

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
        // Reset field name tracking to ensure uniqueness within this class
        resetFieldNameTracking();

        String recordName = definition.getRecordName() != null ? definition.getRecordName() : "CobolRecord";
        String className = (recordName.contains("-") || recordName.contains("_")) ?
                toPascalCase(recordName) : recordName;

        List<FieldDefinition> fields = definition.getFields();
        List<FieldNode> fieldTree = FieldNode.buildTree(fields);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC);

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

        // Add serialization methods
        addSerializeMethods(classBuilder, fieldTree);
        addDeserializeMethods(classBuilder, className, fieldTree);

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

        // Generate nested classes for all descendants that need them
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

        // Add serialization methods for nested class
        addSerializeMethods(builder, node.getChildren());
        addDeserializeMethods(builder, className, node.getChildren());

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
            String fieldName = toUniqueFieldName(field);
            String className = toPascalCase(field.getName());
            TypeName fieldType = ArrayTypeName.of(ClassName.bestGuess(className));

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE);
            fieldBuilder.addJavadoc(buildFieldComment(field));
            builder.addField(fieldBuilder.build());

        } else if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
            for (FieldNode child : node.getChildren()) {
                addFieldFromNode(builder, child);
            }

        } else {
            String fieldName = toUniqueFieldName(field);
            TypeName fieldType = getJavaType(field);

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE);
            fieldBuilder.addJavadoc(buildFieldComment(field));
            builder.addField(fieldBuilder.build());
        }
    }

    /**
     * Build a comment describing the COBOL field definition.
     */
    private String buildFieldComment(FieldDefinition field) {
        StringBuilder comment = new StringBuilder();

        // COBOL field name
        comment.append("COBOL: ").append(field.getName());

        // Level
        comment.append(" - Level: ").append(String.format("%02d", field.getLevel()));

        // Picture
        if (field.getPicture() != null && !field.getPicture().isEmpty()) {
            comment.append(" - PIC ").append(field.getPicture());
        }

        // Type
        comment.append(" - Type: ").append(field.getType());

        // Signed
        if (field.isSigned()) {
            comment.append(" - SIGNED");
            if (field.getSignPosition() != null && !field.getSignPosition().isEmpty()) {
                comment.append(" ").append(field.getSignPosition());
            }
            if (field.isSignSeparate()) {
                comment.append(" SEPARATE");
            }
        }

        // Occurs
        if (field.getOccurs() > 1) {
            comment.append(" - OCCURS ").append(field.getOccurs());
            if (field.getDependingOn() != null && !field.getDependingOn().isEmpty()) {
                comment.append(" DEPENDING ON ").append(field.getDependingOn());
            }
        }

        // Redefines
        if (field.getRedefines() != null && !field.getRedefines().isEmpty()) {
            comment.append(" - REDEFINES ").append(field.getRedefines());
        }

        // Value
        if (field.getValue() != null && !field.getValue().isEmpty()) {
            comment.append(" - VALUE ").append(field.getValue());
        }

        comment.append("\n");
        return comment.toString();
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

    /**
     * Add serialize method to the class.
     */
    private void addSerializeMethods(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        MethodSpec.Builder method1 = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(byte[].class)
                .addException(IOException.class)
                .addJavadoc("Serialize this object to COBOL binary format using CP1047 charset.\n")
                .addJavadoc("@return byte array containing the serialized data\n")
                .addJavadoc("@throws IOException if an I/O error occurs\n");

        method1.addStatement("return this.serialize(Charset.forName(\"CP1047\"))");

        builder.addMethod(method1.build());

        MethodSpec.Builder method2 = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Charset.class, "charset")
                .returns(byte[].class)
                .addException(IOException.class)
                .addJavadoc("Serialize this object to COBOL binary format.\n")
                .addJavadoc("@return byte array containing the serialized data\n")
                .addJavadoc("@throws IOException if an I/O error occurs\n");

        method2.addStatement("$T baos = new $T()", ByteArrayOutputStream.class, ByteArrayOutputStream.class);

        for (FieldNode node : fieldTree) {
            addSerializationCode(method2, node, "this");
        }

        method2.addStatement("return baos.toByteArray()");

        builder.addMethod(method2.build());
    }

    /**
     * Add serialization code for a field.
     */
    private void addSerializationCode(MethodSpec.Builder method, FieldNode node, String objectRef) {
        FieldDefinition field = node.getField();
        String fieldName = getJavaFieldName(field);

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Nested class array
            method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
            method.addStatement("byte[] elementBytes = $L.$L[i].serialize(charset)", objectRef, fieldName);
            method.addStatement("baos.write(elementBytes)");
            method.endControlFlow();

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

        System.out.println("Adding DISPLAY serialization for field: " + field.getName() + " - PIC " + field.getPicture() + " - Length calc: " + (field.getIntegerDigits() + field.getDecimalDigits()));

        int length = field.getIntegerDigits() + field.getDecimalDigits();
        if (length == 0 && field.getPicture() != null) {
            // Try to calculate from picture
            length = field.getPicture().replaceAll("[^X9]", "").length();
        }

        // Use block scope to avoid variable redeclaration
        method.beginControlFlow("");
        method.addStatement("String strValue = $L != null ? $L.toString() : \"\"", valueRef, valueRef);
        method.beginControlFlow("if (strValue.length() < $L)", length);
        if (field.getPicture() != null && field.getPicture().startsWith("9")) {
            method.addStatement("strValue = String.format(\"%1$$\" + $L + \"s\", strValue).replace(' ', '0')", length);
        } else {
            method.addStatement("strValue = String.format(\"%-\" + $L + \"s\", strValue)", length);
        }
        method.nextControlFlow("else if (strValue.length() > $L)", length);
        method.addStatement("strValue = strValue.substring(0, $L)", length);
        method.endControlFlow();

        method.addStatement("baos.write(strValue.getBytes(charset))");
        method.endControlFlow(); // End block scope
    }

    private void addCompSerialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();

        // Use block scope to avoid variable redeclaration
        method.beginControlFlow("");
        method.addStatement("long longValue = $L != null ? ((Number) $L).longValue() : 0L", valueRef, valueRef);

        if (totalDigits <= 4) {
            method.addStatement("$T buffer = $T.allocate(2)", ByteBuffer.class, ByteBuffer.class);
            method.addStatement("buffer.putShort((short) longValue)");
        } else if (totalDigits <= 9) {
            method.addStatement("$T buffer = $T.allocate(4)", ByteBuffer.class, ByteBuffer.class);
            method.addStatement("buffer.putInt((int) longValue)");
        } else {
            method.addStatement("$T buffer = $T.allocate(8)", ByteBuffer.class, ByteBuffer.class);
            method.addStatement("buffer.putLong(longValue)");
        }

        method.addStatement("baos.write(buffer.array())");
        method.endControlFlow(); // End block scope
    }

    private void addComp1Serialization(MethodSpec.Builder method, String valueRef) {
        method.addStatement("float floatValue = $L != null ? ((Number) $L).floatValue() : 0.0f", valueRef, valueRef);
        method.addStatement("$T buffer = $T.allocate(4)", ByteBuffer.class, ByteBuffer.class);
        method.addStatement("buffer.putFloat(floatValue)");
        method.addStatement("baos.write(buffer.array())");
    }

    private void addComp2Serialization(MethodSpec.Builder method, String valueRef) {
        method.addStatement("double doubleValue = $L != null ? ((Number) $L).doubleValue() : 0.0", valueRef, valueRef);
        method.addStatement("$T buffer = $T.allocate(8)", ByteBuffer.class, ByteBuffer.class);
        method.addStatement("buffer.putDouble(doubleValue)");
        method.addStatement("baos.write(buffer.array())");
    }

    private void addComp3Serialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        int byteLength = (totalDigits / 2) + 1;

        // Use block scope to avoid variable redeclaration
        method.beginControlFlow("");
        method.addStatement("$T bdValue = $L != null ? new $T($L.toString()) : $T.ZERO",
                BigDecimal.class, valueRef, BigDecimal.class, valueRef, BigDecimal.class);

        if (field.getDecimalDigits() > 0) {
            method.addStatement("bdValue = bdValue.multiply($T.TEN.pow($L))",
                    BigDecimal.class, field.getDecimalDigits());
        }

        method.addStatement("$T biValue = bdValue.setScale(0, $T.HALF_UP).toBigInteger()",
                BigInteger.class, RoundingMode.class);
        method.addStatement("String digits = biValue.abs().toString()");
        method.addStatement("digits = String.format(\"%0\" + $L + \"d\", Long.parseLong(digits.isEmpty() ? \"0\" : digits))", totalDigits);

        method.addStatement("byte[] packed = new byte[$L]", byteLength);

        // Pack pairs of digits for all bytes except the last
        method.addStatement("int digitIndex = 0");
        method.beginControlFlow("for (int i = 0; i < $L - 1; i++)", byteLength);
        method.addStatement("int high = digits.charAt(digitIndex++) - '0'");
        method.addStatement("int low = digits.charAt(digitIndex++) - '0'");
        method.addStatement("packed[i] = (byte) ((high << 4) | low)");
        method.endControlFlow();

        // Last byte: last digit (if odd) + sign
        method.addStatement("int lastDigit = ($L % 2 != 0) ? (digits.charAt(digitIndex) - '0') : 0", totalDigits);
        method.addStatement("int sign = biValue.signum() < 0 ? 0x0D : 0x0C");
        method.addStatement("packed[$L] = (byte) ((lastDigit << 4) | sign)", byteLength - 1);
        method.addStatement("baos.write(packed)");
        method.endControlFlow(); // End block scope
    }

    private void addZonedDecimalSerialization(MethodSpec.Builder method, FieldDefinition field, String valueRef) {
        int length = field.getIntegerDigits() + field.getDecimalDigits();

        method.addStatement("$T bdValue = $L != null ? new $T($L.toString()) : $T.ZERO",
                BigDecimal.class, valueRef, BigDecimal.class, valueRef, BigDecimal.class);

        if (field.getDecimalDigits() > 0) {
            method.addStatement("bdValue = bdValue.multiply($T.TEN.pow($L))",
                    BigDecimal.class, field.getDecimalDigits());
        }

        method.addStatement("long longValue = bdValue.setScale(0, $T.HALF_UP).longValue()", RoundingMode.class);
        method.addStatement("String digits = String.format(\"%0\" + $L + \"d\", Math.abs(longValue))", length);
        method.addStatement("byte[] zoned = new byte[$L]", length);

        method.beginControlFlow("for (int i = 0; i < $L; i++)", length);
        method.addStatement("byte digit = (byte) (digits.charAt(i) - '0')");
        method.beginControlFlow("if (i == $L - 1 && $L)", length, field.isSigned());
        method.addStatement("zoned[i] = (byte) ((longValue < 0 ? 0xD0 : 0xC0) | digit)");
        method.nextControlFlow("else");
        method.addStatement("zoned[i] = (byte) (0xF0 | digit)");
        method.endControlFlow();
        method.endControlFlow();

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
                .addException(Exception.class)
                .addJavadoc("Deserialize COBOL binary data to create an instance of this class using CP1047 charset.\n")
                .addJavadoc("@param data byte array containing the serialized data\n")
                .addJavadoc("@return deserialized instance\n")
                .addJavadoc("@throws Exception if deserialization fails\n");

        method1.addStatement("return $L.deserialize(data, Charset.forName(\"CP1047\"))", className);

        builder.addMethod(method1.build());

        MethodSpec.Builder method2 = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.bestGuess(className))
                .addParameter(byte[].class, "data")
                .addParameter(Charset.class, "charset")
                .addException(Exception.class)
                .addJavadoc("Deserialize COBOL binary data to create an instance of this class.\n")
                .addJavadoc("@param data byte array containing the serialized data\n")
                .addJavadoc("@return deserialized instance\n")
                .addJavadoc("@throws Exception if deserialization fails\n");

        method2.addStatement("$L instance = new $L()", className, className);
        method2.addStatement("int offset = 0");

        for (FieldNode node : fieldTree) {
            addDeserializationCode(method2, node, "instance");
        }

        method2.addStatement("return instance");

        builder.addMethod(method2.build());
    }

    /**
     * Add deserialization code for a field.
     */
    private void addDeserializationCode(MethodSpec.Builder method, FieldNode node, String instanceRef) {
        FieldDefinition field = node.getField();
        String fieldName = getJavaFieldName(field);

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Nested class array - calculate element size
            int elementSize = calculateNestedClassSize(node.getChildren());

            method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
            method.addStatement("byte[] elementData = $T.copyOfRange(data, offset, offset + $L)",
                    Arrays.class, elementSize);
            method.addStatement("$L.$L[i] = $L.deserialize(elementData, charset)",
                    instanceRef, fieldName, toPascalCase(field.getName()));
            method.addStatement("offset += $L", elementSize);
            method.endControlFlow();

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
                method.addStatement("byte[] elementData = $T.copyOfRange(data, offset, offset + $L)",
                        Arrays.class, elementSize);
                addFieldDeserializationCode(method, field, instanceRef + "." + fieldName + "[i]", "elementData", 0);
                method.addStatement("offset += $L", elementSize);
                method.endControlFlow();
            } else {
                // Single field - use block scope to avoid variable redeclaration
                method.beginControlFlow("");
                int fieldSize = calculateFieldSize(field);
                method.addStatement("byte[] fieldData = $T.copyOfRange(data, offset, offset + $L)",
                        Arrays.class, fieldSize);
                addFieldDeserializationCode(method, field, instanceRef + "." + fieldName, "fieldData", 0);
                method.addStatement("offset += $L", fieldSize);
                method.endControlFlow();
            }
        }
    }

    /**
     * Add deserialization code for a single field value.
     */
    private void addFieldDeserializationCode(MethodSpec.Builder method, FieldDefinition field,
                                             String targetRef, String dataRef, int offset) {
        switch (field.getType()) {
            case DISPLAY -> addDisplayDeserialization(method, field, targetRef, dataRef);
            case COMP, COMP_5 -> addCompDeserialization(method, field, targetRef, dataRef);
            case COMP_1 -> addComp1Deserialization(method, targetRef, dataRef);
            case COMP_2 -> addComp2Deserialization(method, targetRef, dataRef);
            case COMP_3, PACKED_DECIMAL -> addComp3Deserialization(method, field, targetRef, dataRef);
            case ZONED_DECIMAL -> addZonedDecimalDeserialization(method, field, targetRef, dataRef);
        }
    }

    private void addDisplayDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                           String targetRef, String dataRef) {

        // Use block scope to avoid variable redeclaration
        method.beginControlFlow("");
        TypeName javaType = getBaseJavaType(field);
        if (javaType.equals(ClassName.get(String.class))) {
            method.addStatement("$L = new String($L, charset).trim()", targetRef, dataRef);
        } else if (javaType.equals(ClassName.get(Integer.class))) {
            method.addStatement("String strValue = new String($L, charset).trim()", dataRef);
            method.addStatement("$L = strValue.isEmpty() ? 0 : Integer.parseInt(strValue)", targetRef);
        } else if (javaType.equals(ClassName.get(Long.class))) {
            method.addStatement("String strValue = new String($L, charset).trim()", dataRef);
            method.addStatement("$L = strValue.isEmpty() ? 0L : Long.parseLong(strValue)", targetRef);
        } else if (javaType.equals(ClassName.get(BigDecimal.class))) {
            method.addStatement("String strValue = new String($L, charset).trim()", dataRef);
            method.addStatement("$L = strValue.isEmpty() ? $T.ZERO : new $T(strValue)",
                    targetRef, BigDecimal.class, BigDecimal.class);
        } else if (javaType.equals(ClassName.get(BigInteger.class))) {
            method.addStatement("String strValue = new String($L, charset).trim()", dataRef);
            method.addStatement("$L = strValue.isEmpty() ? $T.ZERO : new $T(strValue)",
                    targetRef, BigInteger.class, BigInteger.class);
        }
        method.endControlFlow(); // End block scope
    }

    private void addCompDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                        String targetRef, String dataRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();

        method.addStatement("$T buffer = $T.wrap($L)", ByteBuffer.class, ByteBuffer.class, dataRef);

        TypeName javaType = getBaseJavaType(field);
        if (totalDigits <= 4) {
            if (javaType.equals(ClassName.get(Short.class))) {
                method.addStatement("$L = buffer.getShort()", targetRef);
            } else {
                method.addStatement("$L = (int) buffer.getShort()", targetRef);
            }
        } else if (totalDigits <= 9) {
            method.addStatement("$L = buffer.getInt()", targetRef);
        } else {
            if (javaType.equals(ClassName.get(Long.class))) {
                method.addStatement("$L = buffer.getLong()", targetRef);
            } else {
                method.addStatement("$L = $T.valueOf(buffer.getLong())", targetRef, BigInteger.class);
            }
        }
    }

    private void addComp1Deserialization(MethodSpec.Builder method, String targetRef, String dataRef) {
        method.addStatement("$T buffer = $T.wrap($L)", ByteBuffer.class, ByteBuffer.class, dataRef);
        method.addStatement("$L = buffer.getFloat()", targetRef);
    }

    private void addComp2Deserialization(MethodSpec.Builder method, String targetRef, String dataRef) {
        method.addStatement("$T buffer = $T.wrap($L)", ByteBuffer.class, ByteBuffer.class, dataRef);
        method.addStatement("$L = buffer.getDouble()", targetRef);
    }

    private void addComp3Deserialization(MethodSpec.Builder method, FieldDefinition field,
                                         String targetRef, String dataRef) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();

        // Use block scope to avoid variable redeclaration
        method.beginControlFlow("");
        method.addStatement("$T digits = new $T()", StringBuilder.class, StringBuilder.class);
        method.beginControlFlow("for (int i = 0; i < $L.length - 1; i++)", dataRef);
        method.addStatement("int highNibble = ($L[i] >> 4) & 0x0F", dataRef);
        method.addStatement("int lowNibble = $L[i] & 0x0F", dataRef);
        method.addStatement("digits.append(highNibble)");
        method.addStatement("digits.append(lowNibble)");
        method.endControlFlow();

        method.addStatement("int lastDigit = ($L[$L.length - 1] >> 4) & 0x0F", dataRef, dataRef);
        method.addStatement("int sign = $L[$L.length - 1] & 0x0F", dataRef, dataRef);

        // Only append last digit if total digits is odd
        method.beginControlFlow("if ($L % 2 != 0)", totalDigits);
        method.addStatement("digits.append(lastDigit)");
        method.endControlFlow();

        method.addStatement("boolean isNegative = (sign == 0x0D || sign == 0x0B)");

        method.addStatement("$T biValue = new $T(digits.toString())", BigInteger.class, BigInteger.class);
        method.beginControlFlow("if (isNegative)");
        method.addStatement("biValue = biValue.negate()");
        method.endControlFlow();

        TypeName javaType = getBaseJavaType(field);
        if (field.getDecimalDigits() > 0) {
            method.addStatement("$T bdValue = new $T(biValue)", BigDecimal.class, BigDecimal.class);
            method.addStatement("$L = bdValue.divide($T.TEN.pow($L), $L, $T.HALF_UP).stripTrailingZeros()",
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
        method.endControlFlow(); // End block scope
    }

    private void addZonedDecimalDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                                String targetRef, String dataRef) {
        int length = field.getIntegerDigits() + field.getDecimalDigits();

        method.addStatement("$T digits = new $T()", StringBuilder.class, StringBuilder.class);
        method.addStatement("boolean isNegative = false");

        method.beginControlFlow("for (int i = 0; i < $L; i++)", length);
        method.addStatement("int digit = $L[i] & 0x0F", dataRef);
        method.addStatement("digits.append(digit)");
        method.beginControlFlow("if (i == $L - 1)", length);
        method.addStatement("int zone = $L[i] & 0xF0", dataRef);
        method.addStatement("isNegative = (zone == 0xD0 || zone == 0xB0)");
        method.endControlFlow();
        method.endControlFlow();

        method.addStatement("$T biValue = new $T(digits.toString())", BigInteger.class, BigInteger.class);
        method.beginControlFlow("if (isNegative)");
        method.addStatement("biValue = biValue.negate()");
        method.endControlFlow();

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
        return switch (field.getType()) {
            case DISPLAY -> {
                int length = field.getIntegerDigits() + field.getDecimalDigits();
                if (length == 0 && field.getPicture() != null) {
                    // Try to calculate from picture
                    length = field.getPicture().replaceAll("[^X9]", "").length();
                }
                yield length;
            }
            case COMP, COMP_5 -> {
                int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
                if (totalDigits <= 4) yield 2;
                else if (totalDigits <= 9) yield 4;
                else yield 8;
            }
            case COMP_1 -> 4;
            case COMP_2 -> 8;
            case COMP_3, PACKED_DECIMAL -> {
                int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
                yield (totalDigits / 2) + 1;
            }
            case ZONED_DECIMAL -> field.getIntegerDigits() + field.getDecimalDigits();
        };
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

}

