package org.pojobook.generator.embedded;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.deserializer.CobolFieldDeserializer;
import org.pojobook.exception.DeserializationException;
import org.pojobook.generator.AbstractPojoGenerator;
import org.pojobook.generator.FieldNameTracker;
import org.pojobook.generator.FieldNode;
import org.pojobook.generator.NamingUtils;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Generates deserialization methods for COBOL field types.
 */
public class DeserializationMethodsGenerator {

    private final FieldNameTracker fieldNameTracker;
    private final OffsetCalculator offsetCalculator;
    private final AbstractPojoGenerator parentGenerator;

    public DeserializationMethodsGenerator(FieldNameTracker fieldNameTracker, OffsetCalculator offsetCalculator, AbstractPojoGenerator parentGenerator) {
        this.fieldNameTracker = fieldNameTracker;
        this.offsetCalculator = offsetCalculator;
        this.parentGenerator = parentGenerator;
    }

    /**
     * Reset temp variable counter.
     */
    public void resetTempVarCounter() {
        // No-op: legacy hook kept for GeneratorContext compatibility.
    }

    /**
     * Add deserialization methods to the class.
     */
    public void addDeserializeMethods(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree) {
        addDeserializeWithDefaultCharset(builder, className);
        addDeserializeWithCharset(builder, className);
        addDeserializeFromBuffer(builder, className, fieldTree);
    }

    private void addDeserializeWithDefaultCharset(TypeSpec.Builder builder, String className) {
        MethodSpec method = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.bestGuess(className))
                .addParameter(byte[].class, "data")
                .addException(DeserializationException.class)
                .addJavadoc("Deserialize COBOL binary data to create an instance of this class using CP1047 charset.\n")
                .addJavadoc("@param data byte array containing the serialized data\n")
                .addJavadoc("@return deserialized instance\n")
                .addJavadoc("@throws DeserializationException if deserialization fails\n")
                .addStatement("return $L.deserialize(data, CHARSET_CP1047)", className)
                .build();

