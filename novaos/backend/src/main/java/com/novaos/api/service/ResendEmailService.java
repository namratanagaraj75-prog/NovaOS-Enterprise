package com.novaos.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaos.api.exception.EmailProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResendEmailService {
    private static final Logger logger = LoggerFactory.getLogger(ResendEmailService.class);
    private static final URI EMAILS_ENDPOINT = URI.create("https://api.resend.com/emails");
    private static final int MAX_ENCODED_EMAIL_BYTES = 40 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String from;
    private final String fromName;

    @Autowired
    public ResendEmailService(ObjectMapper objectMapper,
                              @Value("${nova.resend.api-key:}") String apiKey,
                              @Value("${nova.resend.from:}") String from,
                              @Value("${nova.resend.from-name:Nova HR}") String fromName) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                EMAILS_ENDPOINT, apiKey, from, fromName);
    }

    ResendEmailService(ObjectMapper objectMapper, HttpClient httpClient, URI endpoint,
                       String apiKey, String from, String fromName) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.apiKey = trim(apiKey);
        this.from = trim(from);
        this.fromName = trim(fromName);
    }

    public String providerId() { return "RESEND_API"; }

    public EmailDeliveryReceipt send(EmailDeliveryRequest request) {
        validateConfiguration();
        try {
            String encodedAttachment = Base64.getEncoder().encodeToString(request.attachmentBytes());
            if (encodedAttachment.getBytes(StandardCharsets.US_ASCII).length > MAX_ENCODED_EMAIL_BYTES)
                throw failure("EMAIL_PROVIDER_REJECTED", "The email attachment exceeds the provider limit.", null);

            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("filename", request.attachmentName());
            attachment.put("content", encodedAttachment);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", formattedFrom());
            payload.put("to", List.of(request.recipient()));
            payload.put("subject", request.subject());
            payload.put("text", request.textBody());
            payload.put("attachments", List.of(attachment));

            logger.info("Email provider send: provider={}, recipient={}, sender={}, subject={}, attachmentSizeBytes={}",
                    providerId(), request.recipient(), from, request.subject(), request.attachmentBytes().length);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", request.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw responseFailure(response.statusCode(), response.body());
            JsonNode responseBody = objectMapper.readTree(response.body());
            String id = responseBody.path("id").asText("");
            if (!StringUtils.hasText(id))
                throw failure("EMAIL_DELIVERY_FAILED", "The provider response did not include a message identifier.", null);
            return new EmailDeliveryReceipt(providerId(), id);
        } catch (EmailProviderException error) {
            throw error;
        } catch (HttpTimeoutException | ConnectException error) {
            logger.error("Resend API connection failed: rootExceptionClass={}", error.getClass().getName(), error);
            throw failure("EMAIL_PROVIDER_CONNECTION_FAILED", "The email provider could not be reached.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            logger.error("Resend API request was interrupted: rootExceptionClass={}", error.getClass().getName(), error);
            throw failure("EMAIL_PROVIDER_CONNECTION_FAILED", "The email provider request was interrupted.", error);
        } catch (IOException error) {
            logger.error("Resend API I/O failure: rootExceptionClass={}", error.getClass().getName(), error);
            throw failure("EMAIL_PROVIDER_CONNECTION_FAILED", "The email provider could not be reached.", error);
        } catch (Exception error) {
            logger.error("Resend API delivery failure: rootExceptionClass={}", error.getClass().getName(), error);
            throw failure("EMAIL_DELIVERY_FAILED", "Email delivery failed.", error);
        }
    }

    // Retained for the existing employee welcome-email caller.
    public String sendEmail(String toEmail, String subject, String textBody,
                            String attachmentName, byte[] attachmentBytes) {
        byte[] bytes = attachmentBytes == null ? new byte[0] : attachmentBytes;
        String filename = attachmentName == null ? "attachment.txt" : attachmentName;
        EmailDeliveryRequest request = new EmailDeliveryRequest(
                "welcome-" + Integer.toUnsignedString((toEmail + subject).hashCode()),
                toEmail, subject, textBody, filename, bytes);
        if (attachmentBytes == null) return sendWithoutAttachment(request).messageId();
        return send(request).messageId();
    }

    private EmailDeliveryReceipt sendWithoutAttachment(EmailDeliveryRequest request) {
        validateConfiguration();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", formattedFrom());
            payload.put("to", List.of(request.recipient()));
            payload.put("subject", request.subject());
            payload.put("text", request.textBody());
            HttpRequest httpRequest = HttpRequest.newBuilder().uri(endpoint).timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .header("Idempotency-Key", request.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw responseFailure(response.statusCode(), response.body());
            return new EmailDeliveryReceipt(providerId(), objectMapper.readTree(response.body()).path("id").asText(""));
        } catch (EmailProviderException error) {
            throw error;
        } catch (Exception error) {
            throw failure("EMAIL_PROVIDER_CONNECTION_FAILED", "The email provider could not be reached.", error);
        }
    }

    private EmailProviderException responseFailure(int status, String responseBody) {
        String providerType = "unknown";
        try { providerType = objectMapper.readTree(responseBody).path("name").asText("unknown"); }
        catch (Exception ignored) { }
        logger.error("Resend API rejected email: httpStatus={}, providerErrorType={}", status, providerType);
        String code = status == 401 || status == 403 ? "EMAIL_PROVIDER_AUTH_FAILED" : "EMAIL_PROVIDER_REJECTED";
        return failure(code, "The email provider rejected the delivery request.", null);
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(apiKey))
            throw failure("EMAIL_PROVIDER_AUTH_FAILED", "The email provider credentials are not configured.", null);
        if (!StringUtils.hasText(from))
            throw failure("EMAIL_PROVIDER_REJECTED", "The email provider sender is not configured.", null);
    }

    private String formattedFrom() {
        return StringUtils.hasText(fromName) ? fromName + " <" + from + ">" : from;
    }

    private EmailProviderException failure(String code, String message, Throwable cause) {
        return new EmailProviderException(providerId(), code, message, cause);
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }
}
