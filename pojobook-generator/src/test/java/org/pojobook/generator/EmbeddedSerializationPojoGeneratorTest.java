package org.pojobook.generator;

import org.junit.jupiter.api.Test;
import org.pojobook.parser.CopybookDefinition;
import org.pojobook.parser.CopybookParser;
import org.pojobook.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedSerializationPojoGeneratorTest {

    private final Logger log = LoggerFactory.getLogger(EmbeddedSerializationPojoGeneratorTest.class);

    @Test
    void testGeneratePojoWithEmbeddedSerialization() throws ParseException {
        String copybook = """
                   01  EMPLOYEE-RECORD.
                       05  EMPLOYEE-ID         PIC 9(8).
                       05  FIRST-NAME          PIC X(20).
                       05  LAST-NAME           PIC X(30).
                       05  SALARY              PIC S9(7)V99 COMP-3.
                       05  HIRE-DATE           PIC 9(8).
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);
        definition.setRecordName("EMPLOYEE-RECORD");

        EmbeddedSerializationPojoGenerator generator = new EmbeddedSerializationPojoGenerator()
                .withPackage("com.example.generated");

        String generatedCode = generator.generate(definition);

        log.info("=== Generated POJO with Embedded Serialization ===");
        log.info(generatedCode);
        log.info("==================================================");

        assertNotNull(generatedCode);
        assertTrue(generatedCode.contains("package com.example.generated"));
        assertTrue(generatedCode.contains("class EmployeeRecord"));

        // Check for fields
        assertTrue(generatedCode.contains("private"));
        assertTrue(generatedCode.contains("employeeId"));
        assertTrue(generatedCode.contains("firstName"));
        assertTrue(generatedCode.contains("lastName"));
        assertTrue(generatedCode.contains("salary"));
        assertTrue(generatedCode.contains("hireDate"));

        // Check for serialization methods
        assertTrue(generatedCode.contains("public int serializedSize()"));
        assertTrue(generatedCode.contains("public void serialize(byte[] buffer)"));
        assertTrue(generatedCode.contains("public void serialize(byte[] buffer, int offset)"));
        assertTrue(generatedCode.contains("public byte[] serialize(Charset charset)"));
        assertTrue(generatedCode.contains("deserialize(byte[] data, Charset charset)"));
        assertFalse(generatedCode.contains("catch (Exception e)"));

        // Check for cached metadata constants and usage in generated methods
        assertTrue(generatedCode.contains("private static final int LEN_EMPLOYEEID = 8;"));
        assertTrue(generatedCode.contains("private static final boolean IS_NUMERIC_EMPLOYEEID = true;"));
        assertTrue(generatedCode.contains("serializeDisplayStringDirect(buffer, offset + OFFSET_EMPLOYEEID, this.employeeId, LEN_EMPLOYEEID, IS_NUMERIC_EMPLOYEEID, charset)"));
        assertTrue(generatedCode.contains("deserializeDisplayInteger(data, offset + OFFSET_EMPLOYEEID, LEN_EMPLOYEEID, charset)"));

        // Check that there are NO annotations
        assertFalse(generatedCode.contains("@CobolField"));
        assertFalse(generatedCode.contains("@CobolRecord"));
    }

    @Test
    void testGeneratePojoWithOccurs() throws ParseException {
        String copybook = """
                   01  SALES-RECORD.
                       05  STORE-ID            PIC 9(4).
                       05  MONTHLY-SALES       OCCURS 12 TIMES PIC 9(7)V99 COMP-3.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);
        definition.setRecordName("SALES-RECORD");

        EmbeddedSerializationPojoGenerator generator = new EmbeddedSerializationPojoGenerator()
                .withPackage("com.example.generated");

        String generatedCode = generator.generate(definition);

        log.info("=== Generated POJO with OCCURS ===");
        log.info(generatedCode);
        log.info("==================================");

        assertNotNull(generatedCode);
        assertTrue(generatedCode.contains("package com.example.generated"));
        assertTrue(generatedCode.contains("class SalesRecord"));

        // Check for array field
        assertTrue(generatedCode.contains("monthlySales"));
        // Note: The type is Integer[] because integerDigits + decimalDigits is calculated differently
        assertTrue(generatedCode.contains("Integer[]") || generatedCode.contains("BigDecimal[]"));

        // Check for array handling in serialization
        assertTrue(generatedCode.contains("for (int i = 0; i < 12; i++)"));
        assertTrue(generatedCode.contains("int monthlySalesBaseOffset = offset + OFFSET_MONTHLYSALES;"));
        assertTrue(generatedCode.contains("int monthlySalesPos = monthlySalesBaseOffset;"));
        assertTrue(generatedCode.contains("monthlySalesPos += SIZE_MONTHLYSALES;"));

        // Check for serialization methods
        assertTrue(generatedCode.contains("public int serializedSize()"));
        assertTrue(generatedCode.contains("public void serialize(byte[] buffer)"));
        assertTrue(generatedCode.contains("public void serialize(byte[] buffer, int offset)"));
        assertTrue(generatedCode.contains("public byte[] serialize(Charset charset)"));
        assertTrue(generatedCode.contains("deserialize(byte[] data, Charset charset)"));
        assertFalse(generatedCode.contains("catch (Exception e)"));
    }

    @Test
    void testGeneratePojoWithNestedOccurs() throws ParseException {
        String copybook = """
                   01  COMPANY-RECORD.
                       05  COMPANY-ID          PIC 9(8).
                       05  DEPARTMENT          OCCURS 5 TIMES.
                           10  DEPT-ID         PIC 9(4).
                           10  DEPT-NAME       PIC X(20).
                           10  EMPLOYEE-COUNT  PIC 9(4).
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);
        definition.setRecordName("COMPANY-RECORD");

        EmbeddedSerializationPojoGenerator generator = new EmbeddedSerializationPojoGenerator()
                .withPackage("com.example.generated");

        String generatedCode = generator.generate(definition);

        log.info("=== Generated POJO with Nested OCCURS ===");
        log.info(generatedCode);
        log.info("=========================================");

        assertNotNull(generatedCode);
        assertTrue(generatedCode.contains("package com.example.generated"));
        assertTrue(generatedCode.contains("class CompanyRecord"));

        // Check for nested class
        assertTrue(generatedCode.contains("public static class Department"));
        assertTrue(generatedCode.contains("deptId"));
        assertTrue(generatedCode.contains("deptName"));
        assertTrue(generatedCode.contains("employeeCount"));

        // Check for array of nested class
        assertTrue(generatedCode.contains("Department[]"));
        assertTrue(generatedCode.contains("int departmentBaseOffset = offset + OFFSET_DEPARTMENT;"));
        assertTrue(generatedCode.contains("int departmentPos = departmentBaseOffset;"));
        assertTrue(generatedCode.contains("departmentPos += SIZE_DEPARTMENT;"));

        // Check for serialization in nested class
        assertTrue(generatedCode.contains("Department.deserializeFromBuffer(data,"));
        assertTrue(generatedCode.contains("private static CompanyRecord deserializeFromBuffer(byte[] data, int offset, Charset charset)"));
        assertTrue(generatedCode.contains("return deserializeFromBuffer(data, 0, charset);"));
        assertTrue(generatedCode.contains("CobolFieldDeserializer.deserializeDisplayInteger(data,"));
        assertFalse(generatedCode.contains("Integer.parseInt(strVal_"));
        assertFalse(generatedCode.contains("Arrays.copyOfRange"));

        // Check for no annotations
        assertFalse(generatedCode.contains("@CobolField"));
    }

    @Test
    void testGenerateSimpleDtar020() throws ParseException {
        Path copybookPath = Paths.get("src/test/resources/employee-record.cpy");

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybookPath);

        EmbeddedSerializationPojoGenerator generator = new EmbeddedSerializationPojoGenerator()
                .withPackage("org.pojobook.generated");

        String generatedCode = generator.generate(definition);

        log.info("=== Generated POJO for Employee Record ===");
        log.info(generatedCode);
        log.info("==========================================");

        assertNotNull(generatedCode);
        assertTrue(generatedCode.contains("package org.pojobook.generated"));

        // Check for serialization methods
        assertTrue(generatedCode.contains("public int serializedSize()"));
        assertTrue(generatedCode.contains("public void serialize(byte[] buffer)"));
        assertTrue(generatedCode.contains("public void serialize(byte[] buffer, int offset)"));
        assertTrue(generatedCode.contains("public byte[] serialize(Charset charset)"));
        assertTrue(generatedCode.contains("deserialize(byte[] data, Charset charset)"));
        assertFalse(generatedCode.contains("catch (Exception e)"));

        // Check for no annotations
        assertFalse(generatedCode.contains("@CobolField"));
        assertFalse(generatedCode.contains("@CobolRecord"));
    }

    @Test
    void testGeneratePojoWithSpecialFieldNames() throws ParseException {
        String copybook = """
                   01  CUSTOMER-RECORD.
                       05  -CUSTOMER-ID         PIC 9(10).
                       05  -CUSTOMER-NAME       PIC X(50).
                       05  -CUSTOMER-BALANCE    PIC S9(7)V99 COMP-3.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);
        definition.setRecordName("CUSTOMER-RECORD");

        EmbeddedSerializationPojoGenerator generator = new EmbeddedSerializationPojoGenerator()
                .withPackage("com.example.generated");

        String generatedCode = generator.generate(definition);

        log.info("=== Generated POJO for Customer Record ===");
        log.info(generatedCode);
    }

    @Test
    void testPrimitiveNumericFieldMode() throws ParseException {
        String copybook = """
                   01  METRICS-RECORD.
                       05  COUNTER             PIC 9(5).
                       05  TOTAL               PIC 9(12).
                       05  RATE                PIC S9(4)V99 COMP-3.
                       05  BINARY-SHORT        PIC 9(4) COMP.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybook);
        definition.setRecordName("METRICS-RECORD");

        EmbeddedSerializationPojoGenerator primitiveGenerator = new EmbeddedSerializationPojoGenerator()
                .withPackage("com.example.generated")
                .withPrimitiveNumericFields(true);

        String primitiveCode = primitiveGenerator.generate(definition);

        assertTrue(primitiveCode.contains("private int counter = 0;"));
        assertTrue(primitiveCode.contains("private long total = 0L;"));
        assertTrue(primitiveCode.contains("private short binaryShort = (short) 0;"));
        assertTrue(primitiveCode.contains("public int getCounter()"));
        assertTrue(primitiveCode.contains("public void setCounter(int counter)"));
        assertTrue(primitiveCode.contains("counter = CobolFieldDeserializer.deserializeDisplayInteger("));

        EmbeddedSerializationPojoGenerator defaultGenerator = new EmbeddedSerializationPojoGenerator()
                .withPackage("com.example.generated");

        String defaultCode = defaultGenerator.generate(definition);

        assertTrue(defaultCode.contains("private Integer counter = 0;"));
        assertTrue(defaultCode.contains("private Long total = 0L;"));
        assertTrue(defaultCode.contains("private Short binaryShort = (short) 0;"));
    }
}

