package org.pojobook.generator.common;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import org.pojobook.parser.FieldDefinition;

import java.math.BigDecimal;

/**
 * Handles field validation logic for setter methods.
 */
public class FieldValidator {

    /**
     * Add validation and assignment to a setter method.
     */
    public void addValidationAndAssignment(MethodSpec.Builder setter, FieldDefinition field,
                                           String fieldName, TypeName fieldType) {
        ClassName utilClass = ClassName.get("org.pojobook.util", "FieldLengthUtil");
        String javaType = fieldType.toString();

        // Handle BigDecimal specially (with stripTrailingZeros)
        if (isBigDecimalType(fieldType)) {
            addBigDecimalValidationAndAssignment(setter, field, fieldName, utilClass);
        } else if (isBigDecimalArrayType(fieldType)) {
            addBigDecimalArrayValidationAndAssignment(setter, field, fieldName, utilClass);
        } else {
            // For all other types
            addSimpleValidationAndAssignment(setter, field, fieldName, javaType, utilClass);
        }
    }

    private boolean isBigDecimalType(TypeName fieldType) {
        String type = fieldType.toString();
        return type.equals("java.math.BigDecimal") || type.equals("BigDecimal");
    }

    private boolean isBigDecimalArrayType(TypeName fieldType) {
        String type = fieldType.toString();
        return type.equals("java.math.BigDecimal[]") || type.equals("BigDecimal[]");
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
}

