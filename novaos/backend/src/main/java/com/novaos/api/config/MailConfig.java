package com.novaos.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

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
    
    @Value("${spring.mail.properties.mail.smtp.auth:false}") private boolean authEnabled;
    @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") private boolean startTlsEnabled;
    @Value("${spring.mail.properties.mail.smtp.connectiontimeout:0}") private int connectionTimeout;
    @Value("${spring.mail.properties.mail.smtp.timeout:0}") private int readTimeout;
    @Value("${spring.mail.properties.mail.smtp.writetimeout:0}") private int writeTimeout;
    private final String senderAddress;

    public MailConfig(JavaMailSender mailSender,
            @Value("${nova.mail.from:${spring.mail.username:}}") String senderAddress) {
        this.mailSender = mailSender;
        this.senderAddress = senderAddress;
    }

    @PostConstruct
    public void configureAndReportSmtp() {
        // Keep Spring Boot's single auto-configured sender, while removing accidental
        // whitespace introduced by environment-variable entry in Railway.
        if (mailSender instanceof JavaMailSenderImpl sender) {
            sender.setHost(trim(host));
            sender.setPort(port);
            sender.setUsername(trim(username));
            sender.setPassword(trim(password));
            Properties properties = sender.getJavaMailProperties();
            properties.setProperty("mail.smtp.auth", Boolean.toString(authEnabled));
            properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(startTlsEnabled));
            properties.setProperty("mail.smtp.starttls.required", "true");
            properties.setProperty("mail.smtp.connectiontimeout", Integer.toString(connectionTimeout));
            properties.setProperty("mail.smtp.timeout", Integer.toString(readTimeout));
            properties.setProperty("mail.smtp.writetimeout", Integer.toString(writeTimeout));
        }

        logger.info("SMTP provider: GMAIL_SMTP");
        logger.info("SMTP host configured: {}", StringUtils.hasText(host));
        logger.info("SMTP host: {}", trim(host));
        logger.info("SMTP port: {}", port);
        logger.info("SMTP username configured: {}", StringUtils.hasText(username));
        logger.info("SMTP password configured: {}", StringUtils.hasText(password));
        logger.info("SMTP from configured: {}", StringUtils.hasText(senderAddress));
        logger.info("SMTP STARTTLS enabled: {}", startTlsEnabled);
        logger.info("SMTP auth enabled: {}", authEnabled);
        logger.info("SMTP connection timeout: {}", connectionTimeout);
        logger.info("SMTP read timeout: {}", readTimeout);
        logger.info("SMTP write timeout: {}", writeTimeout);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
