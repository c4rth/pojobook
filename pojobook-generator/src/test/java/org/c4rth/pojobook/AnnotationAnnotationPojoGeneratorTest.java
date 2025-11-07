package org.c4rth.pojobook;

import org.c4rth.pojobook.parser.CopybookDefinition;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationAnnotationPojoGeneratorTest {

    private final Logger log = LoggerFactory.getLogger(AnnotationAnnotationPojoGeneratorTest.class);

    @Test
    void testGeneratePojoWithOccursVariations() throws IOException {
        // Load the occurs-variations.cpy test file
        Path copybookPath = Paths.get("src/test/resources/occurs-variations.cpy");

        PojoBook pojoBook = PojoBook.builder()
                .withPackage("org.c4rth.pojobook.generated")
                .build();

        CopybookDefinition definition = pojoBook.parseCopybook(copybookPath);
        definition.setRecordName("SALES-ANALYSIS-RECORD");

        String generatedCode = pojoBook.generatePojo(definition);

        log.info("=== Generated POJO for OCCURS Variations ===");
        log.info(generatedCode);
        log.info("===========================================");

        assertNotNull(generatedCode);
        assertTrue(generatedCode.contains("package org.c4rth.pojobook.generated"));
        assertTrue(generatedCode.contains("class SalesAnalysisRecord"));

        // Check for fixed OCCURS fields (MONTHLY-DATA OCCURS 12 TIMES)
        assertTrue(generatedCode.contains("monthlyData"),
                "Should contain monthlyData field for MONTHLY-DATA array");

        // Check for variable OCCURS fields (REGIONAL-DATA OCCURS 1 TO 50)
        assertTrue(generatedCode.contains("regionalData"),
                "Should contain regionalData field for REGIONAL-DATA array");
        assertTrue(generatedCode.contains("regionCount"),
                "Should contain regionCount field for REGION-COUNT");

        // Check for nested OCCURS (STORE-DATA within REGIONAL-DATA)
        assertTrue(generatedCode.contains("storeData") || generatedCode.contains("storeCount"),
                "Should contain fields for nested STORE-DATA array");

        // Check for OCCURS with INDEXED BY (PRODUCT-TABLE)
        assertTrue(generatedCode.contains("productTable"),
                "Should contain productTable field for PRODUCT-TABLE array");

        // Check for OCCURS with ASCENDING KEY (EMPLOYEE-TABLE)
        assertTrue(generatedCode.contains("employeeTable"),
                "Should contain employeeTable field for EMPLOYEE-TABLE array");

        // Check annotations
        assertTrue(generatedCode.contains("@CobolField"));
        assertTrue(generatedCode.contains("occurs = "));
    }

    @Test
    void testGeneratePojo() throws IOException {
        String copybook = """
                   01  EMPLOYEE-RECORD.
                       05  EMPLOYEE-ID         PIC 9(8).
                       05  FIRST-NAME          PIC X(20).
                       05  LAST-NAME           PIC X(30).
                       05  SALARY              PIC S9(7)V99 COMP-3.
                       05  HIRE-DATE           PIC 9(8).
                """;

        PojoBook pojoBook = PojoBook.builder()
                .withPackage("com.example.generated")
                .build();

        CopybookDefinition definition = pojoBook.parseCopybookString(copybook);
        definition.setRecordName("EMPLOYEE-RECORD");

        String generatedCode = pojoBook.generatePojo(definition);

        log.info(generatedCode);

        assertNotNull(generatedCode);
        assertTrue(generatedCode.contains("package com.example.generated"));
        assertTrue(generatedCode.contains("class EmployeeRecord"));
        assertTrue(generatedCode.contains("private"));
        assertTrue(generatedCode.contains("@CobolField"));
    }

    @Test
    void testGeneratePojo2() throws IOException {
        String copybook = """
                000100*    COPY-ID.          SAMPLE01
                000200*    TITLE.
                000300*    DESCRIPTION.      Input interface-record BS SAMPLE01
                000400*
                000500*    VERSION.          1.
                000600*    DESIGNER.         John Doe
                000700*    AUTHOR.           Jane Smith
                000800*    CODE-READER.
                000900*
                001000*    USAGE.            COPY SAMPLE01
                001100*                           REPLACING ==:SAMPLE01:== BY ==WS==
                001300*
                001400*    DATE-WRITTEN.     Nov 2025
                001500*    DATE-LAST-UPDATE. Nov 2025
                001600*    RECORD LENGTH.    0500 BYTES.
                001700*****************************************************************************
                001800     05  WS-SAMPLE01-INPUT.
                001900       10  WS-COMMON-INPUT.
                002000         20  NM-CALLER                PIC X(0008).
                002100         20  NM-BB                    PIC X(0008).
                002200         20  CO-TRANSMISSION          PIC X(0001).
                002300         20  NM-FUNCTION              PIC X(0008).
                002600           88  CONSULT                VALUE 'CONSULT '.
                002800       10  WS-VARIABLE-INPUT.
                002900         20  WS-REQUIRED-INPUT.
                003300           30  WS-CO-TYPREQ           PIC X(0001).
                003300           30  WS-NS-ID-175TRAN       PIC 9(0006).
                003300           30  WS-DA-PUR-175TRAN      PIC X(0008).
                003300           30  WS-TE-LOGO-175REFC     PIC X(0080).
                003300           30  WS-TE-BRAND-175REFC    PIC X(0080).
                003300           30  WS-TE-CATEG-175REFC    PIC X(0030).
                003300           30  WS-TE-1LETTER-175REFC  PIC X(0001).
                003300           30  WS-TE-GEOCOORD-175REFC PIC X(0025).
                003300           30  WS-TE-FORMADR-175REFC  PIC X(0080).
                003300           30  WS-FILLER              PIC X(0059).
                003600         20  WS-OPTIONAL-INPUT.
                003300           30  WS-FILLER              PIC X(0050).
                004500       10  WS-FILLER          PIC X(0050).
                005000*
                005100* PARAMETER DESCRIPTION
                005200*----------------------
                005300* input record                                     pos 0001 - ....
                005400* common input                                     pos 0001 - 0025
                005500*   NM-CALLER         program name caller          pos 0001 - 0008
                005600*   NM-BB             program name bb              pos 0009 - 0016
                005700*   CO-TRANSMISSION   transmission code            pos 0017 - 0017
                005800*   NM-FUNCTION       function name                pos 0018 - 0025
                005900* variable input                                   pos 0026 - ....
                006000* required input                                   pos 0026 - ....
                006100*   parameter         description                  pos 0026 - ....
                006200* optional input                                   pos .... - ....
                006300*   parameter         description                  pos .... - ....
                006400* input filler                                     pos .... - ....
                006500*----------------------
                006600*    END-COPY SAMPLE01
                """;

        PojoBook pojoBook = PojoBook.builder()
                .withPackage("com.example.generated")
                .build();

        CopybookDefinition definition = pojoBook.parseCopybookString(copybook);
        definition.setRecordName("EMPLOYEE-RECORD");

        String generatedCode = pojoBook.generatePojo(definition);

        log.info(generatedCode);

        assertNotNull(generatedCode);
        assertTrue(generatedCode.contains("package com.example.generated"));
        assertTrue(generatedCode.contains("class EmployeeRecord"));
        assertTrue(generatedCode.contains("private"));
        assertTrue(generatedCode.contains("@CobolField"));
    }
}

