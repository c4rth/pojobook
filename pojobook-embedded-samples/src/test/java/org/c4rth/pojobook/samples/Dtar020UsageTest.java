package org.c4rth.pojobook.samples;

import org.c4rth.pojobook.samples.generated.Dtar020;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class Dtar020UsageTest {

    private final Charset charset = Charset.forName("CP1047");

    private static final String[][] EXPECTED_LINES = {
            {"63604808", "20", "40118", "170", "1", "4.87"},
            {"69684558", "20", "40118", "280", "1", "19.00"},
            {"69684558", "20", "40118", "280", "-1", "-19.00"},
            {"69694158", "20", "40118", "280", "1", "5.01"},
            {"62684671", "20", "40118", "685", "1", "69.99"},
            {"62684671", "20", "40118", "685", "-1", "-69.99"},
            {"61664713", "59", "40118", "335", "1", "17.99"},
            {"61664713", "59", "40118", "335", "-1", "-17.99"},
            {"61684613", "59", "40118", "335", "1", "12.99"},
            {"68634752", "59", "40118", "410", "1", "8.99"},
            {"60694698", "59", "40118", "620", "1", "3.99"},
            {"60664659", "59", "40118", "620", "1", "3.99"},
            {"60614487", "59", "40118", "878", "1", "5.95"},
            {"68654655", "166", "40118", "60", "1", "5.08"},
            {"69624033", "166", "40118", "80", "1", "18.19"},
            {"60604100", "166", "40118", "80", "1", "13.30"},
            {"68674560", "166", "40118", "170", "1", "5.99"},
    };

    @Test
    void testSerializeAndDeserialize() throws Exception {
        Dtar020 original = new Dtar020();
        original.setDtar020KeycodeNo("63604808");
        original.setDtar020StoreNo(20);
        original.setDtar020Date(40118);
        original.setDtar020DeptNo(170);
        original.setDtar020QtySold(1);
        original.setDtar020SalePrice(new BigDecimal("4.87"));

        byte[] cobolData = original.serialize(charset);

        // Then: Should produce binary data
        assertNotNull(cobolData);
        assertTrue(cobolData.length > 0);

        // When: Deserialize back to Java
        Dtar020 deserialized = Dtar020.deserialize(cobolData, charset);
        assertEquals(original.getDtar020SalePrice(), deserialized.getDtar020SalePrice());
        assertEquals(original.getDtar020KeycodeNo(), deserialized.getDtar020KeycodeNo());
        assertEquals(original.getDtar020StoreNo(), deserialized.getDtar020StoreNo());
        assertEquals(original.getDtar020Date(), deserialized.getDtar020Date());
        assertEquals(original.getDtar020DeptNo(), deserialized.getDtar020DeptNo());
        assertEquals(original.getDtar020QtySold(), deserialized.getDtar020QtySold());
        assertEquals(original, deserialized);
    }

    @Test
    void testDeserializeData() throws Exception {
        byte[] cobolData = Files.readAllBytes(Paths.get("src/test/resources/DTAR020_tst1.bin"));

        // Then: Should have data
        assertNotNull(cobolData);
        assertTrue(cobolData.length > 0);

        int recordSize = 27;
        int recordCount = cobolData.length / recordSize;

        // Verify we have the expected number of records
        assertEquals(EXPECTED_LINES.length, recordCount,
                "Expected " + EXPECTED_LINES.length + " records but found " + recordCount);

        // Deserialize each 27-byte record
        // TODO
        /*
        for (int i = 0; i < recordCount; i++) {
            byte[] recordData = new byte[recordSize];
            System.arraycopy(cobolData, i * recordSize, recordData, 0, recordSize);
            Dtar020 deserialized = pojoBook.deserialize(recordData, Dtar020.class);
            check(i, deserialized);
        }*/
    }

    private void check(int index, Dtar020 line) {
        String[] expected = EXPECTED_LINES[index];
        int idx = 0;

        assertEquals(expected[idx++], line.getDtar020KeycodeNo());
        assertEquals(expected[idx++], "" + line.getDtar020StoreNo());
        assertEquals(expected[idx++], "" + line.getDtar020Date());
        assertEquals(expected[idx++], "" + line.getDtar020DeptNo());
        assertEquals(expected[idx++], "" + line.getDtar020QtySold());
        assertEquals(expected[idx], "" + line.getDtar020SalePrice());
    }

}
