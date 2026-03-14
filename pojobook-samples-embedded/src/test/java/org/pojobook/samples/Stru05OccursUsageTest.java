package org.pojobook.samples;

import org.junit.jupiter.api.Test;
import org.pojobook.samples.generated.Stru05;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Stru05 with embedded serialization.
 * Demonstrates nested OCCURS handling with embedded methods.
 */
class Stru05OccursUsageTest {

    private final Logger log = LoggerFactory.getLogger(Stru05OccursUsageTest.class);

    private final Charset charset = Charset.forName("CP1047");

    @Test
    void testSerializeAndDeserializeStru05() throws Exception {
        // Given: A Stru04 record with nested OCCURS
        Stru05 original = new Stru05();
        for (int i = 0; i < original.getComArray1().length; i++) {
            for (int j = 0; j < original.getComArray1()[i].getComArray2().length; j++) {
                original.getComArray1()[i].getComArray2()[j].setComArray3("A,B,C,D,E".split(","));
            }
        }

        // When: Serialize to COBOL format using embedded method
        byte[] cobolData = original.serialize(charset);

        // Then: Should produce binary data
        assertNotNull(cobolData);
        assertTrue(cobolData.length > 0);

        // When: Deserialize back to Java using static method
        Stru05 deserialized = Stru05.deserialize(cobolData, charset);

        // Then: Should have correct structure
        assertNotNull(deserialized);
        assertNotNull(deserialized.getComArray1());
        assertEquals(3, deserialized.getComArray1().length);

        // Debug: Print the values to see what was deserialized
        for (int i = 0; i < deserialized.getComArray1().length; i++) {
            for (int j = 0; j < deserialized.getComArray1()[i].getComArray2().length; j++) {
                log.info("Original comArray3[{}][{}]: {}", i, j, Arrays.toString(original.getComArray1()[i].getComArray2()[j].getComArray3()));
                log.info("Deserialized comArray3[{}][{}]: {}", i, j, Arrays.toString(deserialized.getComArray1()[i].getComArray2()[j].getComArray3()));
            }
        }

        assertEquals(original, deserialized);
    }

}

