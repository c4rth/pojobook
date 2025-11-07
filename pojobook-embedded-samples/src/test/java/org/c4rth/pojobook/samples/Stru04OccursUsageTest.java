package org.c4rth.pojobook.samples;

import org.c4rth.pojobook.samples.generated.Stru04;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Stru04 with embedded serialization.
 * Demonstrates nested OCCURS handling with embedded methods.
 */
class Stru04OccursUsageTest {

    private final Charset charset = Charset.forName("CP1047");

    private final Logger log = LoggerFactory.getLogger(Stru04OccursUsageTest.class);

    @Test
    void testSerializeAndDeserializeStru04() throws Exception {
        // Given: A Stru04 record with nested OCCURS
        Stru04 original = new Stru04();
        original.setComItem1(new java.math.BigDecimal("12345.67"));
        for (int i = 0; i < original.getComArray1().length; i++) {
            original.getComArray1()[i].setComItem2((short) (i + 1));
            for (int j = 0; j < original.getComArray1()[i].getComArray2().length; j++) {
                original.getComArray1()[i].getComArray2()[j].setComItem4("I");
                original.getComArray1()[i].getComArray2()[j].setComArray3("A,B,C,D,E".split(","));
                original.getComArray1()[i].getComArray2()[j].setComItem5(new BigDecimal("12345.67"));
            }
            original.getComArray1()[i].setComItem7(i + 200);
        }
        original.setComItem8(new java.math.BigDecimal("76543.21"));

        // When: Serialize to COBOL format using embedded method
        byte[] cobolData = original.serialize(charset);

        // Then: Should produce binary data
        assertNotNull(cobolData);
        assertTrue(cobolData.length > 0);

        // When: Deserialize back to Java using static method
        Stru04 deserialized = Stru04.deserialize(cobolData, charset);

        // Then: Should have correct structure
        assertNotNull(deserialized);
        assertNotNull(deserialized.getComArray1());
        assertEquals(3, deserialized.getComArray1().length);

        // Verify short values preserved
        assertEquals((short) 1, deserialized.getComArray1()[0].getComItem2());
        assertEquals((short) 2, deserialized.getComArray1()[1].getComItem2());
        assertEquals((short) 3, deserialized.getComArray1()[2].getComItem2());

        // Verify int values preserved
        assertEquals(Integer.valueOf(200), deserialized.getComArray1()[0].getComItem7());
        assertEquals(Integer.valueOf(201), deserialized.getComArray1()[1].getComItem7());
        assertEquals(Integer.valueOf(202), deserialized.getComArray1()[2].getComItem7());

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

