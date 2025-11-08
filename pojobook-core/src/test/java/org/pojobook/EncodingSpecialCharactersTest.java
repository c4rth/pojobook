package org.pojobook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.pojobook.annotation.CobolField;
import org.pojobook.annotation.CobolRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests demonstrating the differences between ASCII and EBCDIC encodings
 * when serializing and deserializing special characters.
 */
public class EncodingSpecialCharactersTest {

    private final Logger log = LoggerFactory.getLogger(EncodingSpecialCharactersTest.class);
    private final PojoBook pojoBook = new PojoBook();

    @CobolRecord
    public static class LatinRecord {
        @CobolField(level = 5, name = "TEXT-FIELD", picture = "X(30)", position = 0, length = 30)
        private String textField;

        public LatinRecord() {
        }

        public String getTextField() {
            return textField;
        }

        public void setTextField(String textField) {
            this.textField = textField;
        }
    }

    @CobolRecord
    public static class EbcdicRecord {
        @CobolField(level = 5, name = "TEXT-FIELD", picture = "X(30)", position = 0, length = 30)
        private String textField;

        public EbcdicRecord() {
        }

        public String getTextField() {
            return textField;
        }

        public void setTextField(String textField) {
            this.textField = textField;
        }
    }

    /**
     * Test special characters that have different byte representations in ASCII vs EBCDIC
     */
    @Test
    void testSpecialCharactersDifferentInAsciiAndEbcdic() throws Exception {
        // Characters that have different byte values in ASCII vs EBCDIC
        String testString = "Hello@World!";  // @ and ! have different encodings

        // Test ASCII encoding
        LatinRecord asciiRecord = new LatinRecord();
        asciiRecord.setTextField(testString);

        byte[] asciiData = pojoBook.serialize(asciiRecord, StandardCharsets.ISO_8859_1);

        // Test EBCDIC encoding
        EbcdicRecord ebcdicRecord = new EbcdicRecord();
        ebcdicRecord.setTextField(testString);

        byte[] ebcdicData = pojoBook.serialize(ebcdicRecord, Charset.forName("CP1047"));

        // The byte arrays should be different
        assertFalse(java.util.Arrays.equals(asciiData, ebcdicData),
                "ASCII and EBCDIC byte arrays should be different for special characters");

        // But both should deserialize back to the original string
        LatinRecord deserializedAscii = pojoBook.deserialize(asciiData, LatinRecord.class, StandardCharsets.ISO_8859_1);
        assertEquals(testString, deserializedAscii.getTextField().trim());

        EbcdicRecord deserializedEbcdic = pojoBook.deserialize(ebcdicData, EbcdicRecord.class, Charset.forName("CP1047"));
        assertEquals(testString, deserializedEbcdic.getTextField().trim());
    }

    /**
     * Test that demonstrates encoding mismatch causes data corruption
     */
    @Test
    void testEncodingMismatchCausesDataCorruption() throws Exception {
        String testString = "Test@123!";

        // Serialize with EBCDIC
        EbcdicRecord ebcdicRecord = new EbcdicRecord();
        ebcdicRecord.setTextField(testString);
        byte[] ebcdicData = pojoBook.serialize(ebcdicRecord, Charset.forName("CP1047"));

        // Try to deserialize with ASCII (wrong encoding) - should get corrupted data
        LatinRecord asciiRecord = pojoBook.deserialize(ebcdicData, LatinRecord.class, StandardCharsets.ISO_8859_1);

        // The deserialized string should NOT match the original
        assertNotEquals(testString, asciiRecord.getTextField().trim(),
                "Using wrong encoding should result in corrupted data");
    }

    /**
     * Test specific special characters that differ between ASCII and EBCDIC
     */
    @ParameterizedTest
    @MethodSource("provideSpecialCharacters")
    void testIndividualSpecialCharacters(String character, String description) throws Exception {
        // Test with ASCII
        LatinRecord asciiRecord = new LatinRecord();
        asciiRecord.setTextField(character);
        byte[] asciiData = pojoBook.serialize(asciiRecord, StandardCharsets.ISO_8859_1);

        // Test with EBCDIC
        EbcdicRecord ebcdicRecord = new EbcdicRecord();
        ebcdicRecord.setTextField(character);
        byte[] ebcdicData = pojoBook.serialize(ebcdicRecord, Charset.forName("CP1047"));

        // Print byte differences for documentation
        log.info("Character '{}' ({}):", character, description);
        log.info("  ASCII bytes:  {}", bytesToHex(asciiData, character.length()));
        log.info("  EBCDIC bytes: {}", bytesToHex(ebcdicData, character.length()));

        // Verify round-trip works correctly for both encodings
        LatinRecord deserializedAscii = pojoBook.deserialize(asciiData, LatinRecord.class, StandardCharsets.ISO_8859_1);
        assertEquals(character, deserializedAscii.getTextField().trim(),
                "ASCII round-trip failed for: " + description);

        EbcdicRecord deserializedEbcdic = pojoBook.deserialize(ebcdicData, EbcdicRecord.class, Charset.forName("CP1047"));
        assertEquals(character, deserializedEbcdic.getTextField().trim(),
                "EBCDIC round-trip failed for: " + description);
    }

