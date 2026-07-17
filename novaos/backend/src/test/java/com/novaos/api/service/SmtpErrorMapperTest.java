package com.novaos.api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import jakarta.mail.internet.InternetAddress;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpErrorMapperTest {
    private final SmtpErrorMapper mapper = new SmtpErrorMapper();

    @Test void mapsNestedConnectionTimeout() { assertCode("SMTP_CONNECTION_TIMEOUT", new MessagingException("mail", new SocketTimeoutException("connect timed out"))); }
    @Test void mapsDnsFailure() { assertCode("SMTP_DNS_FAILURE", new UnknownHostException("smtp.gmail.com")); }
    @Test void mapsConnectionFailure() { assertCode("SMTP_CONNECTION_FAILED", new ConnectException("refused")); }
    @Test void mapsTlsFailure() { assertCode("SMTP_TLS_FAILURE", new SSLException("handshake")); }
    @Test void mapsAuthenticationFailure() { assertCode("SMTP_AUTH_FAILED", new MailAuthenticationException("535 5.7.8")); }
    @Test void mapsInvalidRecipient() { assertCode("INVALID_RECIPIENT", new AddressException("bad address")); }
    @Test void mapsGenericMailSendFailure() { assertCode("SMTP_SEND_FAILED", new MailSendException("send failed")); }
    @Test void mapsUnknownFailureSafely() { assertCode("EMAIL_DELIVERY_FAILED", new IllegalStateException("unknown")); }

    @Test void unwrapsMessagingNextException() {
        MessagingException outer = new MessagingException("outer");
        outer.setNextException(new SocketTimeoutException("nested timeout"));
        assertThat(mapper.inspect(outer, "JAVA_MAIL_SEND").rootExceptionClass())
                .isEqualTo(SocketTimeoutException.class.getName());
        assertCode("SMTP_CONNECTION_TIMEOUT", outer);
    }

    @Test void unwrapsSpringFailedMessageAndCapturesGmailReply() {
        SMTPSendFailedException gmail = new SMTPSendFailedException(
                "DATA", 550, "550 5.7.1 Message rejected", null, null, null, null);
        MailSendException outer = new MailSendException(Map.of("message", gmail));
        SmtpErrorMapper.Analysis analysis = mapper.inspect(outer, "JAVA_MAIL_SEND");
        assertThat(analysis.errorCode()).isEqualTo("SMTP_SEND_FAILED");
        assertThat(analysis.smtpResponseCode()).isEqualTo(550);
        assertThat(analysis.rootExceptionClass()).isEqualTo(SMTPSendFailedException.class.getName());
        assertThat(analysis.failureType()).contains("GMAIL_REJECTION");
    }

    @Test void distinguishesInvalidSenderFromRecipient() throws Exception {
        SMTPAddressFailedException sender = new SMTPAddressFailedException(
                new InternetAddress("sender@example.com"), "MAIL FROM", 553, "sender rejected");
        assertThat(mapper.inspect(sender, "JAVA_MAIL_SEND").failureType()).contains("INVALID_SENDER");

        SMTPAddressFailedException recipient = new SMTPAddressFailedException(
                new InternetAddress("candidate@example.com"), "RCPT TO", 550, "recipient rejected");
        assertThat(mapper.inspect(recipient, "JAVA_MAIL_SEND").errorCode()).isEqualTo("INVALID_RECIPIENT");
    }

    private void assertCode(String expected, Throwable error) {
        assertThat(mapper.code(error)).isEqualTo(expected);
    }
}
