package com.novaos.api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

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

    private void assertCode(String expected, Throwable error) {
        assertThat(mapper.code(error)).isEqualTo(expected);
    }
}
