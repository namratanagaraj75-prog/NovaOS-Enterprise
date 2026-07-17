package com.novaos.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SmtpConnectivityService {
    private static final Logger logger = LoggerFactory.getLogger(SmtpConnectivityService.class);
    private final String host;
    private final int port;

    public SmtpConnectivityService(@Value("${spring.mail.host:smtp.gmail.com}") String host,
                                   @Value("${spring.mail.port:587}") int port) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
    }

    public Map<String, Object> diagnose() {
        boolean dnsResolved = false;
        boolean connected = false;
        String errorCode = null;
        try {
            InetAddress.getAllByName(host);
            dnsResolved = true;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                connected = true;
            }
        } catch (UnknownHostException error) {
            errorCode = "SMTP_DNS_FAILURE";
        } catch (SocketTimeoutException error) {
            errorCode = "SMTP_CONNECTION_TIMEOUT";
        } catch (ConnectException error) {
            errorCode = "SMTP_CONNECTION_REFUSED";
        } catch (SSLException error) {
            errorCode = "SMTP_TLS_FAILURE";
        } catch (Exception error) {
            errorCode = "SMTP_CONNECTION_FAILED";
        }
        if (!connected) logger.error("Gmail SMTP connectivity diagnostic failed before authentication: code={}, host={}, port={}", errorCode, host, port);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dnsResolved", dnsResolved);
        result.put("tcpConnectionSuccessful", connected);
        result.put("host", host);
        result.put("port", port);
        result.put("errorCode", errorCode);
        return result;
    }
}
