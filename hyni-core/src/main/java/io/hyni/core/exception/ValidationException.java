package io.hyni.core.exception;

/**
 * Custom exception for validation-related errors
 */
public class ValidationException extends RuntimeException {

    /**
     * Constructs a validation exception with the given message
     * @param message The error message
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a validation exception with the given message and cause
     * @param message The error message
     * @param cause The underlying cause
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
