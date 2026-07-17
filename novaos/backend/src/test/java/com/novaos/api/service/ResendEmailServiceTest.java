package com.novaos.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaos.api.exception.EmailProviderException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> idempotencyKey = new AtomicReference<>();
    private HttpServer server;
    private int responseStatus;
    private String responseBody;

    @BeforeEach
    void startServer() throws Exception {
        responseStatus = 200;
        responseBody = "{\"id\":\"resend-message-123\"}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach void stopServer() { server.stop(0); }

    @Test
    void sendsCandidatePdfAsBase64UsingRailwayVariables() throws Exception {
        ResendEmailService service = service("re_secret_key", "offers@verified.example", "Nova HR");
        byte[] pdf = "%PDF-test".getBytes(StandardCharsets.UTF_8);
        EmailDeliveryReceipt receipt = service.send(new EmailDeliveryRequest(
                "request-42-attempt-1", "candidate@example.com", "Offer of Employment | Nova OS",
                "Offer body", "Offer_Letter.pdf", pdf));

        assertThat(receipt).isEqualTo(new EmailDeliveryReceipt("RESEND_API", "resend-message-123"));
        JsonNode payload = mapper.readTree(requestBody.get());
        assertThat(payload.path("from").asText()).isEqualTo("Nova HR <offers@verified.example>");
        assertThat(payload.path("to").get(0).asText()).isEqualTo("candidate@example.com");
        assertThat(payload.path("attachments").get(0).path("filename").asText()).isEqualTo("Offer_Letter.pdf");
        assertThat(payload.path("attachments").get(0).path("content").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(pdf));
        assertThat(requestBody.get()).doesNotContain("re_secret_key");
        assertThat(authorization.get()).isEqualTo("Bearer re_secret_key");
        assertThat(idempotencyKey.get()).isEqualTo("request-42-attempt-1");
    }

    @Test
    void mapsAuthenticationRejectionSafely() {
        responseStatus = 401;
        responseBody = "{\"name\":\"validation_error\",\"message\":\"bad key internal detail\"}";
        assertThatThrownBy(() -> service("bad-key", "offers@verified.example", "Nova HR").send(request()))
                .isInstanceOfSatisfying(EmailProviderException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("EMAIL_PROVIDER_AUTH_FAILED");
                    assertThat(error.getMessage()).doesNotContain("bad key internal detail");
                });
    }

    @Test
    void mapsUnverifiedOrInvalidSenderRejectionSafely() {
        responseStatus = 422;
        responseBody = "{\"name\":\"validation_error\",\"message\":\"domain is not verified\"}";
        assertThatThrownBy(() -> service("key", "offers@unverified.example", "Nova HR").send(request()))
                .isInstanceOfSatisfying(EmailProviderException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("EMAIL_PROVIDER_REJECTED"));
    }

    @Test
    void requiresExplicitResendSenderAndKey() {
        assertThatThrownBy(() -> service("", "", "Nova HR").send(request()))
                .isInstanceOfSatisfying(EmailProviderException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("EMAIL_PROVIDER_AUTH_FAILED"));
    }

    private ResendEmailService service(String key, String from, String name) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails");
        return new ResendEmailService(mapper, HttpClient.newHttpClient(), endpoint, key, from, name);
    }

    private EmailDeliveryRequest request() {
        return new EmailDeliveryRequest("request-1", "candidate@example.com", "Subject", "Body", "offer.pdf", new byte[]{1, 2, 3});
    }
}
