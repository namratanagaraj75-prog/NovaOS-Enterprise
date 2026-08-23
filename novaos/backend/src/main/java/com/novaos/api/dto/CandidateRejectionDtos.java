package com.novaos.api.dto;

import jakarta.validation.constraints.NotBlank;

public final class CandidateRejectionDtos {
    private CandidateRejectionDtos() {}

    public record RejectCandidateRequest(
            @NotBlank(message = "An internal rejection reason is required.") String internalRejectionReason,
            String candidateRejectionMessage) {}
}
