package com.openframe.core.exception;

public class NatsException extends RuntimeException {

    public NatsException(String message) {
        super(message);
    }

    public NatsException(String message, Throwable cause) {
        super(message, cause);
    }

}
