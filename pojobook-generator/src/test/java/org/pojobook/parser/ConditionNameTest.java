package org.pojobook.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for 88-level condition name support.
 */
public class ConditionNameTest {

    @Test
    void testSingleConditionValue() throws Exception {
        String copybook = """
                   01  EMPLOYEE-RECORD.
                       05  STATUS-CODE         PIC X(2).
                           88  STATUS-ACTIVE   VALUE 'AC'.
                           88  STATUS-DELETED  VALUE 'DE'.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        // Find the STATUS-CODE field
        FieldDefinition statusField = fields.stream()
                .filter(f -> "STATUS-CODE".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(statusField);
        assertTrue(statusField.hasConditionNames());
        assertEquals(2, statusField.getConditionNames().size());

        // Check first condition
        ConditionName activeCondition = statusField.getConditionNames().getFirst();
        assertEquals("STATUS-ACTIVE", activeCondition.getName());
        assertEquals("STATUS-CODE", activeCondition.getParentFieldName());
        assertArrayEquals(new String[]{"AC"}, activeCondition.getValues());
        assertTrue(activeCondition.matches("AC"));
        assertFalse(activeCondition.matches("DE"));

        // Check second condition
        ConditionName deletedCondition = statusField.getConditionNames().get(1);
        assertEquals("STATUS-DELETED", deletedCondition.getName());
        assertArrayEquals(new String[]{"DE"}, deletedCondition.getValues());
    }

    @Test
    void testMultipleConditionValues() throws Exception {
        String copybook = """
                   01  TRANSACTION-RECORD.
                       05  TRANS-TYPE          PIC X(2).
                           88  TRANS-VALID     VALUES 'AC' 'IN' 'PE'.
                           88  TRANS-INVALID   VALUE 'XX'.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition transField = fields.stream()
                .filter(f -> "TRANS-TYPE".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(transField);
        assertEquals(2, transField.getConditionNames().size());

        // Check multiple values condition
        ConditionName validCondition = transField.getConditionNames().getFirst();
        assertEquals("TRANS-VALID", validCondition.getName());
        assertEquals(3, validCondition.getValues().length);
        assertArrayEquals(new String[]{"AC", "IN", "PE"}, validCondition.getValues());

        // Test matching
        assertTrue(validCondition.matches("AC"));
        assertTrue(validCondition.matches("IN"));
        assertTrue(validCondition.matches("PE"));
        assertFalse(validCondition.matches("XX"));
    }

    @Test
    void testConditionWithSpaces() throws Exception {
        String copybook = """
                   01  MENU-RECORD.
                       05  MENU-OPTION         PIC X(8).
                           88  OPTION-CONSULT  VALUE 'CONSULT '.
                           88  OPTION-UPDATE   VALUE 'UPDATE  '.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition menuField = fields.stream()
                .filter(f -> "MENU-OPTION".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(menuField);
        assertEquals(2, menuField.getConditionNames().size());

        ConditionName consultCondition = menuField.getConditionNames().getFirst();
        assertEquals("OPTION-CONSULT", consultCondition.getName());
        assertArrayEquals(new String[]{"CONSULT "}, consultCondition.getValues());

        // Test matching with trimming
        assertTrue(consultCondition.matches("CONSULT "));
        assertTrue(consultCondition.matches("CONSULT")); // Should match trimmed version
    }

    @Test
    void testRealWorldCopybook() throws Exception {
        CopybookDefinition definition = getCopybookDefinition();

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition functionField = fields.stream()
                .filter(f -> "NM-FUNCTION".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(functionField);
        assertTrue(functionField.hasConditionNames());
        assertEquals(3, functionField.getConditionNames().size());

        // Verify all conditions
        List<ConditionName> conditions = functionField.getConditionNames();
        assertEquals("CONSULT", conditions.get(0).getName());
        assertEquals("UPDATE", conditions.get(1).getName());
        assertEquals("DELETE", conditions.get(2).getName());

        // Verify parent field
        for (ConditionName condition : conditions) {
            assertEquals("NM-FUNCTION", condition.getParentFieldName());
        }
    }

    private static CopybookDefinition getCopybookDefinition() throws ParseException {
        String copybook = """
                   05  WS-SAMPLE01-INPUT.
                     10  WS-COMMON-INPUT.
                       20  NM-CALLER                PIC X(0008).
                       20  NM-BB                    PIC X(0008).
                       20  CO-TRANSMISSION          PIC X(0001).
                       20  NM-FUNCTION              PIC X(0008).
                         88  CONSULT                VALUE 'CONSULT '.
                         88  UPDATE                 VALUE 'UPDATE  '.
                         88  DELETE                 VALUE 'DELETE  '.
                """;

        CopybookParser parser = new CopybookParser();
        return parser.parse(copybook);
    }

    @Test
    void testConditionNamesNotLinkedToFiller() throws Exception {
        String copybook = """
                   01  TEST-RECORD.
                       05  FILLER              PIC X(2).
                           88  SHOULD-BE-IGNORED VALUE 'XX'.
                       05  REAL-FIELD          PIC X(2).
                           88  REAL-CONDITION  VALUE 'YY'.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        // FILLER should not have conditions linked
        FieldDefinition fillerField = fields.stream()
                .filter(FieldDefinition::isFiller)
                .findFirst()
                .orElse(null);

        assertNotNull(fillerField);
        assertFalse(fillerField.hasConditionNames() && !fillerField.getConditionNames().isEmpty());

        // Real field should have condition
        FieldDefinition realField = fields.stream()
                .filter(f -> "REAL-FIELD".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(realField);
        assertTrue(realField.hasConditionNames());
        assertEquals(1, realField.getConditionNames().size());
    }
}

