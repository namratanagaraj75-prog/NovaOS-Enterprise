package com.novaos.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalPolicyServiceTest {
    @Test void incrementsPolicyMinorVersion() {
        assertThat(LegalPolicyService.increment("1.0")).isEqualTo("1.1");
        assertThat(LegalPolicyService.increment("2.9")).isEqualTo("2.10");
    }
}
