package com.novaos.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class NovaOsApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovaOsApplication.class, args);
    }
}
