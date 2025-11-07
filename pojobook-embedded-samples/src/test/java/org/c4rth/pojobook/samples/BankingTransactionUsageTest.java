package org.c4rth.pojobook.samples;

import org.c4rth.pojobook.samples.generated.BankingTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BankingTransaction serialization and deserialization.
 * Demonstrates real-world usage of the generated POJO with PojoBook.
 * <p>
 * Note: These tests focus on DISPLAY fields. COMP-3 field serialization
 * (Amount, Balance fields) is demonstrated in the pojobook-serializer module tests.
 */
class BankingTransactionUsageTest {

    private final Charset charset = Charset.forName("CP1047");

    @Test
    void testSerializeAndDeserializeTransaction() throws Exception {
        BankingTransaction original = createTransaction(100, "D");

        // When: Serialize to COBOL format
        byte[] cobolData = original.serialize(charset);

        // Then: Should produce binary data
        assertNotNull(cobolData);
        assertTrue(cobolData.length > 0);

        // When: Deserialize back to Java
        BankingTransaction deserialized = BankingTransaction.deserialize(cobolData, charset);

        // Then: All DISPLAY fields should match
        assertNotNull(deserialized);
        assertEquals(original.getTransactionId(), deserialized.getTransactionId());
        assertEquals(original.getAccountNumber(), deserialized.getAccountNumber());
        assertEquals(original.getTransactionType().trim(), deserialized.getTransactionType().trim());
        assertEquals(original.getTransactionDate(), deserialized.getTransactionDate());
        assertEquals(original.getTransactionTime(), deserialized.getTransactionTime());
        assertEquals(original.getDescription().trim(), deserialized.getDescription().trim());
        assertEquals(original.getBranchCode().trim(), deserialized.getBranchCode().trim());
        assertEquals(original.getTellerId().trim(), deserialized.getTellerId().trim());
        assertEquals(original.getAuthorizationCode().trim(), deserialized.getAuthorizationCode().trim());
        assertEquals(original.getStatus().trim(), deserialized.getStatus().trim());
    }

    @Test
    void testTransactionTypes() throws Exception {
        // Test all transaction types
        String[] types = {"D", "W", "T", "P"}; // Deposit, Withdrawal, Transfer, Payment

        for (String type : types) {
            BankingTransaction txn = createTransaction(type.charAt(0), type);

            byte[] serialized = txn.serialize(charset);
            BankingTransaction deserialized = BankingTransaction.deserialize(serialized, charset);

            assertEquals(type, deserialized.getTransactionType().trim(),
                    "Transaction type '" + type + "' should be preserved");
        }
    }

    @Test
    void testTransactionStatuses() throws Exception {
        // Test all status values
        String[] statuses = {"C", "P", "F", "R"}; // Completed, Pending, Failed, Reversed

        for (String status : statuses) {
            BankingTransaction txn = createTransaction(0, "D");
            txn.setStatus(status);

            byte[] serialized = txn.serialize(charset);
            BankingTransaction deserialized = BankingTransaction.deserialize(serialized, charset);

            assertEquals(status, deserialized.getStatus().trim(),
                    "Status '" + status + "' should be preserved");
        }
    }

    @Test
    void testMultipleTransactions() throws Exception {
        // Given: Three transactions
        BankingTransaction[] transactions = {
                createTransaction(1, "D"),
                createTransaction(2, "W"),
                createTransaction(3, "T")
        };

        // When: Serialize each
        byte[][] serializedData = new byte[transactions.length][];
        for (int i = 0; i < transactions.length; i++) {
            serializedData[i] = transactions[i].serialize(charset);
        }

        // Then: Each should produce different data
        assertFalse(java.util.Arrays.equals(serializedData[0], serializedData[1]));
        assertFalse(java.util.Arrays.equals(serializedData[1], serializedData[2]));

        // And: Can be deserialized correctly
        for (int i = 0; i < transactions.length; i++) {
            BankingTransaction deserialized = BankingTransaction.deserialize(serializedData[i], charset);
            assertEquals(transactions[i].getTransactionType().trim(),
                    deserialized.getTransactionType().trim());
        }
    }

    @Test
    void testLongDescription() throws Exception {
        // Given: Transaction with long description
        BankingTransaction txn = createTransaction(100, "D");
        txn.setDescription("This is a very long transaction description that demonstrates field length handling in COBOL");

        // When: Serialize and deserialize
        byte[] serialized = txn.serialize(charset);
        BankingTransaction deserialized = BankingTransaction.deserialize(serialized, charset);

        // Then: Description should be preserved
        assertNotNull(deserialized.getDescription());
        assertFalse(deserialized.getDescription().trim().isEmpty());
    }

    @Test
    void testSerializationConsistency() throws Exception {
        // Given: A transaction
        BankingTransaction txn = createTransaction(100, "D");

        // When: Serialize multiple times
        byte[] serialized1 = txn.serialize(charset);
        byte[] serialized2 = txn.serialize(charset);

        // Then: Should produce identical binary data
        assertArrayEquals(serialized1, serialized2,
                "Serializing the same transaction multiple times should produce identical binary data");
    }

    // Helper method
    private BankingTransaction createTransaction(int id, String type) {
        BankingTransaction txn = new BankingTransaction();
        txn.setTransactionId(241101000L + id);
        txn.setAccountNumber(123456789L);
        txn.setTransactionType(type);
        txn.setTransactionDate(20241101);
        txn.setTransactionTime(120000 + (id * 1000));
        txn.setAmount(new BigDecimal(50000 + (id * 1000)));
        txn.setBalanceBefore(new BigDecimal(5000 + (id * 1000)));
        txn.setBalanceAfter(new BigDecimal(10000 + (id * 1000)));
        txn.setStatus("C");
        txn.setDescription("Cash deposit at branch");
        txn.setBranchCode("BR001");
        txn.setTellerId("TELLER123");
        txn.setAuthorizationCode("AUTH789456");
        return txn;
    }
}