package org.c4rth.pojobook.samples;

import org.c4rth.pojobook.PojoBook;
import org.c4rth.pojobook.samples.generated.CustomerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CustomerUsageTest {

    private PojoBook pojoBook;
    private final Charset charset = Charset.forName("CP1047");

    @BeforeEach
    void setUp() {
        pojoBook = new PojoBook();
    }

    private final byte[] record = new byte[]{
            // CUSTOMER-ID: "12345" in CP1047 (EBCDIC)
            (byte) 0xF1, (byte) 0xF2, (byte) 0xF3, (byte) 0xF4, (byte) 0xF5,
            // CUSTOMER-NAME: "JOHN DOE" + spaces in CP1047 (EBCDIC)
            (byte) 0xD1, (byte) 0xD6, (byte) 0xC8, (byte) 0xD5, (byte) 0x40,
            (byte) 0xC4, (byte) 0xD6, (byte) 0xC5, (byte) 0x40, (byte) 0x40,
            (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
            (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
            // CUSTOMER-AGE: "031" in CP1047 (EBCDIC)
            (byte) 0xF0, (byte) 0xF3, (byte) 0xF1,
            // CUSTOMER-BALANCE: COMP-3 +0012345.67
            0x00, 0x12, 0x34, 0x56, 0x7C,
            // CUSTOMER-STATUS: "A" in CP1047 (EBCDIC)
            (byte) 0xC1
    };

    @Test
    public void testCustomerUsage() throws Exception {
        CustomerRecord customer = pojoBook.deserialize(record, CustomerRecord.class, charset);

        assertEquals(12345, customer.getCustomerId());
        assertEquals("JOHN DOE", customer.getCustomerName().trim());
        assertEquals(31, customer.getCustomerAge());
        assertEquals(new java.math.BigDecimal("12345.67"), customer.getCustomerBalance());
        assertEquals("A", customer.getCustomerStatus().trim());
    }

    @Test
    public void testCustomer() throws Exception {
        // Given: Create a customer record
        CustomerRecord original = new CustomerRecord();
        original.setCustomerId(12345);
        original.setCustomerName("JOHN DOE");
        original.setCustomerAge(31);
        original.setCustomerBalance(new BigDecimal("123.00"));
        original.setCustomerStatus("A");

        // When: Serialize and deserialize
        byte[] data = pojoBook.serialize(original, charset);
        CustomerRecord deserialized = pojoBook.deserialize(data, CustomerRecord.class, charset);

        // Then: Fields should match (except balance which has a bug)
        assertNotNull(deserialized);
        assertEquals(12345, deserialized.getCustomerId());
        assertEquals("JOHN DOE", deserialized.getCustomerName().trim());
        assertEquals(31, deserialized.getCustomerAge());
        assertEquals("A", deserialized.getCustomerStatus().trim());
        assertEquals(original.getCustomerBalance(), deserialized.getCustomerBalance());

        assertEquals(original, deserialized);
    }
}
