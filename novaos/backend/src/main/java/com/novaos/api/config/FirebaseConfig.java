package com.novaos.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.config.path}")
    private String configPath;

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${firebase.service-account-json-base64:}")
    private String serviceAccountJsonBase64;

    @Value("${firebase.project-id}")
    private String projectId;

    @Value("${firebase.database.url}")
    private String databaseUrl;

    @Value("${firebase.private-key-id:}")
    private String privateKeyId;

    @Value("${firebase.private-key:}")
    private String privateKey;

    @Value("${firebase.client-email:}")
    private String clientEmail;

    @Value("${firebase.client-id:}")
    private String clientId;

    private final ResourceLoader resourceLoader;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initialize() {
        try {
            boolean hasPrivateKey = StringUtils.hasText(privateKey);
            boolean hasClientEmail = StringUtils.hasText(clientEmail);

            // Reject dummy/mock/placeholder credentials explicitly
            if (hasPrivateKey && (privateKey.contains("MOCK_KEY_DATA") || privateKey.contains("placeholder"))) {
                throw new IllegalArgumentException("FIREBASE_PRIVATE_KEY contains placeholder/mock data.");
            }
            if (hasClientEmail && (clientEmail.contains("placeholder") || clientEmail.contains("novaos-placeholder"))) {
                throw new IllegalArgumentException("FIREBASE_CLIENT_EMAIL contains placeholder/mock data.");
            }

            if (FirebaseApp.getApps().isEmpty()) {
                GoogleCredentials credentials = loadCredentials();

                FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
                if (StringUtils.hasText(projectId)) {
                    options.setProjectId(projectId);
                }
                if (StringUtils.hasText(databaseUrl)) {
                    options.setDatabaseUrl(databaseUrl);
                }

                FirebaseApp.initializeApp(options.build());
                logger.info("Firebase Admin initialized successfully");
            }
        } catch (Exception e) {
            Throwable rootCause = rootCause(e);
            logger.error("Firebase Admin initialization failed: type={}, message={}",
                    rootCause.getClass().getSimpleName(), safeFailureMessage(rootCause));
            throw new RuntimeException(
                    "Firebase Admin SDK initialization failed. Configure the complete service-account JSON; simulation is disabled.",
                    e);
        }
    }

    private GoogleCredentials loadCredentials() throws Exception {
        try (InputStream serviceAccount = loadServiceAccountStream()) {
            return GoogleCredentials.fromStream(serviceAccount);
        }
    }

    private InputStream loadServiceAccountStream() throws Exception {
        String base64Json = environmentValue(
                "FIREBASE_SERVICE_ACCOUNT_JSON_BASE64", serviceAccountJsonBase64);
        if (StringUtils.hasText(base64Json)) {
            byte[] decodedJson;
            try {
                decodedJson = Base64.getDecoder().decode(base64Json.replaceAll("\\s", ""));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 is not valid Base64-encoded JSON.", e);
            }
            logger.info("Firebase credentials source: FIREBASE_SERVICE_ACCOUNT_JSON_BASE64");
            logger.info("Firebase service-account configuration present: YES");
            return new ByteArrayInputStream(decodedJson);
        }

        String completeJson = environmentValue("FIREBASE_SERVICE_ACCOUNT_JSON", serviceAccountJson);
        if (StringUtils.hasText(completeJson)) {
            logger.info("Firebase credentials source: FIREBASE_SERVICE_ACCOUNT_JSON");
            logger.info("Firebase service-account configuration present: YES");
            return new ByteArrayInputStream(completeJson.trim().getBytes(StandardCharsets.UTF_8));
        }

        if (StringUtils.hasText(privateKey) && StringUtils.hasText(clientEmail)) {
            logger.info("Firebase credentials source: individual Firebase environment variables");
            logger.info("Firebase service-account configuration present: YES");
            String normalizedPrivateKey = privateKey.replace("\\n", "\n");
            Map<String, String> serviceAccount = Map.of(
                    "type", "service_account",
                    "project_id", projectId,
                    "private_key_id", privateKeyId,
                    "private_key", normalizedPrivateKey,
                    "client_email", clientEmail,
                    "client_id", clientId,
                    "auth_uri", "https://accounts.google.com/o/oauth2/auth",
                    "token_uri", "https://oauth2.googleapis.com/token",
                    "auth_provider_x509_cert_url", "https://www.googleapis.com/oauth2/v1/certs",
                    "client_x509_cert_url", "https://www.googleapis.com/robot/v1/metadata/x509/" + clientEmail.replace("@", "%40")
            );
            byte[] json = new ObjectMapper().writeValueAsString(serviceAccount).getBytes(StandardCharsets.UTF_8);
            return new ByteArrayInputStream(json);
        }

        logger.info("Firebase credentials source: local ignored credential file");
        logger.info("Firebase service-account configuration present: YES");
        Resource resource = resourceLoader.getResource(configPath);
        return resource.getInputStream();
    }

    private String environmentValue(String name, String configuredValue) {
        String environmentValue = System.getenv(name);
        return StringUtils.hasText(environmentValue) ? environmentValue : configuredValue;
    }

    private Throwable rootCause(Throwable error) {
        Throwable result = error;
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }

    private String safeFailureMessage(Throwable cause) {
        String type = cause.getClass().getSimpleName();
        if ("InvalidKeyException".equals(type) || "EOFException".equals(type)) {
            return "The Firebase service-account private key is incomplete or cannot be decoded.";
        }
        if (cause instanceof IllegalArgumentException) {
            return "The Firebase service-account environment value is invalid.";
        }
        return "The Firebase service-account credential could not be loaded.";
    }
}
