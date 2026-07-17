package com.novaos.api.service;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SmtpErrorMapper {
    private static final Pattern SMTP_CODE = Pattern.compile("(?<!\\d)([245]\\d{2})(?!\\d)");

    public String code(Throwable error) {
        return inspect(error, "JAVA_MAIL_SEND").errorCode();
    }

    public Analysis inspect(Throwable error, String stage) {
        List<Throwable> chain = throwableChain(error);
        Throwable deepest = chain.isEmpty() ? error : chain.get(chain.size() - 1);
        Integer responseCode = null;
        boolean authentication = false;
        boolean dns = false;
        boolean timeout = false;
        boolean connection = false;
        boolean tls = false;
        boolean invalidAddress = false;
        boolean invalidSender = false;
        boolean sendFailure = false;
        boolean messaging = false;
        boolean smtpSendFailed = false;

        for (Throwable cause : chain) {
            String className = cause.getClass().getName();
            String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            Integer candidateCode = smtpResponseCode(cause);
            if (candidateCode != null) responseCode = candidateCode;
            authentication |= cause instanceof MailAuthenticationException
                    || cause instanceof AuthenticationFailedException
                    || candidateCode != null && candidateCode == 535
                    || message.contains("authentication failed")
                    || message.contains("username and password not accepted");
            dns |= cause instanceof UnknownHostException;
            timeout |= cause instanceof SocketTimeoutException;
            connection |= cause instanceof ConnectException;
            tls |= cause instanceof SSLException || message.contains("starttls")
                    || message.contains("tls negotiation") || message.contains("ssl handshake");
            String smtpCommand = smtpCommand(cause).toUpperCase(Locale.ROOT);
            boolean addressFailure = className.endsWith("SMTPAddressFailedException");
            invalidSender |= addressFailure && smtpCommand.contains("MAIL FROM")
                    || message.contains("sender address rejected") || message.contains("invalid sender");
            invalidAddress |= cause instanceof AddressException
                    || addressFailure && !smtpCommand.contains("MAIL FROM")
                    || hasInvalidAddresses(cause);
            smtpSendFailed |= className.endsWith("SMTPSendFailedException");
            sendFailure |= cause instanceof SendFailedException || cause instanceof MailSendException || smtpSendFailed;
            messaging |= cause instanceof MessagingException;
        }

        String errorCode;
        if (authentication) errorCode = "SMTP_AUTH_FAILED";
        else if (dns) errorCode = "SMTP_DNS_FAILURE";
        else if (timeout) errorCode = "SMTP_CONNECTION_TIMEOUT";
        else if (connection) errorCode = "SMTP_CONNECTION_FAILED";
        else if (tls) errorCode = "SMTP_TLS_FAILURE";
        else if (invalidSender) errorCode = "SMTP_SEND_FAILED";
        else if (invalidAddress) errorCode = "INVALID_RECIPIENT";
        else if (sendFailure || messaging) errorCode = "SMTP_SEND_FAILED";
        else errorCode = "EMAIL_DELIVERY_FAILED";

        String failureType;
        String normalizedStage = String.valueOf(stage);
        if (normalizedStage.contains("ATTACHMENT")) failureType = "ATTACHMENT_GENERATION_FAILURE";
        else if (normalizedStage.contains("SET_FROM")) failureType = "INVALID_SENDER_OR_MIME_CONSTRUCTION_FAILURE";
        else if (normalizedStage.contains("SET_TO")) failureType = "INVALID_RECIPIENT_OR_MIME_CONSTRUCTION_FAILURE";
        else if (normalizedStage.contains("MIME")) failureType = "MIME_MESSAGE_CONSTRUCTION_FAILURE";
        else if (authentication) failureType = "MAIL_AUTHENTICATION_EXCEPTION";
        else if (tls) failureType = "SSL_OR_TLS_EXCEPTION";
        else if (invalidSender) failureType = "INVALID_SENDER_OR_GMAIL_SENDER_REJECTION";
        else if (smtpSendFailed) failureType = "SMTP_SEND_FAILED_EXCEPTION_OR_GMAIL_REJECTION";
        else if (invalidAddress) failureType = "INVALID_RECIPIENT";
        else if (sendFailure) failureType = "SEND_FAILED_EXCEPTION";
        else if (messaging) failureType = "MESSAGING_EXCEPTION";
        else failureType = "UNCLASSIFIED_EMAIL_FAILURE";

        List<String> classes = chain.stream().map(t -> t.getClass().getName()).toList();
        return new Analysis(errorCode, failureType,
                deepest == null ? "unknown" : deepest.getClass().getName(), responseCode, classes, deepest);
    }

    private List<Throwable> throwableChain(Throwable error) {
        if (error == null) return List.of();
        record Node(Throwable throwable, int depth) {}
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Node> nodes = new ArrayList<>();
        queue.add(new Node(error, 0));
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            Throwable current = node.throwable();
            if (current == null || !seen.add(current)) continue;
            nodes.add(node);
            if (current.getCause() != null) queue.addLast(new Node(current.getCause(), node.depth() + 1));
            if (current instanceof MessagingException messaging && messaging.getNextException() != null)
                queue.addLast(new Node(messaging.getNextException(), node.depth() + 1));
            if (current instanceof MailSendException mailSend) {
                for (Map.Entry<Object, Exception> failed : mailSend.getFailedMessages().entrySet())
                    if (failed.getValue() != null) queue.addLast(new Node(failed.getValue(), node.depth() + 1));
            }
        }
        nodes.sort((left, right) -> Integer.compare(left.depth(), right.depth()));
        return nodes.stream().map(Node::throwable).toList();
    }

    private Integer smtpResponseCode(Throwable error) {
        try {
            Method method = error.getClass().getMethod("getReturnCode");
            Object value = method.invoke(error);
            if (value instanceof Number number) return number.intValue();
        } catch (ReflectiveOperationException ignored) {
            // Not an Angus SMTP exception; fall back to parsing the server reply.
        }
        Matcher matcher = SMTP_CODE.matcher(String.valueOf(error.getMessage()));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private boolean hasInvalidAddresses(Throwable error) {
        if (!(error instanceof SendFailedException failed)) return false;
        return failed.getInvalidAddresses() != null && failed.getInvalidAddresses().length > 0;
    }

    private String smtpCommand(Throwable error) {
        try {
            Method method = error.getClass().getMethod("getCommand");
            return String.valueOf(method.invoke(error));
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public record Analysis(String errorCode, String failureType, String rootExceptionClass,
                           Integer smtpResponseCode, List<String> exceptionClasses, Throwable rootCause) {}
}
