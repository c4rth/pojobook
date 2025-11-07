package org.c4rth.pojobook.parser;

/**
 * Represents an 88-level condition name in a COBOL copybook.
 * 88-level items define condition names (boolean conditions) for a field.
 * Example:
 * <pre>
 * 05  STATUS-CODE         PIC X(2).
 *     88  STATUS-ACTIVE   VALUE 'AC'.
 *     88  STATUS-DELETED  VALUE 'DE'.
 *     88  STATUS-VALID    VALUES 'AC' 'IN' 'PE'.
 * </pre>
 */
public class ConditionName {
    private String name;
    private String[] values;
    private String parentFieldName;
    private int lineNumber;

    public ConditionName() {
    }

    public ConditionName(String name, String[] values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getValues() {
        return values;
    }

    public void setValues(String[] values) {
        this.values = values;
    }

    public String getParentFieldName() {
        return parentFieldName;
    }

    public void setParentFieldName(String parentFieldName) {
        this.parentFieldName = parentFieldName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * Check if a value matches this condition.
     */
    public boolean matches(String value) {
        if (value == null || values == null) {
            return false;
        }

        for (String condValue : values) {
            if (value.equals(condValue) || value.trim().equals(condValue.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ConditionName{" +
                "name='" + name + '\'' +
                ", values=" + java.util.Arrays.toString(values) +
                ", parentField='" + parentFieldName + '\'' +
                '}';
    }
}

