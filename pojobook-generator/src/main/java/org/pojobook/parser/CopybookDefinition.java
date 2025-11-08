package org.pojobook.parser;

import java.util.List;

/**
 * Represents a parsed COBOL copybook definition.
 */
public class CopybookDefinition {
    private final List<FieldDefinition> fields;
    private String recordName;

    public CopybookDefinition(List<FieldDefinition> fields) {
        this.fields = fields;
    }

    public List<FieldDefinition> getFields() {
        return fields;
    }

    public String getRecordName() {
        return recordName;
    }

    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

}

