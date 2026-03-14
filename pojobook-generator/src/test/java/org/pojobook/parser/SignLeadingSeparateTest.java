package org.pojobook.parser;

import org.junit.jupiter.api.Test;
import org.pojobook.CobolDataType;
import org.pojobook.annotation.CobolField;
import org.pojobook.annotation.CobolRecord;
import org.pojobook.deserializer.CobolDeserializer;
import org.pojobook.serializer.CobolSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for SIGN LEADING SEPARATE and SIGN TRAILING SEPARATE functionality.
 */
class SignLeadingSeparateTest {

    private final Logger log = LoggerFactory.getLogger(SignLeadingSeparateTest.class);

    @CobolRecord
    static class SignLeadingRecord {
        @CobolField(level = 5, name = "AMOUNT-LEADING", picture = "S9(7)V99",
                type = CobolDataType.DISPLAY, integerDigits = 7, decimalDigits = 2,
                signed = true, signPosition = "LEADING", signSeparate = true)
        private BigDecimal amountLeading;

        public BigDecimal getAmountLeading() {
            return amountLeading;
        }

        public void setAmountLeading(BigDecimal amountLeading) {
            this.amountLeading = amountLeading;
        }
    }

    @CobolRecord
    static class SignTrailingRecord {
        @CobolField(level = 5, name = "AMOUNT-TRAILING", picture = "S9(7)V99",
                type = CobolDataType.DISPLAY, integerDigits = 7, decimalDigits = 2,
                signed = true, signPosition = "TRAILING", signSeparate = true)
        private BigDecimal amountTrailing;

        public BigDecimal getAmountTrailing() {
            return amountTrailing;
        }

        public void setAmountTrailing(BigDecimal amountTrailing) {
            this.amountTrailing = amountTrailing;
        }
    }

