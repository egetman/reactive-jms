package com.github.egetman.source.exceptions;

@SuppressWarnings({"unused", "WeakerAccess"})
public class SourceSetupException extends RuntimeException {

    public SourceSetupException() {
    }

    public SourceSetupException(String message) {
        super(message);
    }

    public SourceSetupException(String message, Throwable cause) {
        super(message, cause);
    }

    public SourceSetupException(Throwable cause) {
        super(cause);
    }

}
