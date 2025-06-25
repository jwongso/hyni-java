package io.hyni.spring.boot.exception;

public class HyniException extends RuntimeException {

    public HyniException(String message) {
        super(message);
    }

    public HyniException(String message, Throwable cause) {
        super(message, cause);
    }
}
