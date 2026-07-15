package com.novaos.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStateResponse {
    private List<WorkflowStepDTO> steps;
    private String currentStep;
    private List<WorkflowLogDTO> logs;
    private String status;
    private String candidateName;
    private Integer progress;
}
