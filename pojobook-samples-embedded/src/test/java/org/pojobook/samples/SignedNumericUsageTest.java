package org.pojobook.samples;

import org.junit.jupiter.api.Test;
import org.pojobook.samples.generated.SignedNumeric;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SignedNumericUsageTest {

    private final Charset charset = Charset.forName("CP1047");

    @Test
    void testUsage() throws Exception {

        SignedNumeric original = new SignedNumeric();
        original.setAttrX(new BigDecimal("199999.7770"));
        original.setAttrY(new BigDecimal("-12.34"));
        original.setAttrA(1);
        original.setAttrB(2);
        original.setAttrC(3);
        original.setAttrD(-4);
        original.setAttrE(-5);
        original.setAttrF(-6);
        original.setAttrG(-7);
        original.setAttrH(-8);
        original.setAttrI(-9);
        original.setAttrJ(-10);

        byte[] cobolData = original.serialize(charset);

        String x = new String(cobolData, charset);

        SignedNumeric deserialized = SignedNumeric.deserialize(cobolData, charset);

        assertEquals(new BigDecimal("199999.7770"), deserialized.getAttrX());
        assertEquals(new BigDecimal("-12.34"), deserialized.getAttrY());
        assertEquals(1, deserialized.getAttrA());
        assertEquals(2, deserialized.getAttrB());
        assertEquals(3, deserialized.getAttrC());
        assertEquals(-4, deserialized.getAttrD());
        assertEquals(-5, deserialized.getAttrE());
        assertEquals(-6, deserialized.getAttrF());
        assertEquals(-7, deserialized.getAttrG());
        assertEquals(-8, deserialized.getAttrH());
        assertEquals(-9, deserialized.getAttrI());
        assertEquals(-10, deserialized.getAttrJ());

    }

    @Test
    void testDeserializeData() throws Exception {
        byte[] cobolData = Files.readAllBytes(Paths.get("src/test/resources/signed-numeric.txt"));

        // Then: Should have data
        assertNotNull(cobolData);
        assertTrue(cobolData.length > 0);

        String x = new String(cobolData);

        SignedNumeric deserialized = SignedNumeric.deserialize(cobolData, StandardCharsets.ISO_8859_1);

        assertEquals(new BigDecimal("199999.7770"), deserialized.getAttrX());
        assertEquals(new BigDecimal("-12.34"), deserialized.getAttrY());
        assertEquals(1, deserialized.getAttrA());
        assertEquals(2, deserialized.getAttrB());
        assertEquals(3, deserialized.getAttrC());
        assertEquals(-4, deserialized.getAttrD());
        assertEquals(-5, deserialized.getAttrE());
        assertEquals(-6, deserialized.getAttrF());
        assertEquals(-7, deserialized.getAttrG());
        assertEquals(-8, deserialized.getAttrH());
        assertEquals(-9, deserialized.getAttrI());
        assertEquals(-10, deserialized.getAttrJ());

    }

}

