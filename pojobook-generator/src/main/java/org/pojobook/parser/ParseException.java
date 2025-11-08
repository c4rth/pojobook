package org.pojobook.parser;

import org.pojobook.exception.PojoBookException;

/**
 * Exception thrown during parsing operations.
 */
public class ParseException extends PojoBookException {

    private final int lineNumber;

    public ParseException(String message, int lineNumber) {
        super(message + " at line " + lineNumber);
        this.lineNumber = lineNumber;
    }

    public ParseException(String message, int lineNumber, Throwable cause) {
        super(message + " at line " + lineNumber, cause);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}

