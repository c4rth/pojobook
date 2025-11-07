package org.c4rth.pojobook.exception;

public class PojoBookException extends RuntimeException {
    public PojoBookException(String message) {
        super(message);
    }

    public PojoBookException(String message, Throwable cause) {
        super(message, cause);
    }
}