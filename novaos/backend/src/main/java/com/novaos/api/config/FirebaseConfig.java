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
import java.util.Map;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.config.path}")
    private String configPath;

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

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId(projectId)
                        .setDatabaseUrl(databaseUrl)
                        .build();

                FirebaseApp.initializeApp(options);
                logger.info("Firebase Application has been initialized successfully.");
            }
        } catch (IllegalArgumentException e) {
            logger.error("Firebase Admin SDK failed to initialize: {}", e.getMessage());
            throw new RuntimeException("Firebase Admin SDK initialization aborted due to placeholder/mock credentials.", e);
        } catch (Exception e) {
            logger.error("Firebase Admin SDK failed to initialize: {}", e.getMessage());
            throw new RuntimeException("Firebase Admin SDK initialization failed. Configure a real service account and Firebase project settings; simulation is disabled.", e);
        }
    }

    private GoogleCredentials loadCredentials() throws Exception {
        try (InputStream serviceAccount = loadServiceAccountStream()) {
            return GoogleCredentials.fromStream(serviceAccount);
        }
    }

    private InputStream loadServiceAccountStream() throws Exception {
        if (StringUtils.hasText(privateKey) && StringUtils.hasText(clientEmail)) {
            logger.info("Initializing Firebase credentials from environment variables");
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

        logger.info("Initializing Firebase credentials from path: {}", configPath);
        Resource resource = resourceLoader.getResource(configPath);
        return resource.getInputStream();
    }
}
