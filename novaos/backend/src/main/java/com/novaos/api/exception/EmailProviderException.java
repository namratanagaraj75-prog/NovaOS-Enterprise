package com.novaos.api.exception;

public class EmailProviderException extends RuntimeException {
    private final String provider;
    private final String errorCode;

    public EmailProviderException(String provider, String errorCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.provider = provider;
        this.errorCode = errorCode;
    }

    public String getProvider() { return provider; }
    public String getErrorCode() { return errorCode; }
}
