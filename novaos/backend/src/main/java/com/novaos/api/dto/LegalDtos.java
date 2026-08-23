package com.novaos.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class LegalDtos {
    private LegalDtos() {}

    public record LegalPolicyRequest(
            @NotBlank String policyId,
            @NotBlank String title,
            @NotBlank String description,
            @NotBlank String category,
            @NotBlank String severity,
            boolean active,
            boolean mandatory,
            String policyType) {}

    public record PolicyResultRequest(
            @NotBlank String policyId,
            @NotBlank String status,
            String reviewerComment) {}

    public record LegalReviewRequest(@NotEmpty List<@Valid PolicyResultRequest> policyResults) {}

    public record LegalChangeRequest(
            @NotBlank String reason,
            @NotBlank String policyId,
            String recommendation,
            @NotBlank String targetDepartment) {}
}