        builder.addMethod(method);
    }

    private void addDeserializeWithCharset(TypeSpec.Builder builder, String className) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.bestGuess(className))
                .addParameter(byte[].class, "data")
                .addParameter(Charset.class, "charset")
                .addException(DeserializationException.class)
                .addJavadoc("Deserialize COBOL binary data to create an instance of this class.\n")
                .addJavadoc("@param data byte array containing the serialized data\n")
                .addJavadoc("@return deserialized instance\n")
                .addJavadoc("@throws DeserializationException if deserialization fails\n");

        method.addStatement("return deserializeFromBuffer(data, 0, charset)");

        builder.addMethod(method.build());
    }

    private void addDeserializeFromBuffer(TypeSpec.Builder builder, String className, List<FieldNode> fieldTree) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("deserializeFromBuffer")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(ClassName.bestGuess(className))
                .addParameter(byte[].class, "data")
                .addParameter(int.class, "offset")
                .addParameter(Charset.class, "charset")
                .addException(DeserializationException.class)
                .addJavadoc("Deserialize COBOL binary data from a byte array starting at a given offset.\n")
                .addJavadoc("@param data source byte array containing serialized data\n")
                .addJavadoc("@param offset start position in the source array\n")
                .addJavadoc("@param charset character encoding\n")
                .addJavadoc("@return deserialized instance\n")
                .addJavadoc("@throws DeserializationException if deserialization fails\n");

        method.beginControlFlow("try")
                .addStatement("$L instance = new $L()", className, className);

        for (FieldNode node : fieldTree) {
            addDeserializationCode(method, node, "instance");
        }

        method.addStatement("return instance")
                .nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("throw new $T(\"Serialization failed\", e)", DeserializationException.class)
                .endControlFlow();

        builder.addMethod(method.build());
    }

    /**
     * Add deserialization code for a field using pre-computed offsets.
     */
    private void addDeserializationCode(MethodSpec.Builder method, FieldNode node, String instanceRef) {
        FieldDefinition field = node.getField();

        // Skip 88-level condition names - they are not deserialized
        if (field.getLevel() == 88) {
            return;
        }

        String fieldName = fieldNameTracker.toUniqueFieldName(field);
        String offsetConstant = offsetCalculator.offsetConstantName(fieldName);

        if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
            // Nested class array
            String sizeConstant = offsetCalculator.sizeConstantName(fieldName);
            method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs())
                    .addStatement("$L.$L[i] = $L.deserializeFromBuffer(data, offset + $L + (i * $L), charset)",
                            instanceRef, fieldName, NamingUtils.toPascalCase(field.getName()), offsetConstant, sizeConstant)
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
                String sizeConstant = offsetCalculator.sizeConstantName(fieldName);
                method.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs());
                addFieldDeserializationCode(method, field, instanceRef + "." + fieldName + "[i]",
                        "data", "offset + " + offsetConstant + " + (i * " + sizeConstant + ")");
                method.endControlFlow();
            } else {
                // Single field
                addFieldDeserializationCode(method, field, instanceRef + "." + fieldName,
                        "data", "offset + " + offsetConstant);
            }
        }
    }

    /**
     * Add deserialization code for a single field value using pre-computed offset.
     */
    private void addFieldDeserializationCode(MethodSpec.Builder method, FieldDefinition field,
                                             String targetRef, String dataRef, String offsetExpr) {
        switch (field.getType()) {
            case DISPLAY -> addDisplayDeserialization(method, field, targetRef, dataRef, offsetExpr);
            case COMP, COMP_5 -> addCompDeserialization(method, field, targetRef, dataRef, offsetExpr);
            case COMP_1 -> addComp1Deserialization(method, targetRef, dataRef, offsetExpr);
            case COMP_2 -> addComp2Deserialization(method, targetRef, dataRef, offsetExpr);
            case COMP_3, PACKED_DECIMAL -> addComp3Deserialization(method, field, targetRef, dataRef, offsetExpr);
            case ZONED_DECIMAL -> addZonedDecimalDeserialization(method, field, targetRef, dataRef, offsetExpr);
        }
    }

    private void addDisplayDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                           String targetRef, String dataRef, String offsetExpr) {
        int length = offsetCalculator.calculateFieldSize(field);
        if (length == 0) {
            return;
        }
        TypeName javaType = getBaseJavaType(field);
        boolean signed = field.isSigned();
        int decimalDigits = field.getDecimalDigits();

        if (javaType.equals(ClassName.get(String.class))) {
            method.addStatement("$L = $T.deserializeDisplayString($L, $L, $L, charset)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
        } else if (signed) {
            addSignedNumericDeserialization(method, field, targetRef, dataRef, offsetExpr, javaType, length, decimalDigits);
        } else {
            addUnsignedNumericDeserialization(method, field, targetRef, dataRef, offsetExpr, javaType, length, decimalDigits);
        }
    }

    private void addSignedNumericDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                                  String targetRef, String dataRef, String offsetExpr,
                                                  TypeName javaType, int length, int decimalDigits) {
        if (javaType.equals(ClassName.get(Integer.class))) {
            method.addStatement("$L = $T.deserializeDisplaySignedInteger($L, $L, $L, charset)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
        } else if (javaType.equals(ClassName.get(Long.class))) {
            method.addStatement("$L = $T.deserializeDisplaySignedLong($L, $L, $L, charset)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
        } else if (javaType.equals(ClassName.get(BigDecimal.class))) {
            method.addStatement("$L = $T.deserializeDisplaySignedBigDecimal($L, $L, $L, charset, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, decimalDigits);
        } else if (javaType.equals(ClassName.get(BigInteger.class))) {
            method.addStatement("$L = $T.deserializeDisplaySignedBigInteger($L, $L, $L, charset)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
        } else if (javaType.equals(ClassName.get(Short.class))) {
            method.addStatement("$L = (short) $T.deserializeDisplaySignedInteger($L, $L, $L, charset).intValue()",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
        }
    }

    private void addUnsignedNumericDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                                    String targetRef, String dataRef, String offsetExpr,
                                                    TypeName javaType, int length, int decimalDigits) {
        if (javaType.equals(ClassName.get(Integer.class))) {
            if (decimalDigits > 0) {
                method.addStatement("$L = $T.deserializeDisplayIntegerWithDecimal($L, $L, $L, charset, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, decimalDigits);
            } else {
                method.addStatement("$L = $T.deserializeDisplayInteger($L, $L, $L, charset)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
            }
        } else if (javaType.equals(ClassName.get(Long.class))) {
            if (decimalDigits > 0) {
                method.addStatement("$L = $T.deserializeDisplayLongWithDecimal($L, $L, $L, charset, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, decimalDigits);
            } else {
                method.addStatement("$L = $T.deserializeDisplayLong($L, $L, $L, charset)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
            }
        } else if (javaType.equals(ClassName.get(Short.class))) {
            method.addStatement("$L = $T.deserializeDisplayShort($L, $L, $L, charset)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
        } else if (javaType.equals(ClassName.get(BigDecimal.class))) {
            if (decimalDigits > 0) {
                method.addStatement("$L = $T.deserializeDisplayBigDecimalWithDecimal($L, $L, $L, charset, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, decimalDigits);
            } else {
                method.addStatement("$L = $T.deserializeDisplayBigDecimal($L, $L, $L, charset)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
            }
        } else if (javaType.equals(ClassName.get(BigInteger.class))) {
            if (decimalDigits > 0) {
                method.addStatement("$L = $T.deserializeDisplayBigDecimalWithDecimal($L, $L, $L, charset, $L).toBigInteger()",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, decimalDigits);
            } else {
                method.addStatement("$L = $T.deserializeDisplayBigInteger($L, $L, $L, charset)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
            }
        }
    }

    private void addCompDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                        String targetRef, String dataRef, String offsetExpr) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        int length = offsetCalculator.calculateFieldSize(field);

        if (field.getDecimalDigits() > 0) {
            method.addStatement("$L = $T.deserializeCompBigDecimal($L, $L, $L, $L, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, totalDigits, field.getDecimalDigits());
        } else {
            TypeName fieldType = getBaseJavaType(field);
            if (fieldType.equals(ClassName.get(Integer.class))) {
                method.addStatement("$L = $T.deserializeCompInteger($L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
            } else if (fieldType.equals(ClassName.get(Long.class))) {
                method.addStatement("$L = $T.deserializeCompLong($L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
            } else if (fieldType.equals(ClassName.get(Short.class))) {
                method.addStatement("$L = $T.deserializeCompShort($L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
            } else {
                method.addStatement("$L = $T.deserializeCompBigInteger($L, $L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, totalDigits);
            }
        }
    }

    private void addComp1Deserialization(MethodSpec.Builder method, String targetRef,
                                         String dataRef, String offsetExpr) {
        method.addStatement("$L = $T.deserializeComp1($L, $L)",
                targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr);
    }

    private void addComp2Deserialization(MethodSpec.Builder method, String targetRef,
                                         String dataRef, String offsetExpr) {
        method.addStatement("$L = $T.deserializeComp2($L, $L)",
                targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr);
    }

    private void addComp3Deserialization(MethodSpec.Builder method, FieldDefinition field,
                                         String targetRef, String dataRef, String offsetExpr) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        int length = offsetCalculator.calculateFieldSize(field);

        if (field.getDecimalDigits() > 0) {
            method.addStatement("$L = $T.deserializeComp3BigDecimal($L, $L, $L, $L, $L)",
                    targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, totalDigits, field.getDecimalDigits());
        } else {
            TypeName fieldType = getBaseJavaType(field);
            if (fieldType.equals(ClassName.get(Integer.class))) {
                method.addStatement("$L = $T.deserializeComp3Integer($L, $L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, totalDigits);
            } else if (fieldType.equals(ClassName.get(Long.class))) {
                method.addStatement("$L = $T.deserializeComp3Long($L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
            } else if (fieldType.equals(ClassName.get(Short.class))) {
                method.addStatement("$L = $T.deserializeComp3Short($L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length);
            } else {
                method.addStatement("$L = $T.deserializeComp3BigInteger($L, $L, $L, $L)",
                        targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, length, totalDigits);
            }
        }
    }

    private void addZonedDecimalDeserialization(MethodSpec.Builder method, FieldDefinition field,
                                                String targetRef, String dataRef, String offsetExpr) {
        int totalDigits = field.getIntegerDigits() + field.getDecimalDigits();
        method.addStatement("$L = $T.deserializeZonedDecimalBigDecimal($L, $L, $L, $L, $L)",
                targetRef, CobolFieldDeserializer.class, dataRef, offsetExpr, totalDigits, totalDigits, field.getDecimalDigits());
    }

    private TypeName getBaseJavaType(FieldDefinition field) {
        // Delegate to parent generator which has all the proper type resolution logic
        return parentGenerator.getBaseJavaType(field);
    }
}

