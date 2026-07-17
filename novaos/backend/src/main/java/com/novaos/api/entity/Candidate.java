package com.novaos.api.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {

    private String id;

    private String name;

    private String role;

    private String email;

    private String status;

    private Integer matchScore;

    private String source;

    private String aiSummary;

    private String joiningDate;

    private String ctc;

    private String department;

    private String resumeUrl;

    private String offerLetterUrl;

    private Instant createdAt = Instant.now();
}
