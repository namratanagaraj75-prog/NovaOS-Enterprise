package com.novaos.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRequest {
    private String name;
    private String triggerEvent;
    private String description;
    private Boolean active;
}
