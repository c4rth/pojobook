package org.pojobook.generator.embedded;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.exception.SerializationException;
import org.pojobook.generator.FieldNameTracker;
import org.pojobook.generator.FieldNode;
import org.pojobook.parser.FieldDefinition;
import org.pojobook.serializer.CobolFieldSerializer;

import javax.lang.model.element.Modifier;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Generates serialization methods for COBOL field types.
 */
public class SerializationMethodsGenerator {

    private final FieldNameTracker fieldNameTracker;
    private final OffsetCalculator offsetCalculator;

    public SerializationMethodsGenerator(FieldNameTracker fieldNameTracker, OffsetCalculator offsetCalculator) {
        this.fieldNameTracker = fieldNameTracker;
        this.offsetCalculator = offsetCalculator;
    }

    /**
     * Add serialize methods to the class.
     */
    public void addSerializeMethods(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        // Calculate total size for pre-allocation
        int totalSize = offsetCalculator.calculateNestedClassSize(fieldTree);

        addSerializeWithDefaultCharset(builder);
        addSerializeWithCharset(builder, fieldTree, totalSize);
        addSerializeToBuffer(builder, fieldTree);
    }

    private void addSerializeWithDefaultCharset(TypeSpec.Builder builder) {
        MethodSpec method = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(byte[].class)
                .addException(SerializationException.class)
                .addJavadoc("Serialize this object to COBOL binary format using CP1047 charset.\n")
                .addJavadoc("@return byte array containing the serialized data\n")
                .addJavadoc("@throws SerializationException if an I/O error occurs\n")
                .addStatement("return this.serialize(CHARSET_CP1047)")
                .build();

        builder.addMethod(method);
    }

