package org.c4rth.pojobook.samples;

import org.c4rth.pojobook.PojoBook;
import org.c4rth.pojobook.samples.generated.Stru04;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class Stru04OccursUsageTest {

    private final Logger log = LoggerFactory.getLogger(Stru04OccursUsageTest.class);

    private PojoBook pojoBook;
    private final Charset charset = Charset.forName("CP1047");

    @BeforeEach
    void setUp() {
        pojoBook = new PojoBook();
    }

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

        // When: Serialize to COBOL format
        byte[] cobolData = pojoBook.serialize(original, charset);

        String s1 = new String(cobolData);

        // Then: Should produce binary data
        assertNotNull(cobolData);
        assertTrue(cobolData.length > 0);

        // When: Deserialize back to Java
        Stru04 deserialized = pojoBook.deserialize(cobolData, Stru04.class, charset);

        // Then: Should match
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

