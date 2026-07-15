package com.novaos.api.service;

import com.novaos.api.dto.HiringRequestDtos.CandidateInput;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class HiringWorkflowRulesTest {
    private final HiringWorkflowRules rules = new HiringWorkflowRules(
            Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC));

    private CandidateInput valid() {
        return new CandidateInput("Sharma", "sharma@example.com", "Software Engineer", "Engineering",
                12d, 1_200_000L, "2026-08-01", "Priya Mehta", "Rahul Verma",
                "Hyderabad", "Full-time");
    }

    @Test void completeStructuredCandidatePassesValidation() { assertTrue(rules.validate(valid(), false).isEmpty()); }
    @Test void detectsOnlyMissingRequiredFields() {
        CandidateInput c = new CandidateInput("Sharma", null, null, null, 12d, 1_200_000L,
                null, null, null, null, null);
        assertEquals(java.util.List.of("candidateEmail","jobTitle","department","joiningDate","reportingManagerName","hiringManagerName","location","employmentType"), rules.missing(c));
    }
    @Test void rejectsInvalidCandidateEmail() {
        CandidateInput c = new CandidateInput("Sharma","bad-email","Software Engineer","Engineering",12d,1_200_000L,"2026-08-01","Priya","Rahul",null,null);
        assertTrue(rules.validate(c,false).stream().anyMatch(x->x.contains("email")));
    }
    @Test void rejectsInvalidSalary() {
        CandidateInput c = new CandidateInput("Sharma","s@example.com","Engineer","Engineering",-1d,-1L,"2026-08-01","Priya","Rahul",null,null);
        assertFalse(rules.validate(c,false).isEmpty());
    }
    @Test void routesManagerApprovalToFinance() { assertTrue(rules.canTransition("PENDING_MANAGER_APPROVAL","PENDING_FINANCE_APPROVAL")); }
    @Test void preventsManagerApprovalFromSkippingFinance() { assertFalse(rules.canTransition("PENDING_MANAGER_APPROVAL","APPROVED")); }
    @Test void routesFinanceApprovalToLegal() { assertTrue(rules.canTransition("PENDING_FINANCE_APPROVAL","PENDING_LEGAL_APPROVAL")); }
    @Test void permitsLegalApprovalToCompleteApprovals() { assertTrue(rules.canTransition("PENDING_LEGAL_APPROVAL","APPROVALS_COMPLETED")); }
    @Test void preventsFinanceApprovalFromSkippingLegal() { assertFalse(rules.canTransition("PENDING_FINANCE_APPROVAL","PENDING_CEO_APPROVAL")); }
    @Test void preventsManagerApprovalFromSkippingFinanceForContracts() { assertFalse(rules.canTransition("PENDING_MANAGER_APPROVAL","PENDING_LEGAL_APPROVAL")); }
    @Test void permitsManagerRejection() { assertTrue(rules.canTransition("PENDING_MANAGER_APPROVAL","REJECTED")); }
    @Test void permitsRequestChanges() { assertTrue(rules.canTransition("PENDING_MANAGER_APPROVAL","CHANGES_REQUESTED")); }
    @Test void preventsEmailBeforeApproval() { assertFalse(rules.canTransition("DRAFT","EMAIL_SENT")); }
    @Test void preventsEmailImmediatelyAfterApproval() { assertFalse(rules.canTransition("APPROVALS_COMPLETED","WORKFLOW_COMPLETED")); }
    @Test void permitsOfferGenerationAfterAllApprovals() { assertTrue(rules.canTransition("APPROVALS_COMPLETED","GENERATING_OFFER")); }
    @Test void preventsDuplicateSend() { assertFalse(rules.canTransition("WORKFLOW_COMPLETED","WORKFLOW_COMPLETED")); }
    @Test void rejectsInvalidStatusJump() { assertThrows(IllegalStateException.class,()->rules.requireTransition("DRAFT","PDF_GENERATED")); }
    @Test void requiresConfirmationForPastJoiningDate() {
        CandidateInput c = new CandidateInput("Sharma","s@example.com","Engineer","Engineering",12d,1_200_000L,"2026-01-01","Priya","Rahul","Hyderabad","Full-time");
        assertFalse(rules.validate(c,false).isEmpty()); assertTrue(rules.validate(c,true).isEmpty());
    }
}
