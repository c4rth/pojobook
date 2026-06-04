package org.pojobook.generator.standalone;

import com.palantir.javapoet.MethodSpec;
import org.pojobook.generator.FieldNameTracker;
import org.pojobook.generator.FieldNode;
import org.pojobook.generator.NamingUtils;
import org.pojobook.parser.FieldDefinition;

import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Generates constructor code with proper array initialization for nested classes.
 */
public class ConstructorGenerator {

    private final FieldNameTracker fieldNameTracker;

    public ConstructorGenerator(FieldNameTracker fieldNameTracker) {
        this.fieldNameTracker = fieldNameTracker;
    }

    /**
     * Generate constructor method.
     * Only initializes nested class arrays (other fields are initialized at declaration).
     */
    public MethodSpec generateConstructor(List<FieldNode> fieldTree) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        // Initialize only direct nested class arrays (their constructors will handle their own nested arrays)
        addDirectArrayInitializations(constructor, fieldTree);

        return constructor.build();
    }

    /**
     * Add array initializations only for direct children (non-recursive for nested class fields).
     */
    private void addDirectArrayInitializations(MethodSpec.Builder constructor, List<FieldNode> nodes) {
        for (FieldNode node : nodes) {
            FieldDefinition field = node.getField();

            // Skip 88-level condition names
            if (field.getLevel() == 88) {
                continue;
            }

            if (field.isGroup() && field.getOccurs() > 1 && !node.getChildren().isEmpty()) {
                // This is a nested class array - initialize it (nested class constructor handles its own arrays)
                String fieldName = fieldNameTracker.toUniqueFieldName(field);
                String className = NamingUtils.toPascalCase(field.getName());
                constructor.beginControlFlow("for (int i = 0; i < $L; i++)", field.getOccurs())
                        .addStatement("this.$L[i] = new $L()", fieldName, className)
                        .endControlFlow();
                // Don't recurse - nested class constructor will initialize its own arrays
            } else if (field.isGroup() && field.getOccurs() == 1 && !node.getChildren().isEmpty()) {
                // Flattened group - recurse into children to find nested class arrays at this level
                addDirectArrayInitializations(constructor, node.getChildren());
            }
            // Simple fields and simple arrays don't need initialization (already done at declaration)
        }
    }
}

