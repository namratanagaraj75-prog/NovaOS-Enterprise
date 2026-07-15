package com.novaos.api.dto;

import java.util.List;
import java.util.Map;

public final class HiringPassportDtos {
    private HiringPassportDtos() {}

    public record ParseRequest(String requestId, String instruction) {}

    public record CandidateData(
            String name,
            String email,
            String position,
            Long annualCtc,
            String joiningDate,
            String department,
            String location,
            String manager,
            Integer probationMonths,
            List<String> requiredSkills
    ) {}

    public record ParseResponse(
            String intent,
            CandidateData candidate,
            List<String> missingFields,
            double confidence
    ) {}

    public record ConfirmRequest(
            String requestId,
            String originalInstruction,
            CandidateData candidate,
            double confidence
    ) {}

    public record ApprovalRequest(String comment) {}

    public record WhatIfRequest(Long annualCtc, String joiningDate) {}

    public record PolicyCheck(String name, String status, String reason, Map<String, Object> evidence) {}
}
