package com.novaos.api.controller;

import com.novaos.api.exception.EmailDeliveryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<Map<String, Object>> emailDeliveryFailed(EmailDeliveryException error) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "success", false,
                "status", "EMAIL_FAILED",
                "message", "The offer letter was generated, but email delivery failed.",
                "errorCode", error.getErrorCode()));
    }
}
