package com.novaos.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5175", "http://localhost:5176", "https://nova-os-enterprise-afit-7w894bhgi.vercel.app"}, allowCredentials = "true")
public class WorkflowController {

    private ResponseEntity<Map<String, Object>> retired() {
        return ResponseEntity.status(410).body(Map.of(
                "status", "GONE",
                "message", "The simulated workflow API is disabled. Use /api/hiring/passports and verified role approvals."
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getWorkflow() {
        return retired();
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startWorkflow() {
        return retired();
    }

    @PostMapping("/approve")
    public ResponseEntity<Map<String, Object>> approveWorkflow() {
        return retired();
    }

    @PostMapping("/reject")
    public ResponseEntity<Map<String, Object>> rejectWorkflow() {
        return retired();
    }
}
