package org.pojobook.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.pojobook.generator.AnnotationPojoGenerator;
import org.pojobook.generator.EmbeddedSerializationPojoGenerator;
import org.pojobook.parser.CopybookDefinition;
import org.pojobook.parser.CopybookParser;
import org.pojobook.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Maven plugin goal to generate POJO classes from COBOL copybook files.
 * This Mojo parses COBOL copybook definitions and generates corresponding Java POJO classes
 * with proper annotations, getters/setters, and toString methods.
 * The generated sources are automatically added to the project's compile source roots.
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        threadSafe = true
)
public class GenerateMojo extends AbstractMojo {

    /**
     * The Maven project instance.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Path to the COBOL copybook file(s) to parse.
     * Can be absolute or relative to the project basedir.
     * Supports wildcards: e.g., "src/main/resources/copybooks/*.cpy"
     * or "src/main/resources/copybooks/**\/*.cbl"
     */
    @Parameter(property = "copybookFile", required = true)
    private String copybookFile;

    /**
     * Java package name for the generated POJO classes.
     */
    @Parameter(property = "packageName", required = true)
    private String packageName;

    /**
     * Output directory for generated sources.
     * Defaults to target/generated-sources/pojobook
     */
    @Parameter(
            property = "outputDirectory",
            defaultValue = "${project.build.directory}/generated-sources/pojobook"
    )
    private File outputDirectory;

    /**
     * Generator type to use for POJO generation.
     * Options:
     * - "annotation": Generates POJOs with annotations, requires external serializer
     * - "embedded": Generates POJOs with embedded serialization/deserialization methods
     */
    @Parameter(property = "generatorType", defaultValue = "embedded")
    private String generatorType;

