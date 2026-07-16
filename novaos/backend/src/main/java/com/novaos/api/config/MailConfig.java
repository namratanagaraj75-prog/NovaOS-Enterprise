package com.novaos.api.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class MailConfig {
    private static final Logger logger = LoggerFactory.getLogger(MailConfig.class);
    private final JavaMailSender mailSender;
    private final String host;
    private final int port;
    private final String senderAddress;

    public MailConfig(JavaMailSender mailSender,
            @Value("${spring.mail.host}") String host, @Value("${spring.mail.port}") int port,
            @Value("${email.from.address:${spring.mail.username:}}") String senderAddress) {
        // Spring Boot auto-configures JavaMailSender from spring.mail.* properties.
        this.mailSender = mailSender;
        this.host = host;
        this.port = port;
        this.senderAddress = senderAddress;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifySmtpConnection() {
        try {
            if ("resend".equalsIgnoreCase(System.getenv("EMAIL_PROVIDER"))) {
                logger.info("SMTP connection check skipped because EMAIL_PROVIDER is resend.");
                return;
            }
            if (!(mailSender instanceof JavaMailSenderImpl sender)) {
                logger.warn("SMTP authentication check skipped because the configured mail sender does not expose a connection test.");
                return;
            }
            sender.testConnection();
            logger.info("SMTP authentication verified for host={}, port={}, sender={}", host, port, senderAddress);
        } catch (Exception error) {
            logger.error("SMTP authentication check failed for host={}, port={}, sender={}; root cause: {}",
                    host, port, senderAddress, rootCauseMessage(error), error);
        }
    }

    private String rootCauseMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getClass().getName() + ": " + String.valueOf(cause.getMessage());
    }
}
