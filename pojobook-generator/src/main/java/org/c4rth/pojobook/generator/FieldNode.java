package org.c4rth.pojobook.generator;

import org.c4rth.pojobook.parser.FieldDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a field node in a hierarchical tree structure.
 * Used to build a tree from the flat field list for proper nested class generation.
 */
public class FieldNode {
    private final FieldDefinition field;
    private final List<FieldNode> children = new ArrayList<>();
    private FieldNode parent;

    public FieldNode(FieldDefinition field) {
        this.field = field;
    }

    public FieldDefinition getField() {
        return field;
    }

    public List<FieldNode> getChildren() {
        return children;
    }

    public void addChild(FieldNode child) {
        children.add(child);
        child.parent = this;
    }

    public FieldNode getParent() {
        return parent;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public boolean hasOccurs() {
        return field.getOccurs() > 1;
    }

    public boolean isGroup() {
        return field.isGroup();
    }

    /**
     * Build a tree structure from a flat list of fields.
     * Only processes fields from the first 01-level record.
     */
    public static List<FieldNode> buildTree(List<FieldDefinition> fields) {
        List<FieldNode> roots = new ArrayList<>();
        List<FieldNode> stack = new ArrayList<>();
        boolean foundFirst01 = false;

        for (FieldDefinition field : fields) {
            int level = field.getLevel();

            // Stop at the second 01-level record
            if (level == 1) {
                if (foundFirst01) {
                    break; // Stop processing at second 01-level
                }
                foundFirst01 = true;
            }

            FieldNode node = new FieldNode(field);

            // Pop stack until we find the parent level
            while (!stack.isEmpty() && stack.getLast().getField().getLevel() >= level) {
                stack.removeLast();
            }

            // Add to parent or as root
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.getLast().addChild(node);
            }

            // Push current node to stack (only if it could be a parent - group fields)
            if (field.isGroup()) {
                stack.add(node);
            }
        }

        return roots;
    }
}

