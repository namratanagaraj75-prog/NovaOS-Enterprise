package com.novaos.api.dto;

import com.novaos.api.entity.Candidate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String responseText;
    private List<String> executionSteps;
    private Boolean success;
    private String intent;
    private Boolean liveModel;
    private String workflowId;
    private Candidate parsedCandidate;
}

