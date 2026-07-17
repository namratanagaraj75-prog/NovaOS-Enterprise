package com.novaos.api.service;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;

@Component
public class SmtpFailureLogger {
    private final SmtpErrorMapper errorMapper;
    private final String mailPassword;

    public SmtpFailureLogger(SmtpErrorMapper errorMapper,
                             @Value("${spring.mail.password:}") String mailPassword) {
        this.errorMapper = errorMapper;
        this.mailPassword = mailPassword == null ? "" : mailPassword.trim();
    }

    public SmtpErrorMapper.Analysis log(Logger logger, Throwable error, String stage,
                                        String recipient, String sender, String subject,
                                        int attachmentSizeBytes) {
        SmtpErrorMapper.Analysis analysis = errorMapper.inspect(error, stage);
        logger.error("SMTP delivery failure: stage={}, failureType={}, recipient={}, sender={}, subject={}, "
                        + "attachmentSizeBytes={}, smtpResponseCode={}, rootExceptionClass={}, exceptionClasses={}",
                stage, analysis.failureType(), recipient, sender, subject, attachmentSizeBytes,
                analysis.smtpResponseCode(), analysis.rootExceptionClass(), analysis.exceptionClasses());
        logger.error("SMTP complete exception (credentials redacted):\n{}", sanitizedStackTrace(error));
        if (analysis.rootCause() != null && analysis.rootCause() != error)
            logger.error("SMTP deepest/root exception (credentials redacted):\n{}",
                    sanitizedStackTrace(analysis.rootCause()));
        return analysis;
    }

    private String sanitizedStackTrace(Throwable error) {
        if (error == null) return "No exception object was available.";
        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        return redact(buffer.toString());
    }

    private String redact(String value) {
        String redacted = value;
        if (!mailPassword.isBlank()) {
            redacted = redacted.replace(mailPassword, "[REDACTED_MAIL_PASSWORD]");
            String compact = mailPassword.replace(" ", "");
            if (!compact.isBlank()) redacted = redacted.replace(compact, "[REDACTED_MAIL_PASSWORD]");
        }
        return redacted;
    }
}
