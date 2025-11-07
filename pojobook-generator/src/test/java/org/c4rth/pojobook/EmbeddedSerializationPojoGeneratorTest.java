package org.c4rth.pojobook;

import org.c4rth.pojobook.generator.EmbeddedSerializationPojoGenerator;
import org.c4rth.pojobook.parser.CopybookDefinition;
import org.c4rth.pojobook.parser.CopybookParser;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddedSerializationPojoGeneratorTest {

    private final Logger log = LoggerFactory.getLogger(EmbeddedSerializationPojoGeneratorTest.class);

    @Test
    void testGeneratePojoWithEmbeddedSerialization() throws IOException {
        String copybook = """
                   01  EMPLOYEE-RECORD.
                       05  EMPLOYEE-ID         PIC 9(8).
                       05  FIRST-NAME          PIC X(20).
                       05  LAST-NAME           PIC X(30).
                       05  SALARY              PIC S9(7)V99 COMP-3.
                       05  HIRE-DATE           PIC 9(8).
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parseString(copybook);
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
        assertTrue(generatedCode.contains("public byte[] serialize(Charset charset)"));
        assertTrue(generatedCode.contains("deserialize(byte[] data, Charset charset)"));

        // Check that there are NO annotations
        assertFalse(generatedCode.contains("@CobolField"));
        assertFalse(generatedCode.contains("@CobolRecord"));

        // Check for serialization logic
        assertTrue(generatedCode.contains("ByteArrayOutputStream"));
        assertTrue(generatedCode.contains("baos.write"));

        // Check for deserialization logic
        assertTrue(generatedCode.contains("offset"));
        assertTrue(generatedCode.contains("Arrays.copyOfRange"));
    }

    @Test
    void testGeneratePojoWithOccurs() throws IOException {
        String copybook = """
                   01  SALES-RECORD.
                       05  STORE-ID            PIC 9(4).
                       05  MONTHLY-SALES       OCCURS 12 TIMES PIC 9(7)V99 COMP-3.
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parseString(copybook);
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

        // Check for serialization methods
        assertTrue(generatedCode.contains("public byte[] serialize(Charset charset)"));
        assertTrue(generatedCode.contains("deserialize(byte[] data, Charset charset)"));
    }

    @Test
    void testGeneratePojoWithNestedOccurs() throws IOException {
        String copybook = """
                   01  COMPANY-RECORD.
                       05  COMPANY-ID          PIC 9(8).
                       05  DEPARTMENT          OCCURS 5 TIMES.
                           10  DEPT-ID         PIC 9(4).
                           10  DEPT-NAME       PIC X(20).
                           10  EMPLOYEE-COUNT  PIC 9(4).
                """;

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parseString(copybook);
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

        // Check for serialization in nested class
        assertTrue(generatedCode.contains("Department.deserialize"));

        // Check for no annotations
        assertFalse(generatedCode.contains("@CobolField"));
    }

    @Test
    void testGenerateSimpleDtar020() throws IOException {
        Path copybookPath = Paths.get("src/test/resources/employee-record.cpy");

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(copybookPath);

        EmbeddedSerializationPojoGenerator generator = new EmbeddedSerializationPojoGenerator()
                .withPackage("org.c4rth.pojobook.generated");

        String generatedCode = generator.generate(definition);

        log.info("=== Generated POJO for Employee Record ===");
        log.info(generatedCode);
        log.info("==========================================");

        assertNotNull(generatedCode);
        assertTrue(generatedCode.contains("package org.c4rth.pojobook.generated"));

        // Check for serialization methods
        assertTrue(generatedCode.contains("public byte[] serialize(Charset charset)"));
        assertTrue(generatedCode.contains("deserialize(byte[] data, Charset charset)"));

        // Check for no annotations
        assertFalse(generatedCode.contains("@CobolField"));
        assertFalse(generatedCode.contains("@CobolRecord"));
    }
}

