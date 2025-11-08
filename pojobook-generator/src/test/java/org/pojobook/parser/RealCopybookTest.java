package org.pojobook.parser;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test parsing the real copybook.cbl file with 88-level support.
 */
public class RealCopybookTest {

    private static final Logger log = LoggerFactory.getLogger(RealCopybookTest.class);

    @Test
    void testParseCopybookFile() throws Exception {
        CopybookParser parser = new CopybookParser();
        Path copybookPath = Paths.get("src/test/resources/copybook.cbl");

        CopybookDefinition definition = parser.parse(copybookPath);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();
        assertFalse(fields.isEmpty());

        // Find NM-FUNCTION field which has 88-level condition
        FieldDefinition nmFunction = fields.stream()
                .filter(f -> "NM-FUNCTION".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(nmFunction, "NM-FUNCTION field should be found");

        // Verify it has the CONSULT condition
        assertTrue(nmFunction.hasConditionNames(), "NM-FUNCTION should have condition names");

        List<ConditionName> conditions = nmFunction.getConditionNames();
        assertFalse(conditions.isEmpty(), "Should have at least one condition");

        // Find CONSULT condition
        ConditionName consultCondition = conditions.stream()
                .filter(c -> "CONSULT".equals(c.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(consultCondition, "CONSULT condition should exist");
        assertEquals("NM-FUNCTION", consultCondition.getParentFieldName());

        // Verify the value
        assertNotNull(consultCondition.getValues());
        assertTrue(consultCondition.getValues().length > 0);
        assertEquals("CONSULT ", consultCondition.getValues()[0]);

        // Test matching
        assertTrue(consultCondition.matches("CONSULT "));
        assertTrue(consultCondition.matches("CONSULT")); // Should match trimmed
        assertFalse(consultCondition.matches("UPDATE"));

        log.info("=== Copybook Parse Results ===");
        log.info("Total fields parsed: {}", fields.size());
        log.info("Fields with condition names:");
        for (FieldDefinition field : fields) {
            if (field.hasConditionNames()) {
                log.info("  {}:", field.getName());
                for (ConditionName cond : field.getConditionNames()) {
                    log.info("    88 {} VALUE {}", cond.getName(), String.join(" ", cond.getValues()));
                }
            }
        }
    }
}

