package org.pojobook.generator.common;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;

/**
 * Generates getter and setter methods for POJO fields with validation.
 */
public class GetterSetterGenerator {

    private final FieldValidator fieldValidator;

    public GetterSetterGenerator(FieldValidator fieldValidator) {
        this.fieldValidator = fieldValidator;
    }

    /**
     * Add getter and setter for a field.
     */
    public void addGetterSetter(TypeSpec.Builder builder, FieldDefinition field, 
                                 String fieldName, TypeName fieldType) {
        String methodName = capitalizeFieldName(fieldName);

        builder.addMethod(createGetter(methodName, fieldName, fieldType));
        builder.addMethod(createSetter(methodName, fieldName, fieldType, field));
    }

    private String capitalizeFieldName(String fieldName) {
        return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private MethodSpec createGetter(String methodName, String fieldName, TypeName type) {
        return MethodSpec.methodBuilder("get" + methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addStatement("return $L", fieldName)
                .build();
    }

    private MethodSpec createSetter(String methodName, String fieldName, TypeName fieldType,
                                    FieldDefinition field) {
        MethodSpec.Builder setter = MethodSpec.methodBuilder("set" + methodName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(fieldType, fieldName)
                .returns(void.class);

        fieldValidator.addValidationAndAssignment(setter, field, fieldName, fieldType);

        return setter.build();
    }
}

