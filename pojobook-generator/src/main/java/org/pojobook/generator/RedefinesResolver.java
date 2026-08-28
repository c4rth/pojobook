package org.pojobook.generator;

import org.pojobook.parser.FieldDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves COBOL REDEFINES relationships that are declared on GROUP items down to an actual
 * leaf (or array) field, so that the annotation-based (reflection) runtime - which can only
 * carry {@code redefines} metadata on a real Java field - can correctly share storage between
 * alternate views.
 * <p>
 * In COBOL it is common for a REDEFINES clause to target (or be declared on) a group item that
 * never becomes a physical Java field on its own (because groups with a single occurrence are
 * flattened into their parent class). For example:
 * <pre>
 *     40 deliveryMethod          PIC X(980).
 *     40 smsDeliveryMethod       REDEFINES deliveryMethod.
 *        45 smsFrom              PIC X(200).
 *        45 mobileNumber         PIC X(20).
 * </pre>
 * Here {@code smsDeliveryMethod} carries the REDEFINES clause, but only its first leaf child
 * ({@code smsFrom}) survives as a real field. This resolver computes, for every group that
 * redefines something, the effective REDEFINES target to attach to that group's first leaf
 * descendant - resolving chained/group-to-group references (e.g. a group redefining another
 * group) down to the ultimate leaf field name in the process.
 */
public final class RedefinesResolver {

    private RedefinesResolver() {
    }

    /**
     * Compute the effective REDEFINES target (COBOL field name) that should be attached to each
     * leaf/array field's generated annotation, for a given class scope (top-level record or a
     * single nested class' children).
     *
     * @param nodes the sibling field nodes making up one generated class' scope
     * @return a map from {@link FieldDefinition} to the resolved REDEFINES target name; fields not
     * present in the map have no REDEFINES relationship to encode
     */
    public static Map<FieldDefinition, String> resolve(List<FieldNode> nodes) {
        Map<String, String> nameToLeafName = new HashMap<>();
        collectLeafNames(nodes, nameToLeafName);

        Map<FieldDefinition, String> effective = new HashMap<>();
        propagate(nodes, nameToLeafName, effective);
        return effective;
    }

    /**
     * Build a map of every declared name (leaf or group) to the name of its first leaf
     * descendant (itself, if it is already a leaf).
     */
    private static void collectLeafNames(List<FieldNode> nodes, Map<String, String> nameToLeafName) {
        for (FieldNode node : nodes) {
            FieldDefinition field = node.getField();
            if (field.getLevel() == 88) {
                continue;
            }

            if (field.isGroup() && !node.getChildren().isEmpty()) {
                collectLeafNames(node.getChildren(), nameToLeafName);
                String leafName = firstLeafName(node);
                if (field.getName() != null && leafName != null) {
                    nameToLeafName.put(normalize(field.getName()), leafName);
                }
            } else if (field.getName() != null) {
                nameToLeafName.putIfAbsent(normalize(field.getName()), field.getName());
            }
        }
    }

    /**
     * Propagate each group's (resolved) REDEFINES target onto its first leaf descendant.
     */
    private static void propagate(List<FieldNode> nodes, Map<String, String> nameToLeafName,
                                  Map<FieldDefinition, String> effective) {
        for (FieldNode node : nodes) {
            FieldDefinition field = node.getField();
            if (field.getLevel() == 88) {
                continue;
            }

            String resolvedTarget = resolveTarget(field.getRedefines(), nameToLeafName);

            if (field.isGroup() && !node.getChildren().isEmpty()) {
                if (resolvedTarget != null) {
                    FieldNode firstLeaf = firstLeafNode(node);
                    if (firstLeaf != null) {
                        effective.putIfAbsent(firstLeaf.getField(), resolvedTarget);
                    }
                }
                propagate(node.getChildren(), nameToLeafName, effective);
            } else if (resolvedTarget != null) {
                effective.putIfAbsent(field, resolvedTarget);
            }
        }
    }

    private static String resolveTarget(String redefines, Map<String, String> nameToLeafName) {
        if (redefines == null || redefines.isEmpty()) {
            return null;
        }
        return nameToLeafName.getOrDefault(normalize(redefines), redefines);
    }

    private static String firstLeafName(FieldNode groupNode) {
        FieldNode leaf = firstLeafNode(groupNode);
        return leaf == null ? null : leaf.getField().getName();
    }

    /**
     * Find the first non-88 leaf (or array) descendant of a group node, recursing through any
     * nested flattened groups.
     */
    private static FieldNode firstLeafNode(FieldNode groupNode) {
        for (FieldNode child : groupNode.getChildren()) {
            FieldDefinition field = child.getField();
            if (field.getLevel() == 88) {
                continue;
            }
            if (field.isGroup() && !child.getChildren().isEmpty()) {
                FieldNode leaf = firstLeafNode(child);
                if (leaf != null) {
                    return leaf;
                }
            } else {
                return child;
            }
        }
        return null;
    }

    private static String normalize(String name) {
        return name.toUpperCase(Locale.ROOT);
    }
}

