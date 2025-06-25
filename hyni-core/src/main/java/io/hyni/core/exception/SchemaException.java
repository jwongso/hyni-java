package io.hyni.core.exception;

/**
 * Custom exception for schema-related errors
 */
public class SchemaException extends RuntimeException {

    /**
     * Constructs a schema exception with the given message
     * @param message The error message
     */
    public SchemaException(String message) {
        super(message);
    }

    /**
     * Constructs a schema exception with the given message and cause
     * @param message The error message
     * @param cause The underlying cause
     */
    public SchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