    /**
     * Skip the plugin execution.
     */
    @Parameter(property = "pojobook.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping POJO generation (pojobook.skip=true)");
            return;
        }

        validateParameters();

        try {
            // Resolve copybook files (may include wildcards)
            List<File> copybookFiles = resolveCopybookFiles();

            if (copybookFiles.isEmpty()) {
                getLog().warn("No copybook files found matching pattern: " + copybookFile);
                return;
            }

            getLog().info("Found " + copybookFiles.size() + " copybook file(s) to process");

            // Process each copybook file
            for (File file : copybookFiles) {
                processOneCopybookFile(file);
            }

            // Add output directory to compile source roots (only once)
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

            getLog().info("POJO generation completed successfully for " + copybookFiles.size() + " file(s)");

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read copybook file or write output", e);
        } catch (Exception e) {
            throw new MojoExecutionException("Error generating POJO from copybook", e);
        }
    }

    /**
     * Process a single copybook file.
     */
    private void processOneCopybookFile(File file) throws IOException, ParseException {
        getLog().info("Parsing copybook file: " + file.getAbsolutePath());

        // Parse the copybook
        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(file.toPath());

        getLog().info("Copybook parsed successfully. Record: " +
                (definition.getRecordName() != null ? definition.getRecordName() : "Anonymous") +
                ", Fields: " + definition.getFields().size());

        // Determine class name - use record name if available, otherwise use filename
        String className = toClassName(definition.getRecordName() != null ?
                definition.getRecordName() :
                getFileNameWithoutExtension(file));

        // If record name was null/anonymous, set it to the derived class name
        // so the generator uses the correct name
        if (definition.getRecordName() == null || definition.getRecordName().isEmpty()) {
            definition.setRecordName(className);
        }

        // Determine output file path
        Path packagePath = Paths.get(outputDirectory.getAbsolutePath())
                .resolve(packageName.replace('.', File.separatorChar));

        Path outputFile = packagePath.resolve(className + ".java");

        getLog().info("Generating POJO class: " + className);
        getLog().info("Generator type: " + generatorType);
        getLog().info("Output file: " + outputFile.toAbsolutePath());

        // Generate the POJO based on generator type
        if ("embedded".equalsIgnoreCase(generatorType)) {
            generateWithEmbeddedSerializer(definition, outputFile);
        } else {
            generateWithAnnotations(definition, outputFile);
        }

        getLog().info("Successfully generated: " + className);
    }

    /**
     * Resolve copybook files from the pattern (supports wildcards).
     */
    private List<File> resolveCopybookFiles() throws IOException {
        List<File> files = new ArrayList<>();

        // Check if copybookFile contains wildcards
        if (copybookFile.contains("*") || copybookFile.contains("?")) {
            // Pattern contains wildcards - build absolute pattern string manually
            String pattern;
            if (new File(copybookFile).isAbsolute()) {
                pattern = copybookFile;
            } else {
                pattern = project.getBasedir().getAbsolutePath() + File.separator + copybookFile.replace("/", File.separator);
            }
            files.addAll(resolveWildcardPattern(pattern));
        } else {
            // No wildcards - treat as single file
            Path patternPath;
            if (new File(copybookFile).isAbsolute()) {
                patternPath = Paths.get(copybookFile);
            } else {
                patternPath = project.getBasedir().toPath().resolve(copybookFile);
            }
            File singleFile = patternPath.toFile();
            if (singleFile.exists() && singleFile.isFile()) {
                files.add(singleFile);
            }
        }

        return files;
    }

    /**
     * Resolve wildcard pattern to actual files.
     */
    private List<File> resolveWildcardPattern(String pattern) throws IOException {
        List<File> matchedFiles = new ArrayList<>();

        // Find the base directory (part before first wildcard)
        int firstWildcard = Math.min(
                pattern.indexOf('*') != -1 ? pattern.indexOf('*') : Integer.MAX_VALUE,
                pattern.indexOf('?') != -1 ? pattern.indexOf('?') : Integer.MAX_VALUE
        );

        if (firstWildcard == Integer.MAX_VALUE) {
            // No wildcard found
            return matchedFiles;
        }

        // Find the last directory separator before the wildcard
        int lastSeparator = Math.max(
                pattern.lastIndexOf('/', firstWildcard),
                pattern.lastIndexOf('\\', firstWildcard)
        );

        String baseDir = lastSeparator > 0 ? pattern.substring(0, lastSeparator) : ".";
        String filePattern = pattern.substring(lastSeparator + 1);

        Path basePath = Paths.get(baseDir);
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            getLog().warn("Base directory does not exist: " + basePath);
            return matchedFiles;
        }

        // Check if pattern includes ** (recursive)
        boolean recursive = filePattern.contains("**");

        if (recursive) {
            // Handle ** recursive pattern
            String simplePattern = filePattern.replace("**" + File.separator, "")
                    .replace("**", "");

            try (Stream<Path> paths = Files.walk(basePath)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> matchesPattern(p.getFileName().toString(), simplePattern))
                        .forEach(p -> matchedFiles.add(p.toFile()));
            }
        } else {
            // Non-recursive pattern
            try (Stream<Path> paths = Files.list(basePath)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> matchesPattern(p.getFileName().toString(), filePattern))
                        .forEach(p -> matchedFiles.add(p.toFile()));
            }
        }

        return matchedFiles;
    }

    /**
     * Check if filename matches pattern with wildcards.
     */
    private boolean matchesPattern(String filename, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern.replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return filename.matches(regex);
    }

    /**
     * Validate plugin parameters.
     */
    private void validateParameters() throws MojoFailureException {
        if (copybookFile == null || copybookFile.trim().isEmpty()) {
            throw new MojoFailureException("Parameter 'copybookFile' is required");
        }


        if (packageName == null || packageName.trim().isEmpty()) {
            throw new MojoFailureException("Parameter 'packageName' is required");
        }

        if (!isValidPackageName(packageName)) {
            throw new MojoFailureException("Invalid Java package name: " + packageName);
        }

        // Validate generator type
        if (generatorType != null &&
                !generatorType.equalsIgnoreCase("annotation") &&
                !generatorType.equalsIgnoreCase("embedded")) {
            throw new MojoFailureException(
                    "Invalid generatorType: " + generatorType + ". Must be 'annotation' or 'embedded'");
        }
    }

    /**
     * Generate POJO using annotation-based generator.
     */
    private void generateWithAnnotations(CopybookDefinition definition, Path outputFile) throws IOException {
        AnnotationPojoGenerator generator = new AnnotationPojoGenerator()
                .withPackage(packageName);

        generator.generateToFile(definition, outputFile);
    }

    /**
     * Generate POJO using embedded serialization generator.
     */
    private void generateWithEmbeddedSerializer(CopybookDefinition definition, Path outputFile) throws IOException {

        EmbeddedSerializationPojoGenerator generator = new EmbeddedSerializationPojoGenerator()
                .withPackage(packageName);

        generator.generateToFile(definition, outputFile);
    }

    /**
     * Validate Java package name.
     */
    private boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        String[] parts = packageName.split("\\.");
        for (String part : parts) {
            if (!part.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convert a name to a valid Java class name.
     */
    private String toClassName(String name) {
        if (name == null || name.isEmpty()) {
            return "CobolRecord";
        }

        // Remove file extension if present
        name = getFileNameWithoutExtension(name);

        // Convert to PascalCase
        String[] parts = name.split("[-_\\s]+");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }

        String className = result.toString();

        // Ensure it starts with a letter
        if (!className.isEmpty() && !Character.isJavaIdentifierStart(className.charAt(0))) {
            className = "Record" + className;
        }

        return className.isEmpty() ? "CobolRecord" : className;
    }

    /**
     * Get filename without extension.
     */
    private String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }

    /**
     * Get filename without extension from string.
     */
    private String getFileNameWithoutExtension(String name) {
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }
}

