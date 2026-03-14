package org.pojobook.generator;

import org.pojobook.parser.FieldDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Field node in a hierarchical tree structure.
 */
public final class FieldNode {
    private final FieldDefinition field;
    private final List<FieldNode> children;


    private FieldNode(FieldDefinition field, List<FieldNode> children, boolean copyChildren) {
        this.field = field;
        this.children = copyChildren ? List.copyOf(children) : children;
    }

    public FieldDefinition getField() {
        return field;
    }

    public List<FieldNode> getChildren() {
        return children;
    }

    /**
     * Build a tree structure from a flat list of fields.
     * Only processes fields from the first 01-level record.
     */
    public static List<FieldNode> buildTree(List<FieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }

        List<MutableNode> roots = new ArrayList<>();
        Deque<MutableNode> stack = new ArrayDeque<>();
        boolean foundFirst01 = false;

        for (FieldDefinition field : fields) {
            if (field.getLevel() == 1) {
                if (foundFirst01) {
                    break; // Stop at second 01-level
                }
                foundFirst01 = true;
            }

            // Pop stack to find correct parent level
            popStackUntilParent(stack, field.getLevel());

            // Create mutable node
            MutableNode mutableNode = new MutableNode(field);

            // Add to parent or as root
            if (stack.isEmpty()) {
                roots.add(mutableNode);
            } else {
                stack.getLast().addChild(mutableNode);
            }

            // Push current node to stack if it's a group
            if (field.isGroup()) {
                stack.addLast(mutableNode);
            }
        }

        // Convert mutable tree to immutable
        List<FieldNode> immutableRoots = new ArrayList<>(roots.size());
        for (MutableNode root : roots) {
            immutableRoots.add(root.toImmutable());
        }
        return List.copyOf(immutableRoots);
    }

    /**
     * Pop stack until we find the appropriate parent level.
     */
    private static void popStackUntilParent(Deque<MutableNode> stack, int currentLevel) {
        while (!stack.isEmpty() && stack.getLast().field.getLevel() >= currentLevel) {
            stack.removeLast();
        }
    }

    /**
     * Mutable helper class for building node structure.
     * Used only during tree construction phase.
     */
    private static class MutableNode {
        private final FieldDefinition field;
        private final List<MutableNode> children = new ArrayList<>();

        MutableNode(FieldDefinition field) {
            this.field = field;
        }

        void addChild(MutableNode child) {
            children.add(child);
        }

        FieldNode toImmutable() {
            List<FieldNode> immutableChildren = new ArrayList<>(children.size());
            for (MutableNode child : children) {
                immutableChildren.add(child.toImmutable());
            }
            return new FieldNode(field, List.copyOf(immutableChildren), false);
        }
    }
}
