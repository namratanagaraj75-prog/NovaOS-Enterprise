package com.novaos.api.exception;

public class EmailProviderException extends RuntimeException {
    private final String provider;
    private final String errorCode;
    private final int httpStatus;
    private final String providerMessage;

    public EmailProviderException(String provider, String errorCode, String safeMessage, Throwable cause) {
        this(provider, errorCode, 0, safeMessage, safeMessage, cause);
    }

    public EmailProviderException(String provider, String errorCode, int httpStatus,
                                  String providerMessage, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.provider = provider;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.providerMessage = providerMessage;
    }

    public String getProvider() { return provider; }
    public String getErrorCode() { return errorCode; }
    public int getHttpStatus() { return httpStatus; }
    public String getProviderMessage() { return providerMessage; }
}
