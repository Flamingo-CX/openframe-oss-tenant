package com.openframe.sdk.fleetmdm.exception;

public class FleetMdmException extends RuntimeException {

    public FleetMdmException(String message) {
        super(message);
    }

    public FleetMdmException(String message, Throwable cause) {
        super(message, cause);
    }
}
