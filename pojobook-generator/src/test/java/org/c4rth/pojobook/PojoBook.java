package org.c4rth.pojobook;

import org.c4rth.pojobook.generator.AnnotationPojoGenerator;
import org.c4rth.pojobook.parser.CopybookDefinition;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * Main facade for the PojoBook library.
 * Provides convenient access to all library features including code generation and serialization.
 */
public class PojoBook {

    private final CobolSerializerFacade serializerFacade;
    private final AnnotationPojoGenerator generator;

    public PojoBook() {
        this.serializerFacade = new CobolSerializerFacade();
        this.generator = new AnnotationPojoGenerator();
    }

    /**
     * Parse a copybook file.
     */
    public CopybookDefinition parseCopybook(Path path) throws IOException {
        return serializerFacade.parseCopybook(path);
    }

    /**
     * Parse a copybook from a string.
     */
    public CopybookDefinition parseCopybookString(String copybook) throws IOException {
        return serializerFacade.parseCopybookString(copybook);
    }

    /**
     * Generate a POJO class from a copybook definition.
     */
    public String generatePojo(CopybookDefinition definition) {
        return generator.generate(definition);
    }

    /**
     * Generate POJO class from copybook file.
     */
    public String generatePojo(Path copybookPath) throws IOException {
        CopybookDefinition definition = parseCopybook(copybookPath);
        return generatePojo(definition);
    }

    /**
     * Serialize a POJO to COBOL binary format.
     */
    public byte[] serialize(Object pojo, Charset charset) throws IOException {
        return serializerFacade.serialize(pojo, charset);
    }

    /**
     * Deserialize COBOL binary data to a POJO.
     */
    public <T> T deserialize(byte[] data, Class<T> clazz, Charset charset) throws Exception {
        return serializerFacade.deserialize(data, clazz, charset);
    }

    /**
     * Get the POJO generator for configuration.
     */
    public AnnotationPojoGenerator getGenerator() {
        return generator;
    }

    /**
     * Create a new PojoBook builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for PojoBook configuration.
     */
    public static class Builder {
        private String packageName = "org.c4rth.generated";

        public Builder withPackage(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public PojoBook build() {
            PojoBook pojoBook = new PojoBook();
            pojoBook.getGenerator().withPackage(packageName);
            return pojoBook;
        }
    }
}

