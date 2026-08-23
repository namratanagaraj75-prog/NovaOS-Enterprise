package com.novaos.api.controller;

import com.novaos.api.dto.LegalDtos.LegalPolicyRequest;
import com.novaos.api.service.LegalPolicyService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/legal/policies")
public class LegalPolicyController {
    private final LegalPolicyService service;
    public LegalPolicyController(LegalPolicyService service) { this.service = service; }
    @GetMapping public List<Map<String,Object>> list(@RequestParam(defaultValue="false") boolean includeInactive, Authentication auth) { return service.list(includeInactive, auth); }
    @PostMapping public Map<String,Object> create(@Valid @RequestBody LegalPolicyRequest request, Authentication auth) { return service.create(request, auth); }
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable String id, @Valid @RequestBody LegalPolicyRequest request, Authentication auth) { return service.update(id, request, auth); }
}
