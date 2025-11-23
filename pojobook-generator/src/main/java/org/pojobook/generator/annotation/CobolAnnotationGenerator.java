package org.pojobook.generator.annotation;

import com.palantir.javapoet.AnnotationSpec;
import org.pojobook.CobolDataType;
import org.pojobook.annotation.CobolField;
import org.pojobook.parser.FieldDefinition;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Generates COBOL field annotations for annotation-based POJOs.
 */
public class CobolAnnotationGenerator {

    /**
     * Create @CobolField annotation for a field definition.
     */
    public AnnotationSpec createCobolFieldAnnotation(FieldDefinition field) {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(CobolField.class)
                .addMember("level", "$L", field.getLevel())
                .addMember("name", "$S", field.getName())
                .addMember("type", "$T.$L", CobolDataType.class, field.getType());

        // Add optional members
        addOptionalMember(builder, "picture", field.getPicture());
        addOptionalMemberIf(builder, "integerDigits", field.getIntegerDigits(), d -> d > 0);
        addOptionalMemberIf(builder, "decimalDigits", field.getDecimalDigits(), d -> d > 0);
        addOptionalMemberIf(builder, "signed", field.isSigned(), Boolean::booleanValue);
        addOptionalMember(builder, "signPosition", field.getSignPosition());
        addOptionalMemberIf(builder, "signSeparate", field.isSignSeparate(), Boolean::booleanValue);
        addOptionalMemberIf(builder, "occurs", field.getOccurs(), o -> o > 1);

        return builder.build();
    }

    /**
     * Add optional string member to annotation builder.
     */
    private void addOptionalMember(AnnotationSpec.Builder builder, String name, String value) {
        Optional.ofNullable(value)
                .ifPresent(v -> builder.addMember(name, "$S", v));
    }

    /**
     * Add optional member with predicate.
     */
    private <T> void addOptionalMemberIf(AnnotationSpec.Builder builder, String name, T value, Predicate<T> predicate) {
        Optional.ofNullable(value)
                .filter(predicate)
                .ifPresent(v -> builder.addMember(name, "$L", v));
    }
}

