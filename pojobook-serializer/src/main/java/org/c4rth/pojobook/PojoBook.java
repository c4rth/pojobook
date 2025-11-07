package org.c4rth.pojobook;

import org.c4rth.pojobook.parser.CopybookDefinition;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * Backward compatibility wrapper for tests.
 * Delegates to CobolSerializerFacade for serialization operations.
 * For code generation, use the PojoBook class in pojobook-generator module.
 */
public class PojoBook {

    private final CobolSerializerFacade facade;

    public PojoBook() {
        this.facade = new CobolSerializerFacade();
    }

    public CopybookDefinition parseCopybook(Path path) throws IOException {
        return facade.parseCopybook(path);
    }

    public CopybookDefinition parseCopybookString(String copybook) throws IOException {
        return facade.parseCopybookString(copybook);
    }

    public byte[] serialize(Object pojo) throws IOException {
        return facade.serialize(pojo, Charset.forName("CP1047"));
    }

    public byte[] serialize(Object pojo, Charset charset) throws IOException {
        return facade.serialize(pojo, charset);
    }

    public <T> T deserialize(byte[] data, Class<T> clazz) throws Exception {
        return facade.deserialize(data, clazz, Charset.forName("CP1047"));
    }

    public <T> T deserialize(byte[] data, Class<T> clazz, Charset charset) throws Exception {
        return facade.deserialize(data, clazz, charset);
    }
}

