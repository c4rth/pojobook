package org.c4rth.pojobook;

import org.c4rth.pojobook.annotation.CobolField;
import org.c4rth.pojobook.annotation.CobolRecord;
import org.c4rth.pojobook.serializer.CobolSerializer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Debug test to see what's actually being serialized
 */
public class SignDebugTest {

    private Logger log = LoggerFactory.getLogger(SignDebugTest.class);

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

    @Test
    void debugSerialization() throws Exception {
        CobolSerializer serializer = new CobolSerializer();

        SignLeadingRecord record = new SignLeadingRecord();
        record.setAmountLeading(new BigDecimal("1234567.89"));

        byte[] data = serializer.serialize(record, StandardCharsets.ISO_8859_1);

        log.info("=== Serialization Debug ===");
        log.info("Data length: {}", data.length);
        log.info("Hex bytes:");
        for (int i = 0; i < data.length; i++) {
            log.info("  {}: 0x{} ({} '{}'", i, String.format("%02X", data[i] & 0xFF), data[i], (data[i] >= 32 && data[i] < 127) ? (char) data[i] : '?');
        }

        // Try different encodings
        log.info("As CP1047 (EBCDIC): [{}]", new String(data, Charset.forName("CP1047")));
        log.info("As ASCII: [{}]", new String(data, StandardCharsets.ISO_8859_1));

        // Check what + and - are in EBCDIC
        byte[] plusSign = "+".getBytes(Charset.forName("CP1047"));
        byte[] minusSign = "-".getBytes(Charset.forName("CP1047"));
        log.info("+ in EBCDIC: 0x{}", String.format("%02X", plusSign[0] & 0xFF));
        log.info("- in EBCDIC: 0x{}", String.format("%02X", minusSign[0] & 0xFF));
        log.info("0 in EBCDIC: 0x{}", String.format("%02X", "0".getBytes(Charset.forName("CP1047"))[0] & 0xFF));
    }
}

