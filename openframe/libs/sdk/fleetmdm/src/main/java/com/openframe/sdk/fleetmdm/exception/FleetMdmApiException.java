package com.openframe.sdk.fleetmdm.exception;

public class FleetMdmApiException extends FleetMdmException {

    private final int statusCode;
    private final String responseBody;

    public FleetMdmApiException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String toString() {
        return "TacticalRmmApiException(statusCode=" + this.getStatusCode() + ", responseBody=" + this.getResponseBody() + ")";
    }
}
