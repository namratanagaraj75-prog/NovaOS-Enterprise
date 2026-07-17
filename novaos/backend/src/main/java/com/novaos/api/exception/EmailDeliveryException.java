package com.novaos.api.exception;

public class EmailDeliveryException extends RuntimeException {
    private final String errorCode;

    public EmailDeliveryException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