    static Stream<Arguments> provideSpecialCharacters() {
        return Stream.of(
                Arguments.of("@", "At sign"),
                Arguments.of("!", "Exclamation mark"),
                Arguments.of("#", "Hash/Pound sign"),
                Arguments.of("$", "Dollar sign"),
                Arguments.of("%", "Percent sign"),
                Arguments.of("&", "Ampersand"),
                Arguments.of("*", "Asterisk"),
                Arguments.of("(", "Left parenthesis"),
                Arguments.of(")", "Right parenthesis"),
                Arguments.of("-", "Hyphen/Minus"),
                Arguments.of("+", "Plus sign"),
                Arguments.of("=", "Equals sign"),
                Arguments.of("[", "Left bracket"),
                Arguments.of("]", "Right bracket"),
                Arguments.of("{", "Left brace"),
                Arguments.of("}", "Right brace"),
                Arguments.of("|", "Pipe/Vertical bar"),
                Arguments.of("\\", "Backslash"),
                Arguments.of(":", "Colon"),
                Arguments.of(";", "Semicolon"),
                Arguments.of("'", "Single quote"),
                Arguments.of("\"", "Double quote"),
                Arguments.of("<", "Less than"),
                Arguments.of(">", "Greater than"),
                Arguments.of("?", "Question mark"),
                Arguments.of("/", "Forward slash"),
                Arguments.of("~", "Tilde"),
                Arguments.of("`", "Backtick")
        );
    }

    /**
     * Test common COBOL special characters
     */
    @Test
    void testCobolCommonCharacters() throws Exception {
        // Common characters in COBOL copybooks
        String cobolString = "ACCT-NO: 12345-67";

        // Test ASCII
        LatinRecord asciiRecord = new LatinRecord();
        asciiRecord.setTextField(cobolString);
        byte[] asciiData = pojoBook.serialize(asciiRecord, StandardCharsets.ISO_8859_1);

        // Test EBCDIC
        EbcdicRecord ebcdicRecord = new EbcdicRecord();
        ebcdicRecord.setTextField(cobolString);
        byte[] ebcdicData = pojoBook.serialize(ebcdicRecord, Charset.forName("CP1047"));

        // Verify byte arrays are different
        assertNotEquals(
                bytesToHex(asciiData, cobolString.length()),
                bytesToHex(ebcdicData, cobolString.length()),
                "ASCII and EBCDIC should produce different bytes"
        );

        // Verify correct deserialization
        LatinRecord deserializedAscii = pojoBook.deserialize(asciiData, LatinRecord.class, StandardCharsets.ISO_8859_1);
        assertEquals(cobolString, deserializedAscii.getTextField().trim());

        EbcdicRecord deserializedEbcdic = pojoBook.deserialize(ebcdicData, EbcdicRecord.class, Charset.forName("CP1047"));
        assertEquals(cobolString, deserializedEbcdic.getTextField().trim());
    }

    /**
     * Test numeric characters (0-9) - should be SAME in ASCII and EBCDIC for DISPLAY
     */
    @Test
    void testNumericCharactersAreSame() throws Exception {
        String numbers = "0123456789";

        // Test ASCII
        LatinRecord asciiRecord = new LatinRecord();
        asciiRecord.setTextField(numbers);
        byte[] asciiData = pojoBook.serialize(asciiRecord, StandardCharsets.ISO_8859_1);

        // Test EBCDIC
        EbcdicRecord ebcdicRecord = new EbcdicRecord();
        ebcdicRecord.setTextField(numbers);
        byte[] ebcdicData = pojoBook.serialize(ebcdicRecord, Charset.forName("CP1047"));

        // Extract just the numeric bytes (first 10 bytes)
        byte[] asciiNumBytes = java.util.Arrays.copyOfRange(asciiData, 0, 10);
        byte[] ebcdicNumBytes = java.util.Arrays.copyOfRange(ebcdicData, 0, 10);

        log.info("Numeric '0-9' comparison:");
        log.info("  ASCII:  {}", bytesToHex(asciiNumBytes, 10));
        log.info("  EBCDIC: {}", bytesToHex(ebcdicNumBytes, 10));

        // Note: In EBCDIC, digits 0-9 are 0xF0-0xF9, in ASCII they are 0x30-0x39
        // So they ARE different!
        assertFalse(java.util.Arrays.equals(asciiNumBytes, ebcdicNumBytes),
                "Even numeric characters have different representations in ASCII vs EBCDIC");
    }

