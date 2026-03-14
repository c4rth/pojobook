package org.pojobook.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.pojobook.CobolDataType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for REDEFINES, OCCURS variations, and comprehensive COBOL language support.
 */
class AdvancedCopybookFeaturesTest {

    @Test
    void testRedefines() throws Exception {
        CopybookDefinition definition = getDefinitionRedefines();

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        // Find the REDEFINES field
        FieldDefinition redefField = fields.stream()
                .filter(f -> "EMP-DATA-REDEF".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(redefField);
        assertEquals("EMP-DATA", redefField.getRedefines());

        // Verify that REDEFINES field has same offset as redefined field
        FieldDefinition origField = fields.stream()
                .filter(f -> "EMP-DATA".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(origField);
        assertEquals(origField.getOffset(), redefField.getOffset());
    }

    private static CopybookDefinition getDefinitionRedefines() throws ParseException {
        String copybook = """
                   01  EMPLOYEE-RECORD.
                       05  EMP-ID              PIC 9(6).
                       05  EMP-DATA.
                           10  EMP-NAME        PIC X(30).
                           10  EMP-SALARY      PIC 9(7)V99 COMP-3.
                       05  EMP-DATA-REDEF REDEFINES EMP-DATA.
                           10  EMP-CODE        PIC X(5).
                           10  EMP-BALANCE     PIC S9(9)V99 COMP-3.
                """;

        CopybookParser parser = new CopybookParser();
        return parser.parse(copybook);
    }

    @Test
    void testOccursFixed() throws Exception {
        String copybook = """
                   01  SALES-RECORD.
                       05  STORE-ID            PIC 9(4).
                       05  MONTHLY-SALES       OCCURS 12 TIMES.
                           10  MONTH-AMOUNT    PIC 9(7)V99 COMP-3.
                           10  MONTH-COUNT     PIC 9(5) COMP.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition monthlySales = fields.stream()
                .filter(f -> "MONTHLY-SALES".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(monthlySales);
        assertEquals(12, monthlySales.getOccurs());
    }

    @Test
    void testOccursDependingOn() throws Exception {
        CopybookDefinition definition = getDefinitionOccursDependingOn();

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition lineItems = fields.stream()
                .filter(f -> "LINE-ITEMS".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(lineItems);
        assertEquals(1, lineItems.getMinOccurs());
        assertEquals(100, lineItems.getMaxOccurs());
        assertEquals("ITEM-COUNT", lineItems.getDependingOn());
    }

    private static CopybookDefinition getDefinitionOccursDependingOn() throws ParseException {
        String copybook = """
                   01  ORDER-RECORD.
                       05  ORDER-ID            PIC 9(8).
                       05  ITEM-COUNT          PIC 9(3).
                       05  LINE-ITEMS          OCCURS 1 TO 100 TIMES
                                               DEPENDING ON ITEM-COUNT.
                           10  ITEM-CODE       PIC X(10).
                           10  QUANTITY        PIC 9(5).
                           10  UNIT-PRICE      PIC 9(7)V99 COMP-3.
                """;

        CopybookParser parser = new CopybookParser();
        return parser.parse(copybook);
    }

    @Test
    void testOccursWithIndexedBy() throws Exception {
        String copybook = """
                   01  INVENTORY-RECORD.
                       05  WAREHOUSE-ID        PIC 9(4).
                       05  PRODUCTS            OCCURS 500 TIMES
                                               INDEXED BY PROD-IDX.
                           10  PRODUCT-CODE    PIC X(12).
                           10  STOCK-LEVEL     PIC 9(6).
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition products = fields.stream()
                .filter(f -> "PRODUCTS".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(products);
        assertEquals(500, products.getOccurs());
        assertNotNull(products.getIndexedBy());
        assertEquals(1, products.getIndexedBy().length);
        assertEquals("PROD-IDX", products.getIndexedBy()[0]);
    }

    @Test
    void testOccursWithAscendingKey() throws Exception {
        CopybookDefinition definition = getDefinitionOccursWithAscendingKey();

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition custEntry = fields.stream()
                .filter(f -> "CUSTOMER-ENTRY".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(custEntry);
        assertEquals(1000, custEntry.getOccurs());
        assertTrue(custEntry.isAscendingKey());
        assertNotNull(custEntry.getKeys());
        assertEquals("CUST-ID", custEntry.getKeys()[0]);
    }

    private static CopybookDefinition getDefinitionOccursWithAscendingKey() throws ParseException {
        String copybook = """
                   01  CUSTOMER-TABLE.
                       05  NUM-CUSTOMERS       PIC 9(4).
                       05  CUSTOMER-ENTRY      OCCURS 1000 TIMES
                                               ASCENDING KEY IS CUST-ID
                                               INDEXED BY CUST-IDX.
                           10  CUST-ID         PIC 9(10).
                           10  CUST-NAME       PIC X(40).
                           10  CREDIT-LIMIT    PIC 9(9)V99 COMP-3.
                """;

        CopybookParser parser = new CopybookParser();
        return parser.parse(copybook);
    }

    @Test
    void testFiller() throws Exception {
        String copybook = """
                   01  FORMATTED-RECORD.
                       05  RECORD-TYPE         PIC X(2).
                       05  FILLER              PIC X(10).
                       05  RECORD-DATA         PIC X(50).
                       05  FILLER              PIC X(8).
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        long fillerCount = fields.stream()
                .filter(FieldDefinition::isFiller)
                .count();

        assertEquals(2, fillerCount);
    }

    @Test
    void testValueClause() throws Exception {
        String copybook = """
                   01  CONSTANT-RECORD.
                       05  RECORD-TYPE         PIC X(4) VALUE 'CUST'.
                       05  VERSION-NUMBER      PIC 9(2) VALUE 01.
                       05  DELIMITER           PIC X VALUE ','.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition recordType = fields.stream()
                .filter(f -> "RECORD-TYPE".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(recordType);
        assertEquals("CUST", recordType.getValue());
    }

    @Test
    void testJustifiedRight() throws Exception {
        String copybook = """
                   01  TEXT-RECORD.
                       05  LEFT-ALIGNED        PIC X(20).
                       05  RIGHT-ALIGNED       PIC X(20) JUSTIFIED RIGHT.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition rightAligned = fields.stream()
                .filter(f -> "RIGHT-ALIGNED".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(rightAligned);
        assertTrue(rightAligned.isJustifiedRight());
    }

    @Test
    void testBlankWhenZero() throws Exception {
        String copybook = """
                   01  DISPLAY-RECORD.
                       05  AMOUNT-1            PIC 9(7)V99 BLANK WHEN ZERO.
                       05  AMOUNT-2            PIC 9(7)V99.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition amount1 = fields.stream()
                .filter(f -> "AMOUNT-1".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(amount1);
        assertTrue(amount1.isBlankWhenZero());
    }

    @Test
    void testSynchronized() throws Exception {
        String copybook = """
                   01  ALIGNED-RECORD.
                       05  FIELD-1             PIC X(3).
                       05  FIELD-2             PIC 9(9) COMP SYNCHRONIZED.
                       05  FIELD-3             PIC X(5).
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        FieldDefinition field2 = fields.stream()
                .filter(f -> "FIELD-2".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(field2);
        assertNotNull(field2.getSync());
    }

    @Test
    void testComplexNestedStructureWithRedefines() throws Exception {
        CopybookDefinition definition = getDefinitionComplexNestedStructureWithRedefines();

        assertNotNull(definition);
        assertFalse(definition.getFields().isEmpty());

        // Verify REDEFINES
        FieldDefinition creditInfo = definition.getFields().stream()
                .filter(f -> "CREDIT-INFO".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(creditInfo);
        assertEquals("PAYMENT-INFO", creditInfo.getRedefines());

        // Verify OCCURS
        FieldDefinition transHistory = definition.getFields().stream()
                .filter(f -> "TRANS-HISTORY".equals(f.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(transHistory);
        assertEquals(10, transHistory.getOccurs());
    }

    private static CopybookDefinition getDefinitionComplexNestedStructureWithRedefines() throws ParseException {
        String copybook = """
                   01  TRANSACTION-RECORD.
                       05  TRANS-TYPE          PIC X(2).
                       05  TRANS-DATA.
                           10  PAYMENT-INFO.
                               15  PAYMENT-AMT     PIC 9(7)V99 COMP-3.
                               15  PAYMENT-DATE    PIC 9(8).
                           10  CREDIT-INFO REDEFINES PAYMENT-INFO.
                               15  CREDIT-LIMIT    PIC 9(9)V99 COMP-3.
                               15  CREDIT-USED     PIC 9(9)V99 COMP-3.
                       05  TRANS-HISTORY       OCCURS 10 TIMES.
                           10  HIST-DATE           PIC 9(8).
                           10  HIST-AMOUNT         PIC S9(7)V99 COMP-3.
                """;

        CopybookParser parser = new CopybookParser();
        return parser.parse(copybook);
    }

    @Test
    void testUsageVariations() throws Exception {
        CopybookDefinition definition = getDefintiionUsageVariations();

        assertNotNull(definition);
        List<FieldDefinition> fields = definition.getFields();

        // Verify different data types
        FieldDefinition fieldComp = fields.stream()
                .filter(f -> "FIELD-COMP".equals(f.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(fieldComp);
        Assertions.assertEquals(CobolDataType.COMP, fieldComp.getType());

        FieldDefinition fieldComp3 = fields.stream()
                .filter(f -> "FIELD-COMP-3".equals(f.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(fieldComp3);
        assertEquals(CobolDataType.COMP_3, fieldComp3.getType());
    }

    private static CopybookDefinition getDefintiionUsageVariations() throws ParseException {
        String copybook = """
                   01  USAGE-EXAMPLES.
                       05  FIELD-DISPLAY       PIC 9(5) USAGE DISPLAY.
                       05  FIELD-COMP          PIC 9(9) USAGE COMP.
                       05  FIELD-COMP-3        PIC S9(7)V99 USAGE COMP-3.
                       05  FIELD-BINARY        PIC 9(9) USAGE BINARY.
                       05  FIELD-PACKED        PIC S9(9)V99 USAGE PACKED-DECIMAL.
                """;

        CopybookParser parser = new CopybookParser();
        return parser.parse(copybook);
    }
}

