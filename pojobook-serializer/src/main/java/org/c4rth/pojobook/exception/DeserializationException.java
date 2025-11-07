package org.c4rth.pojobook.exception;

/**
 * Exception thrown during deserialization operations.
 */
public class DeserializationException extends PojoBookException {

    public DeserializationException(String message) {
        super(message);
    }

    public DeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

