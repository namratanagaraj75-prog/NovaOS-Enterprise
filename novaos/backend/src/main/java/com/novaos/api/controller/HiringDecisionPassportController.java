package com.novaos.api.controller;

import com.novaos.api.dto.HiringPassportDtos.*;
import com.novaos.api.service.HiringDecisionPassportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hiring/passports")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5175", "http://localhost:5176", "https://nova-os-enterprise-afit-7w894bhgi.vercel.app"}, allowCredentials = "true")
public class HiringDecisionPassportController {
    private final HiringDecisionPassportService service;

    public HiringDecisionPassportController(HiringDecisionPassportService service) {
        this.service = service;
    }

    @PostMapping("/parse")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ParseResponse parse(@RequestBody ParseRequest request, Authentication auth) {
        return service.parse(request, auth);
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public Map<String, Object> confirm(@RequestBody ConfirmRequest request, Authentication auth) {
        return service.confirm(request, auth);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HIRING_MANAGER','LEGAL','FINANCE','CEO','SUPER_ADMIN')")
    public List<Map<String, Object>> list(Authentication auth) {
        return service.list(auth);
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HIRING_MANAGER','LEGAL','FINANCE','CEO','SUPER_ADMIN')")
    public Map<String, Object> passport(@PathVariable String requestId) {
        return service.passport(requestId);
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyRole('HIRING_MANAGER','LEGAL','FINANCE')")
    public Map<String, Object> approve(@PathVariable String requestId, @RequestBody ApprovalRequest request, Authentication auth) {
        return service.approve(requestId, request, auth);
    }

    @PostMapping("/{requestId}/offer/generate")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
    public Map<String, Object> retryDocument(@PathVariable String requestId, Authentication auth) {
        return service.retryDocument(requestId, auth);
    }

    @PostMapping("/{requestId}/offer/send")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public Map<String, Object> sendOffer(@PathVariable String requestId,
            @RequestParam(defaultValue = "false") boolean resend, Authentication auth) {
        return service.sendOffer(requestId, resend, auth);
    }

    @PostMapping("/{requestId}/what-if")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public Map<String, Object> whatIf(@PathVariable String requestId, @RequestBody WhatIfRequest request, Authentication auth) {
        return service.whatIf(requestId, request, auth);
    }
}
