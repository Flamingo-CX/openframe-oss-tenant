package com.openframe.sdk.tacticalrmm.exception;

public class TacticalRmmApiException extends TacticalRmmException {

    private final int statusCode;
    private final String responseBody;

    public TacticalRmmApiException(String message, int statusCode, String responseBody) {
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
