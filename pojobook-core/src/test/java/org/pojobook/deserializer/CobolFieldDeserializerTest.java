package org.pojobook.deserializer;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CobolFieldDeserializerTest {

    @Test
    void shouldDeserializeUnsignedDisplayIntegerInCp1047() {
        Charset cp1047 = Charset.forName("CP1047");
        byte[] data = "   12345".getBytes(cp1047);

        Integer value = CobolFieldDeserializer.deserializeDisplayInteger(data, 0, data.length, cp1047);

        assertEquals(12345, value);
    }

    @Test
    void shouldDeserializeUnsignedDisplayLongInAscii() {
        byte[] data = "9876543210".getBytes(StandardCharsets.US_ASCII);

        Long value = CobolFieldDeserializer.deserializeDisplayLong(data, 0, data.length, StandardCharsets.US_ASCII);

        assertEquals(9_876_543_210L, value);
    }

    @Test
    void shouldDeserializeUnsignedDisplayShort() {
        byte[] data = "  123".getBytes(StandardCharsets.US_ASCII);

        Short value = CobolFieldDeserializer.deserializeDisplayShort(data, 0, data.length, StandardCharsets.US_ASCII);

        assertEquals((short) 123, value);
    }

    @Test
    void shouldReturnZeroForBlankDisplayValues() {
        Charset cp1047 = Charset.forName("CP1047");
        byte[] data = "      ".getBytes(cp1047);

        assertEquals(0, CobolFieldDeserializer.deserializeDisplayInteger(data, 0, data.length, cp1047));
        assertEquals(0L, CobolFieldDeserializer.deserializeDisplayLong(data, 0, data.length, cp1047));
        assertEquals((short) 0, CobolFieldDeserializer.deserializeDisplayShort(data, 0, data.length, cp1047));
    }

    @Test
    void shouldFailForNonDigitDisplayValues() {
        byte[] data = "12A4".getBytes(StandardCharsets.US_ASCII);

        assertThrows(NumberFormatException.class,
                () -> CobolFieldDeserializer.deserializeDisplayInteger(data, 0, data.length, StandardCharsets.US_ASCII));
    }
}

