package com.novaos.api.controller;

import com.novaos.api.dto.CandidateRejectionDtos.RejectCandidateRequest;
import com.novaos.api.service.CandidateRejectionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/hiring/requests/{requestId}")
public class CandidateRejectionController {
    private final CandidateRejectionService service;
    public CandidateRejectionController(CandidateRejectionService service) { this.service = service; }

    @PostMapping("/reject")
    public Map<String, Object> reject(@PathVariable String requestId,
                                      @Valid @RequestBody RejectCandidateRequest request,
                                      Authentication auth) {
        return service.reject(requestId, request, auth);
    }

    @PostMapping("/rejection-email/retry")
    public Map<String, Object> retry(@PathVariable String requestId, Authentication auth) {
        return service.retry(requestId, auth);
    }

    @GetMapping(value="/rejection-letter", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> rejectionLetter(@PathVariable String requestId, Authentication auth) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=rejection-letter.pdf")
                .body(service.rejectionPdf(requestId, auth));
    }
}
