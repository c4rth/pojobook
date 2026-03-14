package org.pojobook.generator;

import org.junit.jupiter.api.Test;
import org.pojobook.parser.FieldDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldNodeTest {

    @Test
    void buildTreeShouldOnlyIncludeFirstLevelOneRecord() {
        List<FieldDefinition> fields = List.of(
                group(1, "FIRST-RECORD"),
                scalar(5, "FIRST-FIELD", "X(3)"),
                group(1, "SECOND-RECORD"),
                scalar(5, "SECOND-FIELD", "X(3)")
        );

        List<FieldNode> tree = FieldNode.buildTree(fields);

        assertEquals(1, tree.size());
        assertEquals("FIRST-RECORD", tree.getFirst().getField().getName());
        assertEquals(1, tree.getFirst().getChildren().size());
        assertEquals("FIRST-FIELD", tree.getFirst().getChildren().getFirst().getField().getName());
    }

    @Test
    void buildTreeShouldPreserveNestedHierarchy() {
        List<FieldDefinition> fields = List.of(
                group(1, "ROOT"),
                group(5, "GROUP-A"),
                scalar(10, "FIELD-A1", "9(2)"),
                group(10, "GROUP-B"),
                scalar(15, "FIELD-B1", "X(4)"),
                scalar(5, "FIELD-C", "9(1)")
        );

        List<FieldNode> tree = FieldNode.buildTree(fields);

        FieldNode root = tree.getFirst();
        assertEquals("ROOT", root.getField().getName());
        assertEquals(2, root.getChildren().size());

        FieldNode groupA = root.getChildren().getFirst();
        assertEquals("GROUP-A", groupA.getField().getName());
        assertEquals(2, groupA.getChildren().size());
        assertEquals("FIELD-A1", groupA.getChildren().getFirst().getField().getName());

        FieldNode groupB = groupA.getChildren().get(1);
        assertEquals("GROUP-B", groupB.getField().getName());
        assertEquals(1, groupB.getChildren().size());
        assertEquals("FIELD-B1", groupB.getChildren().getFirst().getField().getName());

        FieldNode fieldC = root.getChildren().get(1);
        assertEquals("FIELD-C", fieldC.getField().getName());
        assertEquals(0, fieldC.getChildren().size());
    }

    @Test
    void buildTreeShouldHandleInputsWithoutLevelOne() {
        List<FieldDefinition> fields = List.of(
                group(5, "GROUP-A"),
                scalar(10, "FIELD-A1", "X(2)")
        );

        List<FieldNode> tree = FieldNode.buildTree(fields);

        assertEquals(1, tree.size());
        assertEquals("GROUP-A", tree.getFirst().getField().getName());
        assertEquals(1, tree.getFirst().getChildren().size());
    }

    private static FieldDefinition group(int level, String name) {
        FieldDefinition field = new FieldDefinition();
        field.setLevel(level);
        field.setName(name);
        return field;
    }

    private static FieldDefinition scalar(int level, String name, String picture) {
        FieldDefinition field = group(level, name);
        field.setPicture(picture);
        return field;
    }
}

