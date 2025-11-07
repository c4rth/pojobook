package org.c4rth.pojobook.exception;

/**
 * Exception thrown during serialization operations.
 */
public class SerializationException extends PojoBookException {

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