    @Test
    void testParseSignLeadingSeparate() throws Exception {
        String copybook = """
                   01  TEST-RECORD.
                       05  AMOUNT    PIC S9(7)V99 SIGN LEADING SEPARATE.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        List<FieldDefinition> fields = definition.getFields();
        FieldDefinition amountField = fields.stream()
                .filter(f -> "AMOUNT".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(amountField);
        assertTrue(amountField.isSigned());
        assertEquals("LEADING", amountField.getSignPosition());
        assertTrue(amountField.isSignSeparate());
        assertEquals(10, amountField.getByteLength()); // 9 digits + 1 sign
    }

    @Test
    void testParseSignTrailingSeparate() throws Exception {
        String copybook = """
                   01  TEST-RECORD.
                       05  AMOUNT    PIC S9(7)V99 SIGN TRAILING SEPARATE.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        List<FieldDefinition> fields = definition.getFields();
        FieldDefinition amountField = fields.stream()
                .filter(f -> "AMOUNT".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(amountField);
        assertTrue(amountField.isSigned());
        assertEquals("TRAILING", amountField.getSignPosition());
        assertTrue(amountField.isSignSeparate());
        assertEquals(10, amountField.getByteLength()); // 9 digits + 1 sign
    }

    @Test
    void testSerializePositiveAmountLeading() throws Exception {
        CobolSerializer serializer = new CobolSerializer();

        SignLeadingRecord record = new SignLeadingRecord();
        record.setAmountLeading(new BigDecimal("1234567.89"));

        byte[] data = serializer.serialize(record, Charset.forName("CP1047"));

        // Expected: sign + digits  (10 bytes total: 1 sign + 9 digits)
        assertEquals(10, data.length, "Should be 10 bytes (1 sign + 9 digits)");

        // Convert back to string to verify
        String dataStr = new String(data, Charset.forName("CP1047"));
        log.info("Serialized positive leading: [{}] length={}", dataStr, data.length);

        // The format should be: +123456789 (sign then 9 digits - 1234567.89 = 123456789 unscaled)
        assertEquals(10, dataStr.length());
        assertEquals('+', dataStr.charAt(0), "First character should be + sign");
        assertEquals("123456789", dataStr.substring(1), "Should be digits 123456789 (unscaled value)");
    }

    @Test
    void testSerializeNegativeAmountLeading() throws Exception {
        CobolSerializer serializer = new CobolSerializer();

        SignLeadingRecord record = new SignLeadingRecord();
        record.setAmountLeading(new BigDecimal("-1234567.89"));

        byte[] data = serializer.serialize(record, Charset.forName("CP1047"));

        // Expected: sign + digits
        assertEquals(10, data.length);

        // Decode and verify sign is negative
        String dataStr = new String(data, Charset.forName("CP1047"));
        log.info("Serialized negative leading: [{}]", dataStr);
        assertEquals('-', dataStr.charAt(0), "First character should be - sign");
    }

    @Test
    void testSerializePositiveAmountTrailing() throws Exception {
        CobolSerializer serializer = new CobolSerializer();

        SignTrailingRecord record = new SignTrailingRecord();
        record.setAmountTrailing(new BigDecimal("1234567.89"));

        byte[] data = serializer.serialize(record, Charset.forName("CP1047"));

        // Expected: digits + sign
        assertEquals(10, data.length);

        // Decode and verify sign is at end
        String dataStr = new String(data, Charset.forName("CP1047"));
        log.info("Serialized positive trailing: [{}]", dataStr);
        assertEquals('+', dataStr.charAt(9), "Last character should be + sign");
        assertEquals("123456789", dataStr.substring(0, 9), "Should be digits 123456789");
    }

    @Test
    void testDeserializePositiveAmountLeading() throws Exception {
        CobolDeserializer deserializer = new CobolDeserializer();

        // Create test data using proper encoding: + followed by 123456789 (9 digits for S9(7)V99)
        String testStr = "+123456789";
        byte[] data = testStr.getBytes(Charset.forName("CP1047"));

        SignLeadingRecord record = deserializer.deserialize(data, SignLeadingRecord.class, Charset.forName("CP1047"));

        assertNotNull(record.getAmountLeading());
        assertEquals(new BigDecimal("1234567.89"), record.getAmountLeading());
    }

    @Test
    void testDeserializeNegativeAmountLeading() throws Exception {
        CobolDeserializer deserializer = new CobolDeserializer();

        // Create test data using proper encoding: - followed by 123456789
        String testStr = "-123456789";
        byte[] data = testStr.getBytes(Charset.forName("CP1047"));

        SignLeadingRecord record = deserializer.deserialize(data, SignLeadingRecord.class, Charset.forName("CP1047"));

        assertNotNull(record.getAmountLeading());
        assertEquals(new BigDecimal("-1234567.89"), record.getAmountLeading());
    }

    @Test
    void testDeserializePositiveAmountTrailing() throws Exception {
        CobolDeserializer deserializer = new CobolDeserializer();

        // Create test data using proper encoding: 123456789 followed by +
        String testStr = "123456789+";
        byte[] data = testStr.getBytes(Charset.forName("CP1047"));

        SignTrailingRecord record = deserializer.deserialize(data, SignTrailingRecord.class, Charset.forName("CP1047"));

        assertNotNull(record.getAmountTrailing());
        assertEquals(new BigDecimal("1234567.89"), record.getAmountTrailing());
    }

    @Test
    void testDeserializeNegativeAmountTrailing() throws Exception {
        CobolDeserializer deserializer = new CobolDeserializer();

        // Create test data using proper encoding: 123456789 followed by -
        String testStr = "123456789-";
        byte[] data = testStr.getBytes(Charset.forName("CP1047"));

        SignTrailingRecord record = deserializer.deserialize(data, SignTrailingRecord.class, Charset.forName("CP1047"));

        assertNotNull(record.getAmountTrailing());
        assertEquals(new BigDecimal("-1234567.89"), record.getAmountTrailing());
    }

    @Test
    void testRoundTripLeading() throws Exception {
        CobolSerializer serializer = new CobolSerializer();
        CobolDeserializer deserializer = new CobolDeserializer();

        SignLeadingRecord original = new SignLeadingRecord();
        original.setAmountLeading(new BigDecimal("9876543.21"));

        byte[] data = serializer.serialize(original, Charset.forName("CP1047"));
        SignLeadingRecord deserialized = deserializer.deserialize(data, SignLeadingRecord.class, Charset.forName("CP1047"));

        assertEquals(original.getAmountLeading(), deserialized.getAmountLeading());
    }

    @Test
    void testRoundTripTrailing() throws Exception {
        CobolSerializer serializer = new CobolSerializer();
        CobolDeserializer deserializer = new CobolDeserializer();

        SignTrailingRecord original = new SignTrailingRecord();
        original.setAmountTrailing(new BigDecimal("-9876543.21"));

        byte[] data = serializer.serialize(original, Charset.forName("CP1047"));
        SignTrailingRecord deserialized = deserializer.deserialize(data, SignTrailingRecord.class, Charset.forName("CP1047"));

        assertEquals(original.getAmountTrailing(), deserialized.getAmountTrailing());
    }
}