    private void addSerializeWithCharset(TypeSpec.Builder builder, List<FieldNode> fieldTree, int totalSize) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Charset.class, "charset")
                .returns(byte[].class)
                .addException(SerializationException.class)
                .addJavadoc("Serialize this object to COBOL binary format.\n")
                .addJavadoc("@return byte array containing the serialized data\n")
                .addJavadoc("@throws SerializationException if an I/O error occurs\n");

        method.beginControlFlow("try")
                .addStatement("byte[] result = new byte[$L]", totalSize);

        for (FieldNode node : fieldTree) {
            addSerializationCode(method, node, "this");
        }

        method.addStatement("return result")
                .nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("throw new $T(\"Serialization failed\", e)", SerializationException.class)
                .endControlFlow();

        builder.addMethod(method.build());
    }

    private void addSerializeToBuffer(TypeSpec.Builder builder, List<FieldNode> fieldTree) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("serializeToBuffer")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(byte[].class, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(Charset.class, "charset")
                .addException(SerializationException.class)
                .addJavadoc("Serialize this object directly to a byte buffer at the specified offset.\n")
                .addJavadoc("@param buffer destination byte array\n")
                .addJavadoc("@param offset starting position in the buffer\n")
                .addJavadoc("@param charset character encoding\n")
                .addJavadoc("@throws SerializationException if an I/O error occurs\n");

        method.beginControlFlow("try");

        for (FieldNode node : fieldTree) {
            addSerializationCodeToBuffer(method, node, "this", "offset");
        }

        method.nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("throw new $T(\"Serialization failed\", e)", SerializationException.class)
                .endControlFlow();

        builder.addMethod(method.build());
    }

    /**
     * Add serialization code for a field using pre-computed offsets.
     */
    private void addSerializationCode(MethodSpec.Builder method, FieldNode node, String objectRef) {
        FieldDefinition field = node.getField();

        // Skip 88-level condition names - they are not serialized
        if (field.getLevel() == 88) {
            return;
        }

        String fieldName = fieldNameTracker.toUniqueFieldName(field);
        String offsetConstant = "OFFSET_" + fieldName.replace("-", "_").toUpperCase();

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Nested class array - write directly to buffer instead of allocating intermediate arrays
            String sizeConstant = "SIZE_" + fieldName.replace("-", "_").toUpperCase();
            method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs())
                    .addStatement("$L.$L[i].serializeToBuffer(result, $L + (i * $L), charset)",
                            objectRef, fieldName, offsetConstant, sizeConstant)
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
                String sizeConstant = "SIZE_" + fieldName.replace("-", "_").toUpperCase();
                method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
                addFieldSerializationCode(method, field, objectRef + "." + fieldName + "[i]",
                        offsetConstant + " + (i * " + sizeConstant + ")", "result");
                method.endControlFlow();
            } else {
                // Single field
                addFieldSerializationCode(method, field, objectRef + "." + fieldName, offsetConstant, "result");
            }
        }
    }

    /**
     * Add serialization code for a field to a buffer at given offset (for nested classes).
     */
    private void addSerializationCodeToBuffer(MethodSpec.Builder method, FieldNode node, String objectRef, String baseOffset) {
        FieldDefinition field = node.getField();

        // Skip 88-level condition names - they are not serialized
        if (field.getLevel() == 88) {
            return;
        }

        String fieldName = fieldNameTracker.toUniqueFieldName(field);
        String offsetConstant = "OFFSET_" + fieldName.replace("-", "_").toUpperCase();
        String actualOffset = baseOffset + " + " + offsetConstant;

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Nested class array - write directly to buffer
            String sizeConstant = "SIZE_" + fieldName.replace("-", "_").toUpperCase();
            method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs())
                    .addStatement("$L.$L[i].serializeToBuffer(buffer, $L + (i * $L), charset)",
                            objectRef, fieldName, actualOffset, sizeConstant)
                    .endControlFlow();

        } else if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
            // Flattened group
            for (FieldNode child : node.getChildren()) {
                addSerializationCodeToBuffer(method, child, objectRef, baseOffset);
            }

        } else {
            // Simple field or array
            if (field.getOccurs() > 1) {
                // Array field
                String sizeConstant = "SIZE_" + fieldName.replace("-", "_").toUpperCase();
                method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
                addFieldSerializationCode(method, field, objectRef + "." + fieldName + "[i]",
                        actualOffset + " + (i * " + sizeConstant + ")", "buffer");
                method.endControlFlow();
            } else {
                // Single field
                addFieldSerializationCode(method, field, objectRef + "." + fieldName, actualOffset, "buffer");
            }
        }
    }

    /**
     * Add serialization code for a single field value.
     */
    private void addFieldSerializationCode(MethodSpec.Builder method, FieldDefinition field,
                                           String valueRef, String offsetExpr, String bufferName) {
        switch (field.getType()) {
            case DISPLAY -> addDisplaySerialization(method, field, valueRef, offsetExpr, bufferName);
            case COMP, COMP_5 -> addCompSerialization(method, field, valueRef, offsetExpr, bufferName);
            case COMP_1 -> addComp1Serialization(method, valueRef, offsetExpr, bufferName);
            case COMP_2 -> addComp2Serialization(method, valueRef, offsetExpr, bufferName);
            case COMP_3, PACKED_DECIMAL -> addComp3Serialization(method, field, valueRef, offsetExpr, bufferName);
            case ZONED_DECIMAL -> addZonedDecimalSerialization(method, field, valueRef, offsetExpr, bufferName);
        }
    }

    private void addDisplaySerialization(MethodSpec.Builder method, FieldDefinition field,
                                         String valueRef, String offsetExpr, String bufferName) {
        int length = offsetCalculator.calculateFieldSize(field);
        if (length == 0) {
            return;
        }
        boolean isNumeric = isNumericPicture(field);
        boolean signed = field.isSigned();
        boolean signSeparate = field.isSignSeparate();
        int decimalDigits = field.getDecimalDigits();
        boolean isLeadingSign = field.getSignPosition() != null && !field.getSignPosition().isEmpty()
                && "LEADING".equalsIgnoreCase(field.getSignPosition());

        if (signed && signSeparate) {
            method.addStatement("$T.serializeDisplayWithSeparateSignDirect($L, $L, (Number) $L, $L, $L, $L, charset)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, length, decimalDigits, isLeadingSign);
        } else if (signed && !signSeparate && isNumeric) {
            method.addStatement("$T.serializeDisplayWithEmbeddedSignDirect($L, $L, (Number) $L, $L, $L, charset)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, length, decimalDigits);
        } else if (!signed && decimalDigits > 0 && isNumeric) {
            method.addStatement("$T.serializeDisplayWithImpliedDecimalDirect($L, $L, (Number) $L, $L, $L, charset)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, length, decimalDigits);
        } else {
            method.addStatement("$T.serializeDisplayStringDirect($L, $L, $L, $L, $L, charset)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, length, isNumeric);
        }
    }

    private void addCompSerialization(MethodSpec.Builder method, FieldDefinition field,
                                      String valueRef, String offsetExpr, String bufferName) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        method.addStatement("$T.serializeCompDirect($L, $L, $L, $L)",
                CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, totalDigits);
    }

    private void addComp1Serialization(MethodSpec.Builder method, String valueRef, String offsetExpr, String bufferName) {
        method.addStatement("$T.serializeComp1Direct($L, $L, $L)",
                CobolFieldSerializer.class, bufferName, offsetExpr, valueRef);
    }

    private void addComp2Serialization(MethodSpec.Builder method, String valueRef, String offsetExpr, String bufferName) {
        method.addStatement("$T.serializeComp2Direct($L, $L, $L)",
                CobolFieldSerializer.class, bufferName, offsetExpr, valueRef);
    }

    private void addComp3Serialization(MethodSpec.Builder method, FieldDefinition field,
                                       String valueRef, String offsetExpr, String bufferName) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        method.addStatement("$T.serializeComp3Direct($L, $L, $L, $L, $L)",
                CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, totalDigits, field.getDecimalDigits());
    }

    private void addZonedDecimalSerialization(MethodSpec.Builder method, FieldDefinition field,
                                              String valueRef, String offsetExpr, String bufferName) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        method.addStatement("$T.serializeZonedDecimalDirect($L, $L, $L, $L, $L, $L)",
                CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, totalDigits, field.getDecimalDigits(), field.isSigned());
    }

    /**
     * Check if picture represents numeric.
     */
    private boolean isNumericPicture(FieldDefinition field) {
        String picture = field.getPicture();
        return picture != null && (picture.startsWith("9") || picture.startsWith("S9"));
    }
}

