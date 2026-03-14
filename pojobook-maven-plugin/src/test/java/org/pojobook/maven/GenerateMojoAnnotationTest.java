package org.pojobook.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for GenerateMojo.
 */
class GenerateMojoAnnotationTest {

    @TempDir
    Path tempDir;

    private GenerateMojo mojo;

    @BeforeEach
    void setUp() {
        mojo = new GenerateMojo();
        MavenProject project = new MavenProject();
        File baseDir = tempDir.toFile();
        project.setFile(new File(baseDir, "pom.xml"));
        project.getBuild().setDirectory(tempDir.resolve("target").toString());

        // Use reflection to set the private project field
        try {
            var field = GenerateMojo.class.getDeclaredField("project");
            field.setAccessible(true);
            field.set(mojo, project);
        } catch (Exception e) {
            fail("Failed to set project field: " + e.getMessage());
        }
    }

    @Test
    void testValidateParameters_MissingCopybookFile() {
        setMojoField("packageName", "com.example.test");

        MojoFailureException exception = assertThrows(MojoFailureException.class, () -> mojo.execute());

        assertTrue(exception.getMessage().contains("copybookFile"));
    }

    @Test
    void testValidateParameters_InvalidPackageName() throws IOException {
        Path copybookFile = createTestCopybook();
        setMojoField("copybookFile", copybookFile.toString());
        setMojoField("packageName", "123invalid");
        setMojoField("generatorType", "annotation");

        MojoFailureException exception = assertThrows(MojoFailureException.class, () -> mojo.execute());

        assertTrue(exception.getMessage().contains("Invalid Java package name"));
    }

    @Test
    void testValidateParameters_NonExistentFile() {
        setMojoField("copybookFile", "/nonexistent/file.cpy");
        setMojoField("packageName", "com.example.test");
        setMojoField("generatorType", "annotation");

        // With wildcard support, non-existent files just result in no matches (not an error)
        // The execution should complete without exception, just finding 0 files
        assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    void testGenerate_SimpleRecord() throws Exception {
        Path copybookFile = createTestCopybook();
        Path outputDir = tempDir.resolve("target/generated-sources/pojobook");

        setMojoField("copybookFile", copybookFile.toString());
        setMojoField("packageName", "com.example.test");
        setMojoField("outputDirectory", outputDir.toFile());
        setMojoField("generatorType", "annotation");

        mojo.execute();

        // Verify output directory was created
        Path packagePath = outputDir.resolve("com/example/test");
        assertTrue(Files.exists(packagePath),
                "Package directory should exist at: " + packagePath.toAbsolutePath());

        // Find the generated file
        Path actualFile = null;
        if (Files.exists(packagePath)) {
            try (var stream = Files.list(packagePath)) {
                var files = stream.toList();
                for (Path p : files) {
                    if (p.getFileName().toString().equals("EmployeeRecord.java")) {
                        actualFile = p;
                        break;
                    }
                }
            }
        }

        assertNotNull(actualFile, "EmployeeRecord.java should exist in package directory");

        // Verify content - if we can read it, it exists
        String content = Files.readString(actualFile);

        assertTrue(content.contains("package com.example.test;"), "Should contain package declaration");
        assertTrue(content.contains("public class Employeerecord") || content.contains("public class EmployeeRecord"),
                "Should contain class declaration");
        assertTrue(content.contains("private Integer empId") || content.contains("private String empId"),
                "Should contain empId field");
        assertTrue(content.contains("private String empName"), "Should contain empName field");
        assertTrue(content.contains("getEmpId()"), "Should contain getEmpId method");
        assertTrue(content.contains("setEmpId("), "Should contain setEmpId method");
    }

    @Test
    void testSkip() throws Exception {
        Path copybookFile = createTestCopybook();

        setMojoField("copybookFile", copybookFile.toString());
        setMojoField("packageName", "com.example.test");
        setMojoField("generatorType", "annotation");
        setMojoField("skip", true);

        // Should not throw exception when skipped
        assertDoesNotThrow(() -> mojo.execute());
    }

    /**
     * Create a test copybook file.
     */
    private Path createTestCopybook() throws IOException {
        Path copybookFile = tempDir.resolve("employee-record.cpy");
        String copybookContent = """
                   01  EMPLOYEE-RECORD.
                       05  EMP-ID              PIC 9(6).
                       05  EMP-NAME            PIC X(30).
                       05  EMP-SALARY          PIC 9(7)V99 COMP-3.
                       05  EMP-DEPT            PIC X(10).
                """;
        Files.writeString(copybookFile, copybookContent);
        return copybookFile;
    }

    /**
     * Helper method to set private fields using reflection.
     */
    private void setMojoField(String fieldName, Object value) {
        try {
            var field = GenerateMojo.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(mojo, value);
        } catch (Exception e) {
            fail("Failed to set field " + fieldName + ": " + e.getMessage());
        }
    }
}

