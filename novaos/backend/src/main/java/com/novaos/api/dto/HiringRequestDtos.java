package com.novaos.api.dto;

import java.util.List;

public final class HiringRequestDtos {
    private HiringRequestDtos() {}

    public record ParseRequest(String instruction) {}

    public record CandidateInput(
            String candidateName,
            String candidateEmail,
            String jobTitle,
            String department,
            Double annualPackageLPA,
            Long annualSalaryAmount,
            String joiningDate,
            String reportingManagerName,
            String hiringManagerName,
            String location,
            String employmentType
    ) {}

    public record ParseResponse(
            String intent,
            CandidateInput candidate,
            List<String> missingFields,
            String followUpQuestion,
            double confidence
    ) {}

    public record CreateRequest(CandidateInput candidate, String originalInstruction, CandidateInput aiExtractedCandidate) {}
    public record UpdateRequest(CandidateInput candidate) {}
    public record DecisionRequest(String action, String reason) {}
    public record EmailRequest(boolean resendConfirmed) {}
}
