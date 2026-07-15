package com.novaos.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    private Long totalCandidates;
    private Long offersSent;
    private Long pendingApprovals;
    private Long employeesCreated;
    private Long aiRequests;
    private Long documentsGenerated;
    private Long emailsSent;
    private Long auditLogs;
}
