package org.pojobook;

import org.pojobook.deserializer.CobolDeserializer;
import org.pojobook.exception.DeserializationException;
import org.pojobook.exception.SerializationException;
import org.pojobook.serializer.CobolSerializer;

import java.nio.charset.Charset;

/**
 * Backward compatibility wrapper for tests.
 * Delegates to CobolSerializer for serialization operations.
 * Delegates to CobolDeserializer for deserialization operations.
 */
public class PojoBook {

    private final CobolSerializer serializer;
    private final CobolDeserializer deserializer;

    public PojoBook() {
        this.serializer = new CobolSerializer();
        this.deserializer = new CobolDeserializer();
    }

    /**
     * Serialize a POJO to COBOL binary format.
     */
    public byte[] serialize(Object pojo, Charset charset) throws SerializationException {
        return serializer.serialize(pojo, charset);
    }

    /**
     * Serialize a POJO to COBOL binary format with default charset CP1047.
     */
    public byte[] serialize(Object pojo) throws SerializationException {
        return serializer.serialize(pojo, Charset.forName("CP1047"));
    }

    /**
     * Deserialize COBOL binary data to a POJO.
     */
    public <T> T deserialize(byte[] data, Class<T> clazz, Charset charset) throws DeserializationException {
        return deserializer.deserialize(data, clazz, charset);
    }

    /**
     * Deserialize COBOL binary data to a POJO with default charset CP1047.
     */
    public <T> T deserialize(byte[] data, Class<T> clazz) throws DeserializationException {
        return deserializer.deserialize(data, clazz, Charset.forName("CP1047"));
    }
}

