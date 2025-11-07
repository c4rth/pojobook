package org.c4rth.pojobook;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CharsetDebugTest {

    private final Logger log = LoggerFactory.getLogger(CharsetDebugTest.class);

    @Test
    void testCharsetAvailability() {
        log.info("Testing charset availability:");
        log.info("Cp037 available: {}", Charset.isSupported("Cp037"));
        log.info("Cp1047 available: {}", Charset.isSupported("CP1047"));

        // Test actual encoding
        String test = "Hello@World!";

        Charset ascii = StandardCharsets.US_ASCII;
        Charset ebcdic = Charset.forName("Cp037");
        Charset cp1047 = Charset.forName("Cp1047");

        byte[] asciiBytes = test.getBytes(ascii);
        byte[] ebcdicBytes = test.getBytes(ebcdic);
        byte[] cp1047Bytes = test.getBytes(cp1047);

        log.info("Test string: {}", test);
        log.info("ASCII bytes:  {}", bytesToHex(asciiBytes));
        log.info("EBCDIC bytes: {}", bytesToHex(ebcdicBytes));
        log.info("CP1047 bytes: {}", bytesToHex(cp1047Bytes));

        // Show specific characters
        log.info("Character '@' (position 5):");
        log.info("  ASCII:  0x{}", String.format("%02X", asciiBytes[5] & 0xFF));
        log.info("  EBCDIC: 0x{}", String.format("%02X", ebcdicBytes[5] & 0xFF));
        log.info("  CP1047: 0x{}", String.format("%02X", cp1047Bytes[5] & 0xFF));

        log.info("Character 'H' (position 0):");
        log.info("  ASCII:  0x{}", String.format("%02X", asciiBytes[0] & 0xFF));
        log.info("  EBCDIC: 0x{}", String.format("%02X", ebcdicBytes[0] & 0xFF));
        log.info("  CP1047: 0x{}", String.format("%02X", cp1047Bytes[0] & 0xFF));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }
}

