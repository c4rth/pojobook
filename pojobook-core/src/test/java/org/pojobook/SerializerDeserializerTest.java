package org.pojobook;

import org.junit.jupiter.api.Test;
import org.pojobook.annotation.CobolField;
import org.pojobook.annotation.CobolRecord;

import java.math.BigDecimal;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SerializerDeserializerTest {

    private final PojoBook pojoBook = new PojoBook();

    @CobolRecord
    public static class SimpleRecord {
        @CobolField(level = 5, name = "CUSTOMER-ID", picture = "9(10)", position = 0, integerDigits = 10)
        private Long customerId;

        @CobolField(level = 5, name = "CUSTOMER-NAME", picture = "X(20)", position = 1, length = 20)
        private String customerName;

        @CobolField(level = 5, name = "BALANCE", picture = "S9(7)V99", type = CobolDataType.COMP_3,
                position = 2, integerDigits = 7, decimalDigits = 2, signed = true)
        private BigDecimal balance;

        // Public no-arg constructor
        public SimpleRecord() {
        }

        // Getters and setters
        public Long getCustomerId() {
            return customerId;
        }

        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }
    }

    @Test
    void testSerializeDeserialize() throws Exception {

        // Create test record
        SimpleRecord record = new SimpleRecord();
        record.setCustomerId(1234567890L);
        record.setCustomerName("John Doe");
        record.setBalance(new BigDecimal("12345.67"));

        // Serialize
        byte[] data = pojoBook.serialize(record, Charset.forName("CP1047"));
        assertNotNull(data);
        assertTrue(data.length > 0);

        // Deserialize
        SimpleRecord deserialized = pojoBook.deserialize(data, SimpleRecord.class, Charset.forName("CP1047"));
        assertNotNull(deserialized);
        assertEquals(record.getCustomerId(), deserialized.getCustomerId());
        assertEquals(record.getCustomerName().trim(), deserialized.getCustomerName().trim());
        assertEquals(record.getBalance(), deserialized.getBalance());
    }

    @Test
    void testDeserializeSerialize() throws Exception {

        // Create and serialize original record
        SimpleRecord original = new SimpleRecord();
        original.setCustomerId(1234567890L);
        original.setCustomerName("John Doe");
        original.setBalance(new BigDecimal("12345.67"));

        byte[] data = pojoBook.serialize(original, Charset.forName("CP1047"));
        assertNotNull(data);
        assertTrue(data.length > 0);

        // Deserialize
        SimpleRecord deserialized = pojoBook.deserialize(data, SimpleRecord.class, Charset.forName("CP1047"));
        assertNotNull(deserialized);
        assertEquals(1234567890L, deserialized.getCustomerId());
        assertEquals("John Doe", deserialized.getCustomerName().trim());
        assertEquals(new BigDecimal("12345.67"), deserialized.getBalance());

        // Serialize again and verify it matches
        byte[] serializedData = pojoBook.serialize(deserialized, Charset.forName("CP1047"));
        assertArrayEquals(data, serializedData);
    }
}

