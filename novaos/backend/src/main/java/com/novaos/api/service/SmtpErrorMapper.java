package com.novaos.api.service;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class SmtpErrorMapper {
    public String code(Throwable error) {
        Set<Throwable> seen = new HashSet<>();
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (cause instanceof MailAuthenticationException || message.contains("smtp 535")
                    || message.contains("535 5.7.8") || message.contains("authentication failed")) return "SMTP_AUTH_FAILED";
            if (cause instanceof UnknownHostException) return "SMTP_DNS_FAILURE";
            if (cause instanceof SocketTimeoutException) return "SMTP_CONNECTION_TIMEOUT";
            if (cause instanceof ConnectException) return "SMTP_CONNECTION_FAILED";
            if (cause instanceof SSLException || message.contains("starttls") || message.contains("tls negotiation")) return "SMTP_TLS_FAILURE";
            if (cause instanceof AddressException || cause instanceof SendFailedException) return "INVALID_RECIPIENT";
            if (cause instanceof MailSendException) return "SMTP_SEND_FAILED";
        }
        return "EMAIL_DELIVERY_FAILED";
    }
}
