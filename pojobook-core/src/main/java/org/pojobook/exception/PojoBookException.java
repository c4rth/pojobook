package org.pojobook.exception;

public class PojoBookException extends Exception {
    public PojoBookException(String message) {
        super(message);
    }

    public PojoBookException(String message, Throwable cause) {
        super(message, cause);
    }
}