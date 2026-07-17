package com.novaos.api.controller;

import com.novaos.api.exception.EmailDeliveryException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    @Test
    void returnsOnlySafeEmailFailureFields() {
        var response = new ApiExceptionHandler().emailDeliveryFailed(
                new EmailDeliveryException("internal provider detail", "EMAIL_PROVIDER_CONNECTION_FAILED",
                        new RuntimeException("secret technical exception")));

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("status", "EMAIL_FAILED")
                .containsEntry("message", "The offer letter was generated, but email delivery failed.")
                .containsEntry("errorCode", "EMAIL_PROVIDER_CONNECTION_FAILED");
        assertThat(response.getBody().toString()).doesNotContain("secret technical exception", "internal provider detail");
    }
}
