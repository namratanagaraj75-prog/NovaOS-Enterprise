package com.novaos.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class MailConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(MailConfig.class)
            .withPropertyValues(
                    "spring.mail.host= smtp.gmail.com ",
                    "spring.mail.port=587",
                    "spring.mail.username= sender@gmail.com ",
                    "spring.mail.password= app-password ",
                    "nova.mail.from= sender@gmail.com ",
                    "spring.mail.properties.mail.smtp.auth=true",
                    "spring.mail.properties.mail.smtp.starttls.enable=true",
                    "spring.mail.properties.mail.smtp.connectiontimeout=30000",
                    "spring.mail.properties.mail.smtp.timeout=30000",
                    "spring.mail.properties.mail.smtp.writetimeout=30000");

    @Test
    void usesOneTrimmedBootMailSenderWithRailwaySettings() {
        context.run(ctx -> {
            assertThat(ctx.getBeansOfType(JavaMailSender.class)).hasSize(1);
            JavaMailSenderImpl sender = ctx.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
            assertThat(sender.getPort()).isEqualTo(587);
            assertThat(sender.getUsername()).isEqualTo("sender@gmail.com");
            assertThat(sender.getPassword()).isEqualTo("app-password");
            assertThat(sender.getJavaMailProperties())
                    .containsEntry("mail.smtp.auth", "true")
                    .containsEntry("mail.smtp.starttls.enable", "true")
                    .containsEntry("mail.smtp.connectiontimeout", "30000")
                    .containsEntry("mail.smtp.timeout", "30000")
                    .containsEntry("mail.smtp.writetimeout", "30000");
        });
    }
}
