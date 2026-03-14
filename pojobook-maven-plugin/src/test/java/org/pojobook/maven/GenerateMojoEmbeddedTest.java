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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for embedded serialization generator integration in GenerateMojo.
 */
class GenerateMojoEmbeddedTest {

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
    void testGenerateWithEmbeddedSerializer() throws Exception {
        Path copybookFile = createTestCopybook();
        Path outputDir = tempDir.resolve("target/generated-sources/pojobook");

        setMojoField("copybookFile", copybookFile.toString());
        setMojoField("packageName", "com.example.test");
        setMojoField("outputDirectory", outputDir.toFile());
        setMojoField("generatorType", "embedded");

        mojo.execute();

        // Verify output directory was created
        Path packagePath = outputDir.resolve("com/example/test");
        assertTrue(Files.exists(packagePath),
                "Package directory should exist at: " + packagePath.toAbsolutePath());

        // Find the generated file
        Path generatedFile = packagePath.resolve("EmployeeRecord.java");
        assertTrue(Files.exists(generatedFile),
                "Generated file should exist at: " + generatedFile.toAbsolutePath());

        // Read generated content
        String content = Files.readString(generatedFile);

        // Verify it's using embedded serialization (no annotations)
        assertFalse(content.contains("@CobolField"),
                "Embedded generator should not use @CobolField annotation");
        assertFalse(content.contains("@CobolRecord"),
                "Embedded generator should not use @CobolRecord annotation");

        // Verify embedded methods exist
        assertTrue(content.contains("public int serializedSize()"),
                "Should expose record serialized size");
        assertTrue(content.contains("public void serialize(byte[] buffer)"),
                "Should have serialize(byte[]) overload for buffer reuse");
        assertTrue(content.contains("public void serialize(byte[] buffer, int offset)"),
                "Should have serialize(byte[], int) overload for buffer reuse");
        assertTrue(content.contains("public byte[] serialize(Charset charset)"),
                "Should have embedded serialize() method");
        assertTrue(content.contains("public static EmployeeRecord deserialize(byte[] data, Charset charset)"),
                "Should have embedded deserialize() method");
        assertFalse(content.contains("catch (Exception e)"),
                "Embedded generator should not emit blanket catch(Exception) in hot paths");
    }


    @Test
    void testValidateParameters_InvalidGeneratorType() throws IOException {
        Path copybookFile = createTestCopybook();

        setMojoField("copybookFile", copybookFile.toString());
        setMojoField("packageName", "com.example.test");
        setMojoField("generatorType", "invalid");

        MojoFailureException exception = assertThrows(MojoFailureException.class, () -> mojo.execute());

        assertTrue(exception.getMessage().contains("Invalid generatorType"),
                "Should reject invalid generator type");
    }

    @Test
    void testDefaultsToAnnotationGenerator() throws Exception {
        Path copybookFile = createTestCopybook();
        Path outputDir = tempDir.resolve("target/generated-sources/pojobook");

        setMojoField("copybookFile", copybookFile.toString());
        setMojoField("packageName", "com.example.test");
        setMojoField("outputDirectory", outputDir.toFile());
        // Don't set generatorType - should default to annotation

        mojo.execute();

        Path generatedFile = outputDir.resolve("com/example/test/EmployeeRecord.java");
        assertTrue(Files.exists(generatedFile));

        String content = Files.readString(generatedFile);

        // Verify it's using annotations (default behavior)
        assertTrue(content.contains("@CobolField"),
                "Default generator should use @CobolField annotation");
    }

    private Path createTestCopybook() throws IOException {
        Path copybookFile = tempDir.resolve("employee-record.cpy");
        String copybook = """
                   01  EMPLOYEE-RECORD.
                       05  EMPLOYEE-ID         PIC 9(6).
                       05  EMPLOYEE-NAME       PIC X(30).
                       05  DEPARTMENT          PIC X(10).
                       05  SALARY              PIC 9(7)V99 COMP-3.
                """;
        Files.writeString(copybookFile, copybook);
        return copybookFile;
    }

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

