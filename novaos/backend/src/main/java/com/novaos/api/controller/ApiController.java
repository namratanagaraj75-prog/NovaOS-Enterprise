package com.novaos.api.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.novaos.api.dto.*;
import com.novaos.api.entity.Candidate;
import com.novaos.api.entity.Employee;
import com.novaos.api.entity.Workflow;
import com.novaos.api.service.CandidateService;
import com.novaos.api.service.RecruitmentService;
import com.novaos.api.service.WorkflowService;
import com.novaos.api.service.HiringPolicyEngine;
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
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5175", "http://localhost:5176"}, allowCredentials = "true")
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    private final GeminiService geminiService;
    private final RecruitmentService recruitmentService;
    private final CandidateService candidateService;
    private final WorkflowService workflowService;
    private final EmployeeRepository employeeRepository;
    private final HiringPolicyEngine hiringPolicyEngine;

    public ApiController(GeminiService geminiService,
                          RecruitmentService recruitmentService,
                          CandidateService candidateService,
                          WorkflowService workflowService,
                          EmployeeRepository employeeRepository,
                          HiringPolicyEngine hiringPolicyEngine) {
        this.geminiService = geminiService;
        this.recruitmentService = recruitmentService;
        this.candidateService = candidateService;
        this.workflowService = workflowService;
        this.employeeRepository = employeeRepository;
        this.hiringPolicyEngine = hiringPolicyEngine;
    }

    /**
     * Force-resets all Firestore policy documents to the latest defaults.
     * Call this when a hiring request is blocked by a stale employment-type policy.
     * POST /api/admin/reset-policies
     */
    @PostMapping("/admin/reset-policies")
    public ResponseEntity<?> resetPolicies(Authentication auth) {
        logger.info("REST: Admin policy reset requested by {}", auth != null ? auth.getName() : "anonymous");
        try {
            hiringPolicyEngine.resetPolicies(FirestoreClient.getFirestore());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All Firestore policy documents have been reset to the latest defaults.",
                "departmentValidation", "All department names are accepted.",
                "allowedEmploymentTypes", "Full Time, Full-time, Permanent, Contract, Part Time, Internship"
            ));
        } catch (Exception e) {
            logger.error("Policy reset failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Policy reset failed: " + e.getMessage()
            ));
        }
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
        long employeesCreated = countCollection("employees");
        long pendingApprovals = countPendingApprovals();
        long offersSent = countCandidatesWithOfferSignals();
        long auditLogs = countCollection("auditLogs");
        long emailsSent = countEmailsSent();
        long documentsGenerated = countDocumentsGenerated();

        DocumentSnapshot metrics = readDashboardMetrics();
        DashboardStats stats = DashboardStats.builder()
                .totalCandidates(totalCandidates)
                .offersSent(offersSent)
                .pendingApprovals(pendingApprovals)
                .employeesCreated(employeesCreated)
                .aiRequests(metric(metrics, "aiRequests"))
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
            return FirestoreClient.getFirestore().collection("workflowRequests")
                    .whereEqualTo("status", "Pending")
                    .get()
                    .get()
                    .size();
        } catch (Exception e) {
            logger.warn("Could not count pending approvals: {}", e.getMessage());
            return 0;
        }
    }

    private long countCandidatesWithOfferSignals() {
        try {
            long offerSent = FirestoreClient.getFirestore().collection("candidates")
                    .whereEqualTo("status", "Offer Sent")
                    .get()
                    .get()
                    .size();
            long employeeCreated = FirestoreClient.getFirestore().collection("candidates")
                    .whereEqualTo("status", "Employee Created")
                    .get()
                    .get()
                    .size();
            return offerSent + employeeCreated;
        } catch (Exception e) {
            logger.warn("Could not count offer signals: {}", e.getMessage());
            return 0;
        }
    }

    private long countEmailsSent() {
        try {
            com.google.cloud.firestore.Firestore db = FirestoreClient.getFirestore();
            java.util.Set<String> uniqueIds = new java.util.HashSet<>();
            for (com.google.cloud.firestore.QueryDocumentSnapshot d : db.collection("hiringRequests").get().get().getDocuments()) {
                Map<String, Object> data = d.getData();
                if ("SENT".equals(data.get("emailStatus")) || 
                    "EMAIL_SENT".equals(data.get("status")) || 
                    Boolean.TRUE.equals(data.get("emailSent")) || 
                    data.get("emailSentAt") != null || 
                    "SENT".equals(data.get("emailDeliveryStatus"))) {
                    uniqueIds.add(d.getId());
                }
            }
            for (com.google.cloud.firestore.QueryDocumentSnapshot d : db.collection("workflowRequests").get().get().getDocuments()) {
                Map<String, Object> data = d.getData();
                if ("SENT".equals(data.get("emailStatus")) || 
                    "EMAIL_SENT".equals(data.get("state")) || 
                    Boolean.TRUE.equals(data.get("emailSent")) || 
                    data.get("emailSentAt") != null || 
                    "SENT".equals(data.get("emailDeliveryStatus"))) {
                    uniqueIds.add(d.getId());
                }
            }
            return uniqueIds.size();
        } catch (Exception e) {
            logger.warn("Could not count successfully sent emails: {}", e.getMessage());
            return 0;
        }
    }

    private long countDocumentsGenerated() {
        try {
            return FirestoreClient.getFirestore().collection("documents").get().get().size();
        } catch (Exception e) {
            logger.warn("Could not count documents generated: {}", e.getMessage());
            return 0;
        }
    }

    private DocumentSnapshot readDashboardMetrics() {
        try {
            return FirestoreClient.getFirestore().collection("metrics").document("dashboard").get().get();
        } catch (Exception e) {
            logger.warn("Could not read dashboard metrics: {}", e.getMessage());
            return null;
        }
    }

    @PostMapping("/admin/reset-demo-data")
    public ResponseEntity<?> resetDemoData(Authentication auth) {
        logger.info("REST: Reset demo data requested by {}", auth != null ? auth.getName() : "anonymous");
        try {
            com.google.cloud.firestore.Firestore db = FirestoreClient.getFirestore();
            deleteCollection(db, "hiringRequests");
            deleteCollection(db, "workflowRequests");
            deleteCollection(db, "candidates");
            deleteCollection(db, "employees");
            deleteCollection(db, "approvals");
            deleteCollection(db, "documents");
            deleteCollection(db, "notifications");
            deleteCollection(db, "auditLogs");
            resetMetrics(db);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Demo data reset successfully."
            ));
        } catch (Exception e) {
            logger.error("Demo data reset failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Demo data reset failed: " + e.getMessage()
            ));
        }
    }

    private void deleteCollection(com.google.cloud.firestore.Firestore db, String collectionName) throws Exception {
        com.google.cloud.firestore.CollectionReference colRef = db.collection(collectionName);
        List<com.google.cloud.firestore.QueryDocumentSnapshot> docs = colRef.get().get().getDocuments();
        for (com.google.cloud.firestore.QueryDocumentSnapshot doc : docs) {
            doc.getReference().delete().get();
        }
    }

    private void resetMetrics(com.google.cloud.firestore.Firestore db) throws Exception {
        db.collection("metrics").document("dashboard").set(Map.of(
            "aiRequests", 0L,
            "documentsGenerated", 0L,
            "emailsSent", 0L
        )).get();
    }

    private long metric(DocumentSnapshot metrics, String field) {
        if (metrics == null || !metrics.exists()) {
            return 0;
        }
        Long value = metrics.getLong(field);
        return value != null ? value : 0;
    }
}
