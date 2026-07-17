package com.novaos.api.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class MailConfig {
    private static final Logger logger = LoggerFactory.getLogger(MailConfig.class);
    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.host:}")
    private String host;
    
    @Value("${spring.mail.port:0}")
    private int port;
    
    @Value("${spring.mail.username:}")
    private String username;
    
    @Value("${spring.mail.password:}")
    private String password;
    
    @Value("${spring.mail.properties.mail.smtp.timeout:0}")
    private int smtpTimeout;
    
    private final String senderAddress;

    public MailConfig(JavaMailSender mailSender,
            @Value("${nova.mail.from:${spring.mail.username:}}") String senderAddress) {
        // Spring Boot auto-configure JavaMailSender from spring.mail.* properties.
        this.mailSender = mailSender;
        this.senderAddress = senderAddress;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifySmtpConnection() {
        logger.info("SMTP host configured: {}", StringUtils.hasText(host));
        logger.info("SMTP port: {}", port);
        logger.info("SMTP username configured: {}", StringUtils.hasText(username));
        logger.info("SMTP password configured: {}", StringUtils.hasText(password));
        logger.info("SMTP timeout: {}", smtpTimeout);
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
