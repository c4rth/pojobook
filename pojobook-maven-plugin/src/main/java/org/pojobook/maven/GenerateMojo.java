package org.pojobook.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.pojobook.generator.GeneratorBuilder;
import org.pojobook.parser.CopybookDefinition;
import org.pojobook.parser.CopybookParser;
import org.pojobook.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Pattern;
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
     * - "standalone": Generates POJOs with standalone serialization/deserialization methods
     */
    @Parameter(property = "generatorType", defaultValue = "standalone")
    private String generatorType;

    /**
     * Disable bounds and length validation in generated setters for low-latency systems.
     */
    @Parameter(property = "disableValidation", defaultValue = "false")
    private boolean disableValidation;

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
            List<File> copybookFiles = resolveCopybookFiles();

            if (copybookFiles.isEmpty()) {
                getLog().warn("No copybook files found matching pattern: " + copybookFile);
                return;
            }

            getLog().info("Found " + copybookFiles.size() + " copybook file(s) to process");

            for (File file : copybookFiles) {
                processOneCopybookFile(file);
            }

            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
            getLog().info("POJO generation completed successfully for " + copybookFiles.size() + " file(s)");

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read copybook file or write output", e);
        } catch (ParseException e) {
            throw new MojoExecutionException("Error parsing copybook: " + e.getMessage(), e);
        }
    }

    /**
     * Process a single copybook file.
     */
    private void processOneCopybookFile(File file) throws IOException, ParseException {
        getLog().info("Parsing copybook file: " + file.getAbsolutePath());

        CopybookParser parser = new CopybookParser();
        CopybookDefinition definition = parser.parse(file.toPath());

        getLog().info("Copybook parsed successfully. Record: " +
                (definition.getRecordName() != null ? definition.getRecordName() : "Anonymous") +
                ", Fields: " + definition.getFields().size());

        // Determine class name from record name or filename
        String className = definition.getRecordName() != null && !definition.getRecordName().isEmpty()
                ? toClassName(definition.getRecordName())
                : toClassName(getFileNameWithoutExtension(file));

        if (definition.getRecordName() == null || definition.getRecordName().isEmpty()) {
            definition.setRecordName(className);
        }

        // Generate output path
        Path outputFile = Paths.get(outputDirectory.getAbsolutePath())
                .resolve(packageName.replace('.', File.separatorChar))
                .resolve(className + ".java");

        getLog().info("Generating POJO class: " + className);
        getLog().info("Generator type: " + generatorType);
        getLog().info("Output file: " + outputFile.toAbsolutePath());

        // Generate the POJO using GeneratorBuilder
        if ("standalone".equalsIgnoreCase(generatorType)) {
            GeneratorBuilder.standaloneGenerator(packageName)
                    .withDisableValidation(disableValidation)
                    .generateToFile(definition, outputFile);
        } else {
            GeneratorBuilder.annotationGenerator(packageName)
                    .withDisableValidation(disableValidation)
                    .generateToFile(definition, outputFile);
        }

        getLog().info("Successfully generated: " + className);
    }

    /**
     * Resolve copybook files from the pattern (supports wildcards).
     */
    private List<File> resolveCopybookFiles() throws IOException {
        List<File> files = new ArrayList<>();

        // Check if pattern contains wildcards
        if (copybookFile.contains("*") || copybookFile.contains("?")) {
            files.addAll(resolveWildcardPattern(copybookFile));
        } else {
            Path basePath = new File(copybookFile).isAbsolute()
                    ? Paths.get(copybookFile)
                    : project.getBasedir().toPath().resolve(copybookFile);
            File singleFile = basePath.toFile();
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
        String normalizedPattern = normalizeSeparators(pattern);
        String absolutePattern = new File(pattern).isAbsolute()
                ? normalizedPattern
                : normalizeSeparators(new File(project.getBasedir(), pattern).getAbsolutePath());

        // Find the base directory (part before first wildcard)
        int firstWildcard = Math.min(
                absolutePattern.indexOf('*') != -1 ? absolutePattern.indexOf('*') : Integer.MAX_VALUE,
                absolutePattern.indexOf('?') != -1 ? absolutePattern.indexOf('?') : Integer.MAX_VALUE
        );

        if (firstWildcard == Integer.MAX_VALUE) {
            return matchedFiles; // No wildcard found
        }

        // Find the last directory separator before the wildcard
        int lastSeparator = absolutePattern.lastIndexOf('/', firstWildcard);

        String baseDir = lastSeparator >= 0 ? absolutePattern.substring(0, lastSeparator) : ".";
        String filePattern = lastSeparator >= 0 ? absolutePattern.substring(lastSeparator + 1) : absolutePattern;

        Path basePath = Paths.get(baseDir);
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            getLog().warn("Base directory does not exist: " + basePath);
            return matchedFiles;
        }

        Pattern regex = Pattern.compile(globToRegex(filePattern));

        try (Stream<Path> paths = Files.walk(basePath)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> regex.matcher(normalizeSeparators(basePath.relativize(p).toString())).matches())
                    .sorted()
                    .forEach(p -> matchedFiles.add(p.toFile()));
        }

        return matchedFiles;
    }

    /**
     * Check if filename matches pattern with wildcards.
     */
    private String normalizeSeparators(String value) {
        return value.replace('\\', '/');
    }

    /**
     * Convert a glob pattern to a regex using '/' as the directory separator.
     */
    private String globToRegex(String pattern) {
        String normalizedPattern = normalizeSeparators(pattern);
        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < normalizedPattern.length(); i++) {
            char ch = normalizedPattern.charAt(i);

            if (ch == '*') {
                boolean doubleStar = i + 1 < normalizedPattern.length() && normalizedPattern.charAt(i + 1) == '*';
                if (doubleStar) {
                    boolean followedBySlash = i + 2 < normalizedPattern.length() && normalizedPattern.charAt(i + 2) == '/';
                    regex.append(followedBySlash ? "(?:.*/)?" : ".*");
                    i += followedBySlash ? 2 : 1;
                } else {
                    regex.append("[^/]*");
                }
                continue;
            }

            if (ch == '?') {
                regex.append("[^/]");
                continue;
            }

            if (".[]{}()+-^$|".indexOf(ch) >= 0) {
                regex.append('\\');
            }

            regex.append(ch);
        }

        regex.append('$');
        return regex.toString();
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

        if (generatorType != null &&
                !generatorType.equalsIgnoreCase("annotation") &&
                !generatorType.equalsIgnoreCase("standalone")) {
            throw new MojoFailureException(
                    "Invalid generatorType: " + generatorType + ". Must be 'annotation' or 'standalone'");
        }
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
    private String getFileNameWithoutExtension(Object fileOrName) {
        String name = fileOrName instanceof File ? ((File) fileOrName).getName() : fileOrName.toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }
}
