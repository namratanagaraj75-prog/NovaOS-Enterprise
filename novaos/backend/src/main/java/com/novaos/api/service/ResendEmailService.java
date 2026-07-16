package com.novaos.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResendEmailService {
    private static final Logger logger = LoggerFactory.getLogger(ResendEmailService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String sendEmail(String toEmail, String subject, String textBody, String attachmentName, byte[] attachmentBytes) throws Exception {
        String apiKey = System.getenv("RESEND_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("RESEND_API_KEY environment variable is missing.");
        }

        String fromEmail = System.getenv("RESEND_FROM_EMAIL");
        if (!StringUtils.hasText(fromEmail)) {
            fromEmail = "onboarding@resend.dev";
        }

        String fromName = System.getenv("RESEND_FROM_NAME");
        String fromValue = StringUtils.hasText(fromName) ? fromName + " <" + fromEmail + ">" : fromEmail;

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", fromValue);
        payload.put("to", List.of(toEmail));
        payload.put("subject", subject);
        payload.put("text", textBody);

        if (attachmentName != null && attachmentBytes != null) {
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("filename", attachmentName);
            attachment.put("content", Base64.getEncoder().encodeToString(attachmentBytes));
            payload.put("attachments", List.of(attachment));
        }

        String jsonBody = objectMapper.writeValueAsString(payload);

        logger.info("Sending email via Resend to recipient={}, subject={}", toEmail, subject);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorMsg = "Resend API returned status code " + response.statusCode();
            try {
                Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
                if (responseMap.containsKey("message")) {
                    errorMsg = String.valueOf(responseMap.get("message"));
                }
            } catch (Exception ignored) {}
            throw new RuntimeException(errorMsg);
        }

        try {
            Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
            if (responseMap.containsKey("id")) {
                return String.valueOf(responseMap.get("id"));
            }
        } catch (Exception ignored) {}
        return "";
    }
}
