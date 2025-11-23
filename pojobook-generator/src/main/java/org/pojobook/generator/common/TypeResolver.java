package org.pojobook.generator.common;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import org.pojobook.parser.FieldDefinition;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Resolves COBOL field definitions to Java types.
 */
public class TypeResolver {

    /**
     * Get base Java type for a field (non-array).
     */
    public TypeName getBaseJavaType(FieldDefinition field) {
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
     * Get default value for a field.
     */
    public String getDefaultValue(FieldDefinition field, TypeName baseType) {
        if (field.getOccurs() > 1) {
            return String.format("new %s[%d]", baseType.toString(), field.getOccurs());
        }

        return switch (baseType.toString()) {
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
}

