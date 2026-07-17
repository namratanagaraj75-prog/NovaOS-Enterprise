package com.novaos.api.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {

    private String id;

    private String name;

    private String description;

    private String triggerEvent;

    private Boolean active = true;

    private Double successRate = 100.0;

    private Instant createdAt = Instant.now();
}
