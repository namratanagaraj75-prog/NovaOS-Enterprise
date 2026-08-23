package com.novaos.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateRejectionServiceTest {
    @Test
    void candidateEmailNeverContainsInternalRejectionReason() {
        String internalReason = "Salary exceeds confidential Finance threshold";
        String body = CandidateRejectionService.rejectionBody("Ananya Sharma", "Data Analyst", null);
        assertThat(body).contains("Ananya Sharma", "Data Analyst", "Nova HR Team");
        assertThat(body).doesNotContain(internalReason, "Finance", "risk score", "reviewer");
    }

    @Test
    void explicitlyCandidateVisibleMessageCanBeIncluded() {
        String body = CandidateRejectionService.rejectionBody("Ananya", "Analyst", "We appreciated meeting you.");
        assertThat(body).contains("We appreciated meeting you.");
    }
}
