package com.github.egetman.source.exceptions;

@SuppressWarnings("unused")
public class JmsSourceUnrecoverableException extends RuntimeException {

    public JmsSourceUnrecoverableException(String message) {
        super(message);
    }

    public JmsSourceUnrecoverableException(String message, Throwable cause) {
        super(message, cause);
    }

    public JmsSourceUnrecoverableException(Throwable cause) {
        super(cause);
    }
}
