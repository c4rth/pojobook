package org.pojobook.samples;

import org.junit.jupiter.api.Test;
import org.pojobook.PojoBook;
import org.pojobook.samples.generated.SalesOccurs;

import java.nio.charset.Charset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SalesOccurs serialization and deserialization.
 * Demonstrate OCCURS clause usage - arrays and repeating groups.
 * <p>
 * Note: These tests focus on DISPLAY fields. COMP-3 sales amount fields are
 * demonstrated in the pojobook-serializer module tests.
 */
class SalesOccursUsageTest {

    private final PojoBook pojoBook = new PojoBook();
    private final Charset charset = Charset.forName("CP1047");

    @Test
    void testSerializeAndDeserializeSalesReport() throws Exception {
        // Given: A sales report with DISPLAY fields
        SalesOccurs original = new SalesOccurs();
        original.setReportDate(20241031);
        original.setReportPeriod("October 2024");
        original.setTransactionCount(1523);

        // When: Serialize to COBOL format
        byte[] cobolData = pojoBook.serialize(original, charset);

        // Then: Should produce binary data
        assertNotNull(cobolData);
        assertTrue(cobolData.length > 0);

        // When: Deserialize back to Java
        SalesOccurs deserialized = pojoBook.deserialize(cobolData, SalesOccurs.class, charset);

        // Then: DISPLAY fields should match
        assertNotNull(deserialized);
        assertEquals(original.getReportDate(), deserialized.getReportDate());
        assertEquals(original.getReportPeriod().trim(), deserialized.getReportPeriod().trim());
        assertEquals(original.getTransactionCount(), deserialized.getTransactionCount());
    }

    @Test
    void testMonthlyReports() throws Exception {
        // Given: Three monthly reports
        SalesOccurs oct = createSalesReport(20241031, "October 2024", 1500);
        SalesOccurs nov = createSalesReport(20241130, "November 2024", 1800);
        SalesOccurs dec = createSalesReport(20241231, "December 2024", 2500);

        // When: Serialize each
        byte[] octData = pojoBook.serialize(oct, charset);
        byte[] novData = pojoBook.serialize(nov, charset);
        byte[] decData = pojoBook.serialize(dec, charset);

        // Then: Each should produce different data
        assertNotNull(octData);
        assertNotNull(novData);
        assertNotNull(decData);
        assertFalse(Arrays.equals(octData, novData));
        assertFalse(Arrays.equals(novData, decData));

        // When: Deserialize all
        SalesOccurs octResult = pojoBook.deserialize(octData, SalesOccurs.class, charset);
        SalesOccurs novResult = pojoBook.deserialize(novData, SalesOccurs.class, charset);
        SalesOccurs decResult = pojoBook.deserialize(decData, SalesOccurs.class, charset);

        // Then: Transaction counts should show growth
        assertTrue(novResult.getTransactionCount() > octResult.getTransactionCount(),
                "November transactions should exceed October");
        assertTrue(decResult.getTransactionCount() > novResult.getTransactionCount(),
                "December transactions should exceed November");
    }

    @Test
    void testReportPeriodFormats() throws Exception {
        // Test different report period formats
        String[] periods = {
                "January 2024",
                "Q4 2024",
                "Fiscal Year 2024",
                "Week 42"
        };

        for (String period : periods) {
            SalesOccurs report = new SalesOccurs();
            report.setReportDate(20241101);
            report.setReportPeriod(period);
            report.setTransactionCount(1000);

            byte[] serialized = pojoBook.serialize(report, charset);
            SalesOccurs deserialized = pojoBook.deserialize(serialized, SalesOccurs.class, charset);

            assertNotNull(deserialized.getReportPeriod());
            assertFalse(deserialized.getReportPeriod().trim().isEmpty(), "Period '" + period + "' should be preserved");
        }
    }

    @Test
    void testZeroTransactions() throws Exception {
        // Given: Report with no transactions
        SalesOccurs report = new SalesOccurs();
        report.setReportDate(20240101);
        report.setReportPeriod("Holiday");
        report.setTransactionCount(0);

        // When: Serialize and deserialize
        byte[] serialized = pojoBook.serialize(report, charset);
        SalesOccurs deserialized = pojoBook.deserialize(serialized, SalesOccurs.class, charset);

        // Then: Zero value should be preserved
        assertEquals((short) 0, deserialized.getTransactionCount());
    }

    @Test
    void testMaximumTransactionCount() throws Exception {
        // Given: Report with high transaction count
        SalesOccurs report = new SalesOccurs();
        report.setReportDate(20241231);
        report.setReportPeriod("Peak Season");
        report.setTransactionCount(30000);

        // When: Serialize and deserialize
        byte[] serialized = pojoBook.serialize(report, charset);
        SalesOccurs deserialized = pojoBook.deserialize(serialized, SalesOccurs.class, charset);

        // Then: Count should be preserved
        assertEquals(report.getTransactionCount(), deserialized.getTransactionCount());
    }

    @Test
    void testSerializationConsistency() throws Exception {
        // Given: A sales report
        SalesOccurs report = createSalesReport(20241031, "October 2024", 1234);

        // When: Serialize multiple times
        byte[] serialized1 = pojoBook.serialize(report, charset);
        byte[] serialized2 = pojoBook.serialize(report, charset);

        // Then: Should produce identical binary data
        assertArrayEquals(serialized1, serialized2,
                "Serializing the same report multiple times should produce identical binary data");
    }

    // Helper method
    private SalesOccurs createSalesReport(int date, String period, Integer transactionCount) {
        SalesOccurs report = new SalesOccurs();
        report.setReportDate(date);
        report.setReportPeriod(period);
        report.setTransactionCount(transactionCount);
        return report;
    }
}

