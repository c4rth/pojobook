package org.c4rth.pojobook;

import org.c4rth.pojobook.deserializer.CobolDeserializer;
import org.c4rth.pojobook.parser.CopybookDefinition;
import org.c4rth.pojobook.parser.CopybookParser;
import org.c4rth.pojobook.serializer.CobolSerializer;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * Main facade for COBOL copybook serialization and deserialization operations.
 * This class provides the primary API for working with COBOL binary data.
 */
public class CobolSerializerFacade {

    private final CobolSerializer serializer;
    private final CobolDeserializer deserializer;
    private final CopybookParser parser;

    public CobolSerializerFacade() {
        this.serializer = new CobolSerializer();
        this.deserializer = new CobolDeserializer();
        this.parser = new CopybookParser();
    }

    /**
     * Parse a COBOL copybook from a file.
     */
    public CopybookDefinition parseCopybook(Path path) throws IOException {
        return parser.parse(path);
    }

    /**
     * Parse a COBOL copybook from a string.
     */
    public CopybookDefinition parseCopybookString(String copybook) throws IOException {
        return parser.parse(new StringReader(copybook));
    }

    /**
     * Serialize a POJO to COBOL binary format.
     */
    public byte[] serialize(Object pojo, Charset charset) throws IOException {
        return serializer.serialize(pojo, charset);
    }

    /**
     * Deserialize COBOL binary data to a POJO.
     */
    public <T> T deserialize(byte[] data, Class<T> clazz, Charset charset) throws Exception {
        return deserializer.deserialize(data, clazz, charset);
    }
}

