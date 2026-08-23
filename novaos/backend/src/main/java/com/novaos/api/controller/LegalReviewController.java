package com.novaos.api.controller;

import com.novaos.api.dto.CandidateRejectionDtos.RejectCandidateRequest;
import com.novaos.api.dto.HiringRequestDtos.DecisionRequest;
import com.novaos.api.dto.LegalDtos.LegalChangeRequest;
import com.novaos.api.dto.LegalDtos.LegalReviewRequest;
import com.novaos.api.service.CandidateRejectionService;
import com.novaos.api.service.HiringRequestService;
import com.novaos.api.service.LegalReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/legal/reviews/{requestId}")
public class LegalReviewController {
    private final LegalReviewService reviews; private final HiringRequestService hiring; private final CandidateRejectionService rejection;
    public LegalReviewController(LegalReviewService reviews, HiringRequestService hiring, CandidateRejectionService rejection) { this.reviews=reviews;this.hiring=hiring;this.rejection=rejection; }
    @GetMapping public Map<String,Object> get(@PathVariable String requestId,Authentication auth){return reviews.get(requestId,auth);}
    @PostMapping public Map<String,Object> save(@PathVariable String requestId,@Valid @RequestBody LegalReviewRequest request,Authentication auth){return reviews.save(requestId,request,auth);}
    @PostMapping("/approve") public Map<String,Object> approve(@PathVariable String requestId,@RequestBody(required=false) DecisionRequest request,Authentication auth){return hiring.decide(requestId,new DecisionRequest("APPROVE",request==null?null:request.reason()),auth);}
    @PostMapping("/request-changes") public Map<String,Object> requestChanges(@PathVariable String requestId,@Valid @RequestBody LegalChangeRequest request,Authentication auth){return reviews.requestChanges(requestId,request,auth);}
    @PostMapping("/reject") public Map<String,Object> reject(@PathVariable String requestId,@Valid @RequestBody RejectCandidateRequest request,Authentication auth){return rejection.reject(requestId,request,auth);}
}
