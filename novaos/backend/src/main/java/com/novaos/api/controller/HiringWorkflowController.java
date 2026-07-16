package com.novaos.api.controller;

import com.novaos.api.dto.HiringRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
public class HiringWorkflowController {

    private static final Logger logger = LoggerFactory.getLogger(HiringWorkflowController.class);

    @PostMapping("/hiring")
    public ResponseEntity<Map<String, String>> startHiringWorkflow(@RequestBody HiringRequestDTO request) {
        logger.warn("Rejected legacy hiring workflow endpoint for workflowId={}", request.getWorkflowId());
        return ResponseEntity.status(410).body(Map.of(
                "status", "GONE",
                "message", "Use /api/hiring/passports/parse and /confirm. The legacy endpoint cannot enforce governed approvals."
        ));
    }
}
