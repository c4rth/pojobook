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

        addSerializedSizeMethod(builder, totalSize);
        addSerializeWithDefaultCharset(builder);
        addSerializeIntoBufferOverloads(builder);
        addSerializeWithCharset(builder, totalSize);
        addSerializeToBuffer(builder, fieldTree);
    }

    private void addSerializedSizeMethod(TypeSpec.Builder builder, int totalSize) {
        MethodSpec method = MethodSpec.methodBuilder("serializedSize")
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addJavadoc("Return the serialized record size in bytes.\n")
                .addStatement("return $L", totalSize)
                .build();

        builder.addMethod(method);
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

    private void addSerializeIntoBufferOverloads(TypeSpec.Builder builder) {
        MethodSpec serializeWithDefaultCharset = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(byte[].class, "buffer")
                .addException(SerializationException.class)
                .addJavadoc("Serialize this object into a caller-provided buffer using CP1047 charset.\n")
                .addJavadoc("@param buffer destination byte array\n")
                .addJavadoc("@throws SerializationException if serialization fails\n")
                .addStatement("this.serialize(buffer, 0)")
                .build();

        MethodSpec serializeWithOffset = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(byte[].class, "buffer")
                .addParameter(int.class, "offset")
                .addException(SerializationException.class)
                .addJavadoc("Serialize this object into a caller-provided buffer using CP1047 charset.\n")
                .addJavadoc("@param buffer destination byte array\n")
                .addJavadoc("@param offset starting position in the buffer\n")
                .addJavadoc("@throws SerializationException if serialization fails\n")
                .addStatement("this.serializeToBuffer(buffer, offset, CHARSET_CP1047)")
                .build();

        builder.addMethod(serializeWithDefaultCharset);
        builder.addMethod(serializeWithOffset);
    }

    private void addSerializeWithCharset(TypeSpec.Builder builder, int totalSize) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Charset.class, "charset")
                .returns(byte[].class)
                .addException(SerializationException.class)
                .addJavadoc("Serialize this object to COBOL binary format.\n")
                .addJavadoc("@return byte array containing the serialized data\n")
                .addJavadoc("@throws SerializationException if an I/O error occurs\n");

        method.addStatement("byte[] result = new byte[$L]", totalSize)
                .addStatement("this.serializeToBuffer(result, 0, charset)")
                .addStatement("return result");

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

        for (FieldNode node : fieldTree) {
            addSerializationCodeToBuffer(method, node, "this", "offset");
        }


        builder.addMethod(method.build());
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
        String offsetConstant = offsetCalculator.offsetConstantName(fieldName);
        String actualOffset = baseOffset + " + " + offsetConstant;

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Nested class array - write directly to buffer
            String sizeConstant = offsetCalculator.sizeConstantName(fieldName);
            String baseOffsetVar = fieldName + "BaseOffset";
            String posVar = fieldName + "Pos";
            method.addStatement("int $L = $L", baseOffsetVar, actualOffset)
                    .addStatement("int $L = $L", posVar, baseOffsetVar);
            method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs())
                    .addStatement("$L.$L[i].serializeToBuffer(buffer, $L, charset)",
                            objectRef, fieldName, posVar)
                    .addStatement("$L += $L", posVar, sizeConstant)
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
                String sizeConstant = offsetCalculator.sizeConstantName(fieldName);
                String baseOffsetVar = fieldName + "BaseOffset";
                String posVar = fieldName + "Pos";
                method.addStatement("int $L = $L", baseOffsetVar, actualOffset)
                        .addStatement("int $L = $L", posVar, baseOffsetVar);
                method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
                addFieldSerializationCode(method, fieldName, field, objectRef + "." + fieldName + "[i]",
                        posVar, "buffer");
                method.addStatement("$L += $L", posVar, sizeConstant);
                method.endControlFlow();
            } else {
                // Single field
                addFieldSerializationCode(method, fieldName, field, objectRef + "." + fieldName, actualOffset, "buffer");
            }
        }
    }

    /**
     * Add serialization code for a single field value.
     */
    private void addFieldSerializationCode(MethodSpec.Builder method, String fieldName, FieldDefinition field,
                                           String valueRef, String offsetExpr, String bufferName) {
        switch (field.getType()) {
            case DISPLAY -> addDisplaySerialization(method, fieldName, field, valueRef, offsetExpr, bufferName);
            case COMP, COMP_5 -> addCompSerialization(method, fieldName, field, valueRef, offsetExpr, bufferName);
            case COMP_1 -> addComp1Serialization(method, valueRef, offsetExpr, bufferName);
            case COMP_2 -> addComp2Serialization(method, valueRef, offsetExpr, bufferName);
            case COMP_3, PACKED_DECIMAL -> addComp3Serialization(method, fieldName, field, valueRef, offsetExpr, bufferName);
            case ZONED_DECIMAL -> addZonedDecimalSerialization(method, fieldName, field, valueRef, offsetExpr, bufferName);
        }
    }

    private void addDisplaySerialization(MethodSpec.Builder method, String fieldName, FieldDefinition field,
                                         String valueRef, String offsetExpr, String bufferName) {
        int length = offsetCalculator.calculateFieldSize(field);
        if (length == 0) {
            return;
        }
        String lenConstant = lengthConstantName(fieldName);
        String isNumericConstant = isNumericConstantName(fieldName);
        String decimalDigitsConstant = decimalDigitsConstantName(fieldName);
        boolean isNumeric = isNumericPicture(field);
        boolean signed = field.isSigned();
        boolean signSeparate = field.isSignSeparate();

        if (signed && signSeparate) {
            String leadingSignConstant = leadingSignConstantName(fieldName);
            method.addStatement("$T.serializeDisplayWithSeparateSignDirect($L, $L, $L, $L, $L, $L, charset)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, lenConstant, decimalDigitsConstant,
                    leadingSignConstant);
        } else if (signed && !signSeparate && isNumeric) {
            method.addStatement("$T.serializeDisplayWithEmbeddedSignDirect($L, $L, $L, $L, $L, charset)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, lenConstant, decimalDigitsConstant);
        } else if (!signed && field.getDecimalDigits() > 0 && isNumeric) {
            method.addStatement("$T.serializeDisplayWithImpliedDecimalDirect($L, $L, $L, $L, $L, charset)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, lenConstant, decimalDigitsConstant);
        } else {
            method.addStatement("$T.serializeDisplayStringDirect($L, $L, $L, $L, $L, charset)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, lenConstant, isNumericConstant);
        }
    }

    private void addCompSerialization(MethodSpec.Builder method, String fieldName, FieldDefinition field,
                                      String valueRef, String offsetExpr, String bufferName) {
        String totalDigitsConstant = totalDigitsConstantName(fieldName);
        method.addStatement("$T.serializeCompDirect($L, $L, $L, $L)",
                CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, totalDigitsConstant);
    }

    private void addComp1Serialization(MethodSpec.Builder method, String valueRef, String offsetExpr, String bufferName) {
        method.addStatement("$T.serializeComp1Direct($L, $L, $L)",
                CobolFieldSerializer.class, bufferName, offsetExpr, valueRef);
    }

    private void addComp2Serialization(MethodSpec.Builder method, String valueRef, String offsetExpr, String bufferName) {
        method.addStatement("$T.serializeComp2Direct($L, $L, $L)",
                CobolFieldSerializer.class, bufferName, offsetExpr, valueRef);
    }

    private void addComp3Serialization(MethodSpec.Builder method, String fieldName, FieldDefinition field,
                                        String valueRef, String offsetExpr, String bufferName) {
        String totalDigitsConstant = totalDigitsConstantName(fieldName);
        if (field.getDecimalDigits() > 0) {
            String scaleFactorConstant = scaleFactorConstantName(fieldName);
            method.addStatement("$T.serializeComp3Direct($L, $L, $L, $L, $L)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, totalDigitsConstant, scaleFactorConstant);
        } else {
            method.addStatement("$T.serializeComp3Direct($L, $L, $L, $L, $L)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, totalDigitsConstant, "0");
        }
    }

    private void addZonedDecimalSerialization(MethodSpec.Builder method, String fieldName, FieldDefinition field,
                                               String valueRef, String offsetExpr, String bufferName) {
        String totalDigitsConstant = totalDigitsConstantName(fieldName);
        String signedConstant = signedConstantName(fieldName);
        if (field.getDecimalDigits() > 0) {
            String scaleFactorConstant = scaleFactorConstantName(fieldName);
            method.addStatement("$T.serializeZonedDecimalDirect($L, $L, $L, $L, $L, $L)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, totalDigitsConstant, scaleFactorConstant,
                    signedConstant);
        } else {
            String decimalDigitsConstant = decimalDigitsConstantName(fieldName);
            method.addStatement("$T.serializeZonedDecimalDirect($L, $L, $L, $L, $L, $L)",
                    CobolFieldSerializer.class, bufferName, offsetExpr, valueRef, totalDigitsConstant, decimalDigitsConstant,
                    signedConstant);
        }
    }

    private String lengthConstantName(String fieldName) {
        return "LEN_" + fieldName.toUpperCase();
    }

    private String isNumericConstantName(String fieldName) {
        return "IS_NUMERIC_" + fieldName.toUpperCase();
    }

    private String decimalDigitsConstantName(String fieldName) {
        return "DECIMAL_DIGITS_" + fieldName.toUpperCase();
    }

    private String totalDigitsConstantName(String fieldName) {
        return "TOTAL_DIGITS_" + fieldName.toUpperCase();
    }

    private String signedConstantName(String fieldName) {
        return "SIGNED_" + fieldName.toUpperCase();
    }

    private String scaleFactorConstantName(String fieldName) {
        return "SCALE_FACTOR_" + fieldName.toUpperCase();
    }

    private String leadingSignConstantName(String fieldName) {
        return "LEADING_SIGN_" + fieldName.toUpperCase();
    }

    /**
     * Check if picture represents numeric.
     */
    private boolean isNumericPicture(FieldDefinition field) {
        String picture = field.getPicture();
        return picture != null && (picture.startsWith("9") || picture.startsWith("S9"));
    }
}