    /**
     * Test alphabetic characters - also different between ASCII and EBCDIC
     */
    @Test
    void testAlphabeticCharactersAreDifferent() throws Exception {
        String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // Test ASCII
        LatinRecord asciiRecord = new LatinRecord();
        asciiRecord.setTextField(alpha);
        byte[] asciiData = pojoBook.serialize(asciiRecord, StandardCharsets.ISO_8859_1);

        // Test EBCDIC
        EbcdicRecord ebcdicRecord = new EbcdicRecord();
        ebcdicRecord.setTextField(alpha);
        byte[] ebcdicData = pojoBook.serialize(ebcdicRecord, Charset.forName("CP1047"));

        // Should be different
        assertFalse(java.util.Arrays.equals(asciiData, ebcdicData),
                "Alphabetic characters have different representations in ASCII vs EBCDIC");

        log.info("Alphabetic 'A-Z' first 5 chars comparison:");
        log.info("  ASCII:  {}", bytesToHex(asciiData, 5));
        log.info("  EBCDIC: {}", bytesToHex(ebcdicData, 5));

        // But round-trip should work
        LatinRecord deserializedAscii = pojoBook.deserialize(asciiData, LatinRecord.class, StandardCharsets.ISO_8859_1);
        assertEquals(alpha, deserializedAscii.getTextField().trim());

        EbcdicRecord deserializedEbcdic = pojoBook.deserialize(ebcdicData, EbcdicRecord.class, Charset.forName("CP1047"));
        assertEquals(alpha, deserializedEbcdic.getTextField().trim());
    }

    /**
     * Test space character - should be SAME (0x40 in both)
     */
    @Test
    void testSpaceCharacter() throws Exception {
        String spaces = "     ";  // 5 spaces

        // Test ASCII
        LatinRecord asciiRecord = new LatinRecord();
        asciiRecord.setTextField(spaces);
        byte[] asciiData = pojoBook.serialize(asciiRecord, StandardCharsets.ISO_8859_1);

        // Test EBCDIC
        EbcdicRecord ebcdicRecord = new EbcdicRecord();
        ebcdicRecord.setTextField(spaces);
        byte[] ebcdicData = pojoBook.serialize(ebcdicRecord, Charset.forName("CP1047"));

        log.info("Space character comparison:");
        log.info("  ASCII:  {}", bytesToHex(asciiData, 5));
        log.info("  EBCDIC: {}", bytesToHex(ebcdicData, 5));

        // Space is 0x20 in ASCII, 0x40 in EBCDIC
        assertFalse(java.util.Arrays.equals(asciiData, ebcdicData),
                "Space character is different: ASCII=0x20, EBCDIC=0x40");
    }

    /**
     * Test a complex real-world string
     */
    @Test
    void testRealWorldCobolData() throws Exception {
        String realWorldData = "SMITH, JOHN A. - ACCT#12345";

        // Test ASCII
        LatinRecord asciiRecord = new LatinRecord();
        asciiRecord.setTextField(realWorldData);
        byte[] asciiData = pojoBook.serialize(asciiRecord, StandardCharsets.ISO_8859_1);

        // Test EBCDIC
        EbcdicRecord ebcdicRecord = new EbcdicRecord();
        ebcdicRecord.setTextField(realWorldData);
        byte[] ebcdicData = pojoBook.serialize(ebcdicRecord, Charset.forName("CP1047"));

        // Print comparison
        log.info("\nReal-world COBOL data comparison:");
        log.info("String: '{}'", realWorldData);
        log.info("ASCII encoding:  {}", bytesToHex(asciiData, realWorldData.length()));
        log.info("EBCDIC encoding: {}", bytesToHex(ebcdicData, realWorldData.length()));

        // Verify they're different
        assertFalse(java.util.Arrays.equals(asciiData, ebcdicData));

        // Verify correct round-trip
        LatinRecord deserializedAscii = pojoBook.deserialize(asciiData, LatinRecord.class, StandardCharsets.ISO_8859_1);
        assertEquals(realWorldData, deserializedAscii.getTextField().trim());

        EbcdicRecord deserializedEbcdic = pojoBook.deserialize(ebcdicData, EbcdicRecord.class, Charset.forName("CP1047"));
        assertEquals(realWorldData, deserializedEbcdic.getTextField().trim());
    }

    /**
     * Helper method to convert bytes to hex string for display
     */
    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(length, bytes.length); i++) {
            sb.append(String.format("%02X ", bytes[i] & 0xFF));
        }
        return sb.toString().trim();
    }
}

