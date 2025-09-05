package com.openframe.sdk.tacticalrmm.exception;

public class TacticalRmmException extends RuntimeException {

    public TacticalRmmException(String message) {
        super(message);
    }

    public TacticalRmmException(String message, Throwable cause) {
        super(message, cause);
    }
}
