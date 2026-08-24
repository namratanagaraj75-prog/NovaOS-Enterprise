package com.novaos.api.controller;

import com.google.firebase.cloud.FirestoreClient;
import com.novaos.api.dto.*;
import com.novaos.api.entity.Candidate;
import com.novaos.api.entity.Employee;
import com.novaos.api.entity.Workflow;
import com.novaos.api.service.CandidateService;
import com.novaos.api.service.RecruitmentService;
import com.novaos.api.service.WorkflowService;
import com.novaos.api.ai.GeminiService;
import com.novaos.api.repository.EmployeeRepository;
import org.springframework.security.core.Authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    private final GeminiService geminiService;
    private final RecruitmentService recruitmentService;
    private final CandidateService candidateService;
    private final WorkflowService workflowService;
    private final EmployeeRepository employeeRepository;

    public ApiController(GeminiService geminiService,
                          RecruitmentService recruitmentService,
                          CandidateService candidateService,
                          WorkflowService workflowService,
                          EmployeeRepository employeeRepository) {
        this.geminiService = geminiService;
        this.recruitmentService = recruitmentService;
        this.candidateService = candidateService;
        this.workflowService = workflowService;
        this.employeeRepository = employeeRepository;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> processChatCommand(@RequestBody ChatRequest request) {
        logger.info("REST: Received chat command request: {}", request.getMessage());

        ChatResponse response = new ChatResponse();
        List<String> steps = new java.util.ArrayList<>();
        steps.add("Sent operator prompt to Gemini for intent classification and extraction");

        try {
            GeminiService.ChatIntentResult result = geminiService.handleCommand(request.getMessage());

            response.setResponseText(result.replyText);
            response.setSuccess(true);
            response.setIntent(result.intent);
            response.setLiveModel(result.liveModel);
            steps.add("Gemini classified intent as \"" + result.intent + "\"");

            if (result.candidate != null) {
                GeminiService.CandidateFields cf = result.candidate;
                steps.add("Extracted candidate: " + cf.name + " (" + cf.role + ")");
                if (cf.matchScore != null) {
                    steps.add("Computed AI match score: " + cf.matchScore + "%");
                }

                Candidate parsed = new Candidate();
                parsed.setName(cf.name);
                parsed.setRole(cf.role);
                parsed.setEmail(cf.email);
                parsed.setStatus("hire".equals(result.intent) ? "Applied" : "AI Screening");
                parsed.setMatchScore(cf.matchScore != null ? cf.matchScore : 0);
                parsed.setSource("AI Command Center Sourced");
                parsed.setAiSummary(cf.summary);
                parsed.setJoiningDate(cf.joiningDate);
                parsed.setCtc(cf.ctc);
                parsed.setDepartment(cf.department);
                response.setParsedCandidate(parsed);
                // Parsing is side-effect free. HR must confirm through /api/hiring/passports/confirm.
            }

            response.setExecutionSteps(steps);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("REST: AI command failed: {}", e.getMessage());
            response.setSuccess(false);
            response.setLiveModel(false);
            response.setIntent("chat");
            response.setResponseText("AI Command Center could not complete this request: " + e.getMessage());
            steps.add("Gemini failed before a workflow could be started");
            response.setExecutionSteps(steps);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
        }
    }

    @PostMapping("/hire")
    public ResponseEntity<?> hireCandidate(@RequestBody Candidate candidate) {
        logger.warn("Rejected legacy candidate creation for {}", candidate.getName());
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "success", false,
                "message", "Use the governed Decision Passport parse and confirm endpoints."
        ));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        logger.info("REST: Fetching Firestore dashboard statistics");

        long totalCandidates = countCollection("candidates");
        long employeesCreated = countCandidatesByStatus("EMPLOYEE_CREATED", "Employee Created", "HIRED", "Hired");
        long pendingApprovals = countPendingApprovals();
        long offersSent = countEmailNotifications("OFFER", "SENT");
        long auditLogs = countCollection("workflowEvents");
        long emailsSent = countEmailsSent();
        long documentsGenerated = countDocumentsGenerated();

        DashboardStats stats = DashboardStats.builder()
                .totalCandidates(totalCandidates)
                .offersSent(offersSent)
                .pendingApprovals(pendingApprovals)
                .employeesCreated(employeesCreated)
                .aiRequests(countCollection("candidateIntelligence"))
                .documentsGenerated(documentsGenerated)
                .emailsSent(emailsSent)
                .auditLogs(auditLogs)
                .build();

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<Candidate>> getCandidates() {
        logger.info("REST: Retrieving all candidate records from Firestore");
        return ResponseEntity.ok(candidateService.getAllCandidates());
    }

    @PutMapping("/candidates/{id}/status")
    public ResponseEntity<?> updateCandidateStatus(@PathVariable String id, @RequestParam String status) {
        logger.warn("Rejected legacy candidate status mutation for {}", id);
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "success", false,
                "message", "Candidate state is controlled by the governed Decision Passport workflow."
        ));
    }

    @PostMapping("/approve")
    public ResponseEntity<?> approveCandidate(@RequestBody ApproveRequest request) {
        logger.warn("Rejected legacy direct employee creation for candidate ID: {}", request.getCandidateId());
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "success", false,
                "message", "Direct promotion is disabled. Use the governed Decision Passport approval workflow."
        ));
    }

    @PostMapping("/offer-letter")
    public ResponseEntity<?> sendOfferLetter(@RequestBody OfferLetterRequest request) {
        logger.warn("Rejected legacy direct offer dispatch for {} <{}>", request.getName(), request.getEmail());
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "success", false,
                "message", "Direct offer dispatch is disabled. Final approval generates and sends the PDF from the governed workflow."
        ));
    }

    @PostMapping("/workflow")
    public ResponseEntity<?> createWorkflow(@RequestBody WorkflowRequest request) {
        logger.warn("Rejected legacy workflow creation for {}", request.getName());
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "success", false,
                "message", "Use the governed Decision Passport workflow."
        ));
    }

    private long countCollection(String collection) {
        try {
            return FirestoreClient.getFirestore().collection(collection).get().get().size();
        } catch (Exception e) {
            logger.warn("Could not count Firestore collection {}: {}", collection, e.getMessage());
            return 0;
        }
    }

    private long countPendingApprovals() {
        try {
            long pending=0;
            for (var d : FirestoreClient.getFirestore().collection("hiringRequests").get().get().getDocuments()) {
                String status=String.valueOf(d.get("status"));
                if (status.startsWith("PENDING") || "DRAFT".equals(status) || "CHANGES_REQUESTED".equals(status)) pending++;
            }
            return pending;
        } catch (Exception e) {
            logger.warn("Could not count pending approvals: {}", e.getMessage());
            return 0;
        }
    }

    private long countCandidatesByStatus(String... statuses) {
        try {
            java.util.Set<String> accepted=java.util.Set.of(statuses); long count=0;
            for (var d:FirestoreClient.getFirestore().collection("candidates").get().get().getDocuments())
                if(accepted.contains(String.valueOf(d.get("currentStatus")))||accepted.contains(String.valueOf(d.get("status"))))count++;
            return count;
        } catch (Exception e) {
            logger.warn("Could not count offer signals: {}", e.getMessage());
            return 0;
        }
    }

    private long countEmailsSent() {
        try {
            return countEmailNotifications(null,"SENT");
        } catch (Exception e) {
            logger.warn("Could not count successfully sent emails: {}", e.getMessage());
            return 0;
        }
    }

    private long countDocumentsGenerated() {
        try {
            long count=0;for(var d:FirestoreClient.getFirestore().collection("documents").get().get().getDocuments())
                if("GENERATED".equals(d.getString("status"))||d.get("generatedAt")!=null)count++;return count;
        } catch (Exception e) {
            logger.warn("Could not count documents generated: {}", e.getMessage());
            return 0;
        }
    }

    private long countEmailNotifications(String type,String status) {
        try { long count=0;for(var d:FirestoreClient.getFirestore().collection("emailNotifications").get().get().getDocuments())
            if((type==null||type.equals(d.getString("type")))&&(status==null||status.equals(d.getString("status"))))count++;return count;
        } catch(Exception e){logger.warn("Could not count email notifications: {}",e.getMessage());return 0;}
    }

}
