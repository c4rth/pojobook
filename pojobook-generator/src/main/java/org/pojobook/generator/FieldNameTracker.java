package org.pojobook.generator;

import org.pojobook.parser.FieldDefinition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class FieldNameTracker {
    private FieldNameState currentState = FieldNameState.empty();
    private final Deque<FieldNameState> history = new ArrayDeque<>();

    public String toUniqueFieldName(FieldDefinition field) {
        String key = uniqueKey(field);
        return currentState.cobolToJavaNameMap()
                .computeIfAbsent(key, ignored -> createUniqueName(field));
    }

    public String getJavaFieldName(FieldDefinition field) {
        return currentState.cobolToJavaNameMap()
                .getOrDefault(uniqueKey(field), NamingUtils.toCamelCase(field.getName()));
    }

    public void reset() {
        currentState = FieldNameState.empty();
        history.clear();
    }

    public void push() {
        history.push(currentState);
        currentState = FieldNameState.empty();
    }

    public void pop() {
        currentState = history.isEmpty() ? FieldNameState.empty() : history.pop();
    }

    private String createUniqueName(FieldDefinition field) {
        String baseName = NamingUtils.toCamelCase(field.getName());
        int count = currentState.fieldNameCounts().merge(baseName, 1, Integer::sum);
        return count == 1 ? baseName : baseName + count;
    }

    private static String uniqueKey(FieldDefinition field) {
        return field.getName() + "_" + field.getLineNumber();
    }

    private record FieldNameState(Map<String, Integer> fieldNameCounts,
                                  Map<String, String> cobolToJavaNameMap) {

        static FieldNameState empty() {
            return new FieldNameState(new HashMap<>(), new HashMap<>());
        }

    }
}
