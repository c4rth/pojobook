package org.pojobook.generator.common;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.parser.ConditionName;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;

/**
 * Generates condition name (88-level) checker methods.
 */
public class ConditionNameMethodGenerator {

    private final TypeResolver typeResolver;

    public ConditionNameMethodGenerator(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    /**
     * Add condition name (88-level) checker methods for a field.
     */
    public void addConditionNameMethods(TypeSpec.Builder builder, FieldDefinition field, String fieldName) {
        if (!field.hasConditionNames()) {
            return;
        }

        TypeName fieldType = typeResolver.getBaseJavaType(field);
        boolean isNumeric = isNumericType(fieldType.toString());

        for (ConditionName condition : field.getConditionNames()) {
            String methodName = createMethodName(field, condition);
            MethodSpec method = createConditionMethod(methodName, fieldName, condition, isNumeric);
            builder.addMethod(method);
        }
    }

    private String createMethodName(FieldDefinition field, ConditionName condition) {
        // Create unique method name: is<FieldName><ConditionName>
        // E.g., for field "NM-FUNCTION" with condition "CONSULT" -> "isNmFunctionConsult"
        String fieldNameCamel = org.pojobook.generator.NamingUtils.toCamelCase(field.getName());
        String conditionNameCamel = org.pojobook.generator.NamingUtils.toCamelCase(condition.getName());
        
        return "is" + Character.toUpperCase(fieldNameCamel.charAt(0)) + fieldNameCamel.substring(1)
                + Character.toUpperCase(conditionNameCamel.charAt(0)) + conditionNameCamel.substring(1);
    }

    private MethodSpec createConditionMethod(String methodName, String fieldName, 
                                             ConditionName condition, boolean isNumeric) {
        MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addJavadoc("Check if $L matches condition $L.\n", fieldName, condition.getName())
                .addJavadoc("@return true if $L equals one of: $L\n",
                        fieldName,
                        String.join(", ", condition.getValues()));

        if (condition.getValues().length == 1) {
            addSingleValueComparison(method, fieldName, condition.getValues()[0], isNumeric);
        } else {
            addMultiValueComparison(method, fieldName, condition.getValues(), isNumeric);
        }

        return method.build();
    }

    private void addSingleValueComparison(MethodSpec.Builder method, String fieldName, 
                                         String value, boolean isNumeric) {
        if (isNumeric) {
            try {
                Integer.parseInt(value.trim());
                method.addStatement("return $L != null && $L.equals($L)",
                        fieldName, fieldName, value.trim());
            } catch (NumberFormatException e) {
                method.addStatement("return $L != null && $L.equals($S)",
                        fieldName, fieldName, value);
            }
        } else {
            method.addStatement("return $L != null && $L.equals($S)",
                    fieldName, fieldName, value);
        }
    }

    private void addMultiValueComparison(MethodSpec.Builder method, String fieldName,
                                        String[] values, boolean isNumeric) {
        method.beginControlFlow("if ($L == null)", fieldName)
                .addStatement("return false")
                .endControlFlow();

        CodeBlock.Builder codeBlock = CodeBlock.builder().add("return ");

        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                codeBlock.add(" || ");
            }
            if (isNumeric) {
                try {
                    Integer.parseInt(values[i].trim());
                    codeBlock.add("$L.equals($L)", fieldName, values[i].trim());
                } catch (NumberFormatException e) {
                    codeBlock.add("$L.equals($S)", fieldName, values[i]);
                }
            } else {
                codeBlock.add("$L.equals($S)", fieldName, values[i]);
            }
        }

        method.addStatement(codeBlock.build());
    }

    private boolean isNumericType(String javaType) {
        return javaType.matches(".*(Integer|Long|Short|BigInteger|BigDecimal).*");
    }
}

