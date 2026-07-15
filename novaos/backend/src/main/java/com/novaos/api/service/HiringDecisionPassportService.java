package com.novaos.api.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import com.google.firebase.cloud.FirestoreClient;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.novaos.api.ai.GeminiService;
import com.novaos.api.dto.HiringPassportDtos.*;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class HiringDecisionPassportService {
    private static final Logger logger = LoggerFactory.getLogger(HiringDecisionPassportService.class);
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final List<String> REQUIRED = List.of("name", "email", "position", "annualCtc", "joiningDate",
            "department", "location", "manager", "probationMonths", "requiredSkills");

    private final GeminiService gemini;
    private final JavaMailSender mailSender;
    private final String mailUsername;
    private final String mailPassword;
    private final String mailFrom;
    private final String mailFromName;
    private final String mailHost;
    private final int mailPort;

    public HiringDecisionPassportService(GeminiService gemini, JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${email.from.address:${spring.mail.username:}}") String mailFrom,
            @Value("${email.from.name:Nova HR}") String mailFromName,
            @Value("${spring.mail.host}") String mailHost, @Value("${spring.mail.port}") int mailPort) {
        this.gemini = gemini;
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.mailFrom = mailFrom;
        this.mailFromName = mailFromName;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
    }

    public ParseResponse parse(ParseRequest request, Authentication auth) {
        requireRole(auth, "HR_ADMIN");
        if (request == null || !StringUtils.hasText(request.instruction())) throw bad("Hiring instruction is required.");
        String requestId = requireUuid(request.requestId());
        try {
            ParseResponse result = gemini.parseHiringInstruction(request.instruction());
            Firestore db = db();
            Map<String, Object> record = new HashMap<>();
            record.put("requestId", requestId);
            record.put("requestor", actor(auth, db));
            record.put("instruction", request.instruction());
            record.put("intent", result.intent());
            record.put("candidate", aiCandidateMap(result.candidate()));
            record.put("missingFields", result.missingFields());
            record.put("confidence", result.confidence());
            record.put("status", "PARSED");
            record.put("createdAt", Instant.now().toString());
            db.collection("aiRequests").document(requestId).create(record).get();
            return result;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            try {
                Firestore db = db();
                Map<String, Object> failed = new HashMap<>();
                failed.put("requestId", requestId);
                failed.put("requestor", actor(auth, db));
                failed.put("instruction", request.instruction());
                failed.put("status", "FAILED");
                failed.put("error", Objects.toString(e.getMessage(), "Gemini parsing failed"));
                failed.put("createdAt", Instant.now().toString());
                db.collection("aiRequests").document(requestId).set(failed).get();
            } catch (Exception ignored) {}
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    public Map<String, Object> confirm(ConfirmRequest request, Authentication auth) {
        requireRole(auth, "HR_ADMIN");
        if (request == null || request.candidate() == null) throw bad("Parsed candidate details are required.");
        String requestId = StringUtils.hasText(request.requestId()) ? request.requestId() : UUID.randomUUID().toString();
        try {
            Firestore db = db();
            DocumentSnapshot existing = db.collection("workflowRequests").document(requestId).get().get();
            if (existing.exists()) return passport(requestId);

            ensurePolicies(db);
            List<PolicyCheck> checks = evaluate(db, request.candidate(), null);
            List<String> failures = checks.stream().filter(c -> "FAIL".equals(c.status())).map(PolicyCheck::reason).toList();
            if (!failures.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, String.join("; ", failures));

            Map<String, Object> actor = actor(auth, db);
            Instant now = Instant.now();
            Map<String, Object> candidate = candidateMap(requestId, request.candidate(), now);
            Map<String, Object> workflow = new HashMap<>();
            workflow.put("requestId", requestId);
            workflow.put("candidateId", requestId);
            workflow.put("state", "MANAGER_PENDING");
            workflow.put("lastCompletedState", "POLICY_REVIEWED");
            workflow.put("requestor", actor);
            workflow.put("originalInstruction", request.originalInstruction());
            workflow.put("geminiConfidence", request.confidence());
            workflow.put("extractedDetails", candidate);
            workflow.put("policyChecks", checks);
            workflow.put("approvalChain", List.of("HIRING_MANAGER", "LEGAL", "FINANCE"));
            workflow.put("createdAt", now.toString());
            workflow.put("updatedAt", now.toString());
            workflow.put("emailStatus", "NOT_READY");
            workflow.put("employeeCreated", false);

            WriteBatch batch = db.batch();
            batch.create(db.collection("candidates").document(requestId), candidate);
            batch.create(db.collection("workflowRequests").document(requestId), workflow);
            batch.create(db.collection("auditLogs").document(requestId + "-WORKFLOW_CREATED"),
                    audit(requestId, "WORKFLOW_CREATED", actor, "Policy checks passed and manager approval requested."));
            batch.create(db.collection("notifications").document(requestId + "-MANAGER_PENDING"),
                    notification(requestId, "HIRING_MANAGER", "Manager approval required", request.candidate().name()));
            batch.commit().get();
            return passport(requestId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw server("Firestore could not create the hiring workflow", e);
        }
    }

    public Map<String, Object> approve(String requestId, ApprovalRequest request, Authentication auth) {
        try {
            Firestore db = db();
            Map<String, Object> actor = actor(auth, db);
            String role = String.valueOf(actor.get("role"));
            if (!List.of("HIRING_MANAGER", "LEGAL", "FINANCE").contains(role)) throw forbidden("This role cannot approve hiring stages.");

            var workflowRef = db.collection("workflowRequests").document(requestId);
            DocumentSnapshot workflow = workflowRef.get().get();
            if (!workflow.exists()) throw notFound("Hiring workflow not found.");
            String expectedRole = switch (String.valueOf(workflow.get("state"))) {
                case "MANAGER_PENDING" -> "HIRING_MANAGER";
                case "LEGAL_PENDING" -> "LEGAL";
                case "FINANCE_PENDING" -> "FINANCE";
                default -> null;
            };
            if (!role.equals(expectedRole)) throw forbidden("Workflow is not awaiting " + role + " approval.");

            String approvalId = requestId + "-" + role;
            if (db.collection("approvals").document(approvalId).get().get().exists()) return passport(requestId);

            String approvedState = role.equals("HIRING_MANAGER") ? "MANAGER_APPROVED" : role + "_APPROVED";
            String nextState = role.equals("HIRING_MANAGER") ? "LEGAL_PENDING" : role.equals("LEGAL") ? "FINANCE_PENDING" : "OFFER_GENERATING";
            Instant now = Instant.now();
            Map<String, Object> approval = new HashMap<>();
            approval.put("requestId", requestId);
            approval.put("approverUid", actor.get("uid"));
            approval.put("approverEmail", actor.get("email"));
            approval.put("approverRole", role);
            approval.put("comment", request == null ? "" : Objects.toString(request.comment(), ""));
            approval.put("status", "APPROVED");
            approval.put("timestamp", now.toString());

            WriteBatch batch = db.batch();
            batch.create(db.collection("approvals").document(approvalId), approval);
            batch.set(workflowRef, Map.of("state", nextState, "lastCompletedState", approvedState,
                    "updatedAt", now.toString()), SetOptions.merge());
            batch.create(db.collection("auditLogs").document(requestId + "-" + approvedState),
                    audit(requestId, approvedState, actor, "Approval recorded with verified identity."));
            if (!role.equals("FINANCE")) {
                batch.create(db.collection("notifications").document(requestId + "-" + nextState),
                        notification(requestId, role.equals("HIRING_MANAGER") ? "LEGAL" : "FINANCE",
                                nextState.replace('_', ' '), requestId));
            }
            batch.commit().get();
            if (role.equals("FINANCE")) {
                generateOffer(requestId);
                sendOffer(requestId, false, auth);
            }
            return passport(requestId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw server("Approval could not be recorded", e);
        }
    }

    public Map<String, Object> retryDocument(String requestId, Authentication auth) {
        requireAnyRole(auth, "HR_ADMIN", "SUPER_ADMIN");
        generateOffer(requestId);
        return passport(requestId);
    }

    public Map<String, Object> sendOffer(String requestId, boolean resend, Authentication auth) {
        try {
            Firestore db = db();
            Map<String, Object> actor = actor(auth, db);
            var workflowRef = db.collection("workflowRequests").document(requestId);
            DocumentSnapshot workflow = workflowRef.get().get();
            if (!workflow.exists()) throw notFound("Hiring workflow not found.");
            String currentEmailStatus = Objects.toString(workflow.get("emailStatus"), "");
            if ("SENT".equals(currentEmailStatus)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer already sent; duplicate delivery is blocked.");
            if ("FAILED".equals(currentEmailStatus) && !resend) throw new ResponseStatusException(HttpStatus.CONFLICT, "Use explicit retry confirmation after an email failure.");
            if (!"FAILED".equals(currentEmailStatus) && !"OFFER_GENERATED".equals(String.valueOf(workflow.get("state"))))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email delivery is available only after final approval or after a failed attempt.");

            requireMailCredentials();

            Map<String, Object> details = castMap(workflow.get("extractedDetails"));
            byte[] pdfContent = offerPdf(requestId, details);

            workflowRef.set(Map.of("state", "EMAIL_PENDING", "emailStatus", "PENDING", "updatedAt", Instant.now().toString()), SetOptions.merge()).get();
            try {
                sendEmail(details, pdfContent, requestId);
            } catch (Exception mailError) {
                String filename = offerFileName(String.valueOf(details.get("name")), requestId);
                logger.error("SMTP delivery failed via host={}, port={}, sender={}, recipient={}; root cause: {}", mailHost, mailPort, mailFrom, details.get("email"), rootCauseMessage(mailError), mailError);
                workflowRef.set(Map.of("state", "EMAIL_PENDING", "emailStatus", "FAILED",
                        "emailError", mailError.getMessage(), "updatedAt", Instant.now().toString()), SetOptions.merge()).get();
                db.collection("documents").document(requestId + "-offer")
                        .set(documentMetadata(requestId, details, filename, "FAILED", null, actor, safeMessage(mailError)), SetOptions.merge()).get();
                db.collection("auditLogs").document(requestId + "-EMAIL_FAILED-" + System.currentTimeMillis())
                        .set(audit(requestId, "EMAIL_FAILED", actor, mailError.getMessage())).get();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMTP delivery failed. " + mailError.getMessage());
            }

            Instant sentAt = Instant.now();
            workflowRef.set(Map.of("state", "EMAIL_SENT", "lastCompletedState", "EMAIL_SENT", "emailStatus", "SENT",
                    "emailSentAt", sentAt.toString(), "updatedAt", sentAt.toString()), SetOptions.merge()).get();
            String filename = offerFileName(String.valueOf(details.get("name")), requestId);
            db.collection("documents").document(requestId + "-offer")
                    .set(documentMetadata(requestId, details, filename, "SENT", sentAt.toString(), actor, null)).get();
            db.collection("auditLogs").document(requestId + "-EMAIL_SENT-" + (resend ? sentAt.toEpochMilli() : "INITIAL"))
                    .set(audit(requestId, resend ? "OFFER_RESENT" : "EMAIL_SENT", actor, "SMTP accepted the offer PDF attachment.")).get();
            createEmployee(db, requestId, details, actor);
            return passport(requestId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw server("Offer delivery could not be completed", e);
        }
    }

    public Map<String, Object> whatIf(String requestId, WhatIfRequest change, Authentication auth) {
        requireRole(auth, "HR_ADMIN");
        try {
            Firestore db = db();
            DocumentSnapshot workflow = db.collection("workflowRequests").document(requestId).get().get();
            if (!workflow.exists()) throw notFound("Hiring workflow not found.");
            CandidateData current = fromMap(castMap(workflow.get("extractedDetails")));
            CandidateData changed = new CandidateData(current.name(), current.email(), current.position(),
                    change.annualCtc() == null ? current.annualCtc() : change.annualCtc(),
                    StringUtils.hasText(change.joiningDate()) ? change.joiningDate() : current.joiningDate(),
                    current.department(), current.location(), current.manager(), current.probationMonths(), current.requiredSkills());
            List<PolicyCheck> before = evaluate(db, current, requestId);
            List<PolicyCheck> after = evaluate(db, changed, requestId);
            return Map.of("requestId", requestId, "currentChecks", before, "previewChecks", after,
                    "changedCandidate", changed, "changesPersisted", false);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw server("What-if policy evaluation failed", e);
        }
    }

    public Map<String, Object> passport(String requestId) {
        try {
            Firestore db = db();
            DocumentSnapshot workflow = db.collection("workflowRequests").document(requestId).get().get();
            if (!workflow.exists()) throw notFound("Hiring workflow not found.");
            DocumentSnapshot candidate = db.collection("candidates").document(requestId).get().get();
            DocumentSnapshot employee = db.collection("employees").document(requestId).get().get();
            return Map.of(
                    "requestId", requestId,
                    "workflow", workflow.getData(),
                    "candidate", candidate.exists() ? candidate.getData() : Map.of(),
                    "employee", employee.exists() ? employee.getData() : Map.of(),
                    "approvals", query(db, "approvals", requestId),
                    "documents", query(db, "documents", requestId),
                    "auditEvents", query(db, "auditLogs", requestId)
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw server("Decision Passport could not be loaded", e);
        }
    }

    public List<Map<String, Object>> list(Authentication auth) {
        requireAnyRole(auth, "HR_ADMIN", "HIRING_MANAGER", "LEGAL", "FINANCE", "CEO", "SUPER_ADMIN");
        try {
            return db().collection("workflowRequests").orderBy("createdAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
                    .limit(100).get().get().getDocuments().stream().map(DocumentSnapshot::getData).toList();
        } catch (Exception e) {
            throw server("Hiring workflows could not be loaded", e);
        }
    }

    private void generateOffer(String requestId) {
        try {
            Firestore db = db();
            var workflowRef = db.collection("workflowRequests").document(requestId);
            DocumentSnapshot workflow = workflowRef.get().get();
            if (!workflow.exists()) throw notFound("Hiring workflow not found.");
            DocumentSnapshot existing = db.collection("documents").document(requestId + "-offer").get().get();
            if (existing.exists() && "SENT".equals(existing.getString("emailStatus"))) return;
            if (!"FINANCE_APPROVED".equals(String.valueOf(workflow.get("lastCompletedState")))
                    && !"OFFER_GENERATING".equals(String.valueOf(workflow.get("state"))))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Finance approval is required before PDF generation.");

            Map<String, Object> details = castMap(workflow.get("extractedDetails"));
            String filename = offerFileName(String.valueOf(details.get("name")), requestId);
            Map<String, Object> metadata = documentMetadata(requestId, details, filename, "PENDING", null,
                    Map.of("uid", "SYSTEM"), null);

            WriteBatch batch = db.batch();
            batch.set(db.collection("documents").document(requestId + "-offer"), metadata);
            batch.set(workflowRef, Map.of("state", "OFFER_GENERATED", "lastCompletedState", "OFFER_GENERATED",
                    "offerDocumentId", requestId + "-offer", "emailStatus", "PENDING", "updatedAt", Instant.now().toString()), SetOptions.merge());
            batch.create(db.collection("auditLogs").document(requestId + "-OFFER_GENERATED"),
                    audit(requestId, "OFFER_GENERATED", Map.of("uid", "SYSTEM", "email", "system@novaos", "role", "SYSTEM"),
                            "Offer delivery prepared after final approval."));
            batch.commit().get();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            try {
                db().collection("workflowRequests").document(requestId).set(Map.of("state", "FAILED",
                        "failedStage", "OFFER_GENERATING", "failureReason", e.getMessage(), "updatedAt", Instant.now().toString()), SetOptions.merge()).get();
            } catch (Exception ignored) {}
            throw server("PDF generation failed", e);
        }
    }

    private List<PolicyCheck> evaluate(Firestore db, CandidateData c, String currentRequestId) throws Exception {
        List<PolicyCheck> checks = new ArrayList<>();
        List<String> missing = missing(c);
        checks.add(check("Required fields", missing.isEmpty() ? "PASS" : "FAIL",
                missing.isEmpty() ? "All mandatory fields are present." : "Missing: " + String.join(", ", missing),
                evidence("missingFields", missing)));
        checks.add(check("Email format", StringUtils.hasText(c.email()) && EMAIL.matcher(c.email()).matches() ? "PASS" : "FAIL",
                "Candidate email must be RFC-style and deliverable.", evidence("email", c.email())));

        LocalDate joining = null;
        try { joining = LocalDate.parse(c.joiningDate()); } catch (Exception ignored) {}
        checks.add(check("Joining date", joining != null && joining.isAfter(LocalDate.now()) ? "PASS" : "FAIL",
                joining == null ? "Joining date must use ISO yyyy-MM-dd." : "Joining date must be in the future.",
                evidence("joiningDate", c.joiningDate(), "today", LocalDate.now().toString())));
        checks.add(check("Positive compensation", c.annualCtc() != null && c.annualCtc() > 0 ? "PASS" : "FAIL",
                "Annual CTC must be a positive integer in INR.", evidence("annualCtc", c.annualCtc())));

        DocumentSnapshot hiring = db.collection("policies").document("hiring").get().get();
        List<String> roles = strings(hiring.get("approvedRoles"));
        checks.add(check("Approved role", containsIgnoreCase(roles, c.position()) ? "PASS" : "FAIL",
                "Position must exist in policies/hiring approvedRoles.", evidence("position", c.position(), "approvedRoles", roles)));
        checks.add(check("Department", "PASS",
                "Department is accepted as entered.", evidence("department", c.department())));

        DocumentSnapshot bands = db.collection("policies").document("salaryBands").get().get();
        Map<String, Object> allBands = castMap(bands.get("bands"));
        Map<String, Object> band = findBand(allBands, c.position());
        long min = number(band.get("minAnnualCtc")), max = number(band.get("maxAnnualCtc"));
        boolean salaryPass = c.annualCtc() != null && min > 0 && c.annualCtc() >= min && c.annualCtc() <= max;
        checks.add(check("Salary band", salaryPass ? "PASS" : "FAIL",
                salaryPass ? "Compensation is within the configured role band." : "Compensation is outside the configured role band.",
                evidence("annualCtc", c.annualCtc(), "minimum", min, "maximum", max)));

        DocumentSnapshot probation = db.collection("policies").document("probation").get().get();
        List<String> allowedProbation = strings(probation.get("allowedMonths"));
        boolean probationAllowed = c.probationMonths() != null && allowedProbation.contains(String.valueOf(c.probationMonths()));
        long defaultProbation = number(probation.get("defaultMonths"));
        String probationStatus = !probationAllowed ? "FAIL" : c.probationMonths() == defaultProbation ? "PASS" : "WARNING";
        checks.add(check("Probation policy", probationStatus,
                !probationAllowed ? "Probation must match policies/probation allowedMonths."
                        : "WARNING".equals(probationStatus) ? "Allowed exception; configured default is " + defaultProbation + " months."
                        : "Probation matches the configured default.",
                evidence("months", c.probationMonths(), "allowedMonths", allowedProbation, "defaultMonths", defaultProbation)));

        boolean duplicate = duplicate(db, "candidates", c.email(), currentRequestId) || duplicate(db, "employees", c.email(), currentRequestId);
        checks.add(check("Duplicate identity", duplicate ? "FAIL" : "PASS",
                duplicate ? "A candidate or employee with this email already exists." : "No duplicate candidate or employee was found.",
                evidence("email", c.email())));

        List<String> chain = strings(hiring.get("approvalChain"));
        checks.add(check("Approval chain", chain.equals(List.of("HIRING_MANAGER", "LEGAL", "FINANCE")) ? "PASS" : "FAIL",
                "Required chain is Manager, Legal, then Finance.", evidence("configuredChain", chain)));
        return checks;
    }

    private void ensurePolicies(Firestore db) throws Exception {
        var salary = db.collection("policies").document("salaryBands");
        var hiring = db.collection("policies").document("hiring");
        var probation = db.collection("policies").document("probation");
        if (!salary.get().get().exists()) salary.set(Map.of(
                "configuration", true, "source", "NovaOS demo minimum configuration",
                "bands", Map.of("Software Engineer", Map.of("minAnnualCtc", 800000L, "maxAnnualCtc", 1800000L)),
                "updatedAt", Instant.now().toString())).get();
        if (!hiring.get().get().exists()) hiring.set(Map.of(
                "configuration", true, "source", "NovaOS demo minimum configuration",
                "approvedRoles", List.of("Software Engineer"),
                "approvalChain", List.of("HIRING_MANAGER", "LEGAL", "FINANCE"), "updatedAt", Instant.now().toString())).get();
        if (!probation.get().get().exists()) probation.set(Map.of(
                "configuration", true, "source", "NovaOS demo minimum configuration",
                "allowedMonths", List.of("3", "6"), "defaultMonths", 6, "updatedAt", Instant.now().toString())).get();
    }

    private void createEmployee(Firestore db, String requestId, Map<String, Object> c, Map<String, Object> actor) throws Exception {
        var employeeRef = db.collection("employees").document(requestId);
        if (employeeRef.get().get().exists()) return;
        Instant now = Instant.now();
        Map<String, Object> employee = new HashMap<>(c);
        employee.put("id", requestId);
        employee.put("requestId", requestId);
        employee.put("role", c.get("position"));
        employee.put("status", "Active");
        employee.put("createdAt", now.toString());

        WriteBatch batch = db.batch();
        batch.create(employeeRef, employee);
        batch.set(db.collection("candidates").document(requestId), Map.of("status", "Employee Created", "updatedAt", now.toString()), SetOptions.merge());
        batch.set(db.collection("workflowRequests").document(requestId), Map.of("state", "EMPLOYEE_CREATED",
                "lastCompletedState", "EMPLOYEE_CREATED", "employeeCreated", true, "employeeCreatedAt", now.toString(), "updatedAt", now.toString()), SetOptions.merge());
        batch.create(db.collection("auditLogs").document(requestId + "-EMPLOYEE_CREATED"),
                audit(requestId, "EMPLOYEE_CREATED", actor, "Employee created only after successful SMTP delivery."));
        batch.commit().get();
    }

    private byte[] offerPdf(String requestId, Map<String, Object> c) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document();
        PdfWriter.getInstance(pdf, out);
        pdf.open();
        Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new BaseColor(11, 25, 44));
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, new BaseColor(26, 72, 100));
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 11, BaseColor.DARK_GRAY);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.DARK_GRAY);
        Paragraph logo = new Paragraph("NovaOS", brand); logo.setAlignment(Element.ALIGN_CENTER); pdf.add(logo);
        Paragraph heading = new Paragraph("Employment Offer Letter", title); heading.setAlignment(Element.ALIGN_CENTER); heading.setSpacingAfter(22); pdf.add(heading);
        pdf.add(new Paragraph("Offer ID: NOVA-" + requestId.substring(0, Math.min(8, requestId.length())).toUpperCase(), bold));
        pdf.add(new Paragraph("Issue Date: " + LocalDate.now(), body));
        pdf.add(new Paragraph("\nDear " + c.get("name") + ",\n", bold));
        pdf.add(new Paragraph("NovaOS is pleased to offer you employment subject to the governed approvals recorded in your Hiring Decision Passport.", body));
        PdfPTable table = new PdfPTable(2); table.setWidthPercentage(100); table.setSpacingBefore(18);
        row(table, "Position", c.get("position"), bold, body);
        row(table, "Annual CTC", "INR " + c.get("annualCtc"), bold, body);
        row(table, "Joining Date", c.get("joiningDate"), bold, body);
        row(table, "Department", c.get("department"), bold, body);
        row(table, "Location", c.get("location"), bold, body);
        row(table, "Reporting Manager", c.get("manager"), bold, body);
        row(table, "Probation", c.get("probationMonths") + " months", bold, body);
        pdf.add(table);
        pdf.add(new Paragraph("\nThis offer remains subject to NovaOS employment policies, background verification, and your written acceptance.", body));
        pdf.add(new Paragraph("\nSincerely,\nNovaOS Human Resources", bold));
        pdf.close();
        return out.toByteArray();
    }

    private void sendEmail(Map<String, Object> c, byte[] pdf, String requestId) throws Exception {
        String recipient = String.valueOf(c.get("email"));
        logger.info("Sending offer email via SMTP host={}, port={}, sender={}, recipient={}", mailHost, mailPort, mailFrom, recipient);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(mailFrom, mailFromName);
        helper.setTo(recipient);
        helper.setSubject("Your Offer Letter – " + mailFromName);
        helper.setText("Dear " + c.get("name") + ",\n\nCongratulations! Your offer letter has been approved and generated successfully.\n\nPlease find your offer letter attached to this email. Kindly review the document carefully.\n\nRegards,\nHR Team\n" + mailFromName);
        helper.addAttachment(offerFileName(String.valueOf(c.get("name")), requestId), new ByteArrayResource(pdf), "application/pdf");
        mailSender.send(message);
    }

    private Map<String, Object> documentMetadata(String requestId, Map<String, Object> details, String filename,
            String emailStatus, String emailSentAt, Map<String, Object> actor, String errorMessage) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("requestId", requestId);
        metadata.put("candidateName", String.valueOf(details.get("name")));
        metadata.put("candidateEmail", String.valueOf(details.get("email")));
        metadata.put("documentType", "OFFER_LETTER");
        if (!"FAILED".equals(emailStatus)) metadata.put("generatedAt", Instant.now().toString());
        metadata.put("emailStatus", emailStatus);
        metadata.put("sentBy", actor.get("uid"));
        metadata.put("finalApprovalStatus", "APPROVALS_COMPLETED");
        metadata.put("documentFileName", filename);
        if (emailSentAt != null) metadata.put("emailSentAt", emailSentAt);
        if (errorMessage != null) metadata.put("emailErrorMessage", errorMessage);
        return metadata;
    }

    private String offerFileName(String candidateName, String requestId) {
        String sanitized = candidateName.replaceAll("[^A-Za-z0-9]", "_").replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return "Offer_Letter_" + (sanitized.isBlank() ? "Candidate" : sanitized) + "_" + requestId + ".pdf";
    }

    private String safeMessage(Throwable error) {
        String message = Objects.toString(error == null ? null : error.getMessage(), "Email delivery failed");
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private String rootCauseMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getClass().getName() + ": " + Objects.toString(cause.getMessage(), "No message");
    }

    private Map<String, Object> actor(Authentication auth, Firestore db) throws Exception {
        String uid = String.valueOf(auth.getPrincipal());
        DocumentSnapshot user = db.collection("users").document(uid).get().get();
        if (!user.exists()) throw forbidden("Authenticated user profile is missing from users/" + uid);
        return Map.of("uid", uid, "email", Objects.toString(user.get("email"), ""),
                "role", normalizeRole(Objects.toString(user.get("role"), "")));
    }

    private void requireMailCredentials() {
        if (!StringUtils.hasText(mailUsername))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_USERNAME is missing. Create a Gmail account/app sender and set MAIL_USERNAME in backend/.env.");
        if (!StringUtils.hasText(mailPassword))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_PASSWORD is missing. Create a Google Account App Password at https://myaccount.google.com/apppasswords and set MAIL_PASSWORD in backend/.env.");
    }

    private Firestore db() {
        try { return FirestoreClient.getFirestore(); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Firebase Admin is unavailable. Configure FIREBASE_PROJECT_ID and a service account in backend/.env.", e); }
    }

    private List<Map<String, Object>> query(Firestore db, String collection, String requestId) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QueryDocumentSnapshot doc : db.collection(collection).whereEqualTo("requestId", requestId).get().get().getDocuments())
            rows.add(doc.getData());
        rows.sort(Comparator.comparing(row -> Objects.toString(row.getOrDefault("timestamp", row.getOrDefault("createdAt", "")))));
        return rows;
    }

    private boolean duplicate(Firestore db, String collection, String email, String currentId) throws Exception {
        if (!StringUtils.hasText(email)) return false;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return db.collection(collection).get().get().getDocuments().stream()
                .anyMatch(doc -> (currentId == null || !doc.getId().equals(currentId))
                        && normalized.equals(Objects.toString(doc.get("email"), "").trim().toLowerCase(Locale.ROOT)));
    }

    private Map<String, Object> aiCandidateMap(CandidateData c) {
        Map<String, Object> map = new HashMap<>();
        if (c == null) return map;
        map.put("name", c.name()); map.put("email", c.email()); map.put("position", c.position());
        map.put("annualCtc", c.annualCtc()); map.put("joiningDate", c.joiningDate()); map.put("department", c.department());
        map.put("location", c.location()); map.put("manager", c.manager()); map.put("probationMonths", c.probationMonths());
        map.put("requiredSkills", c.requiredSkills() == null ? List.of() : c.requiredSkills());
        return map;
    }

    private String requireUuid(String requestId) {
        if (!StringUtils.hasText(requestId)) throw bad("requestId UUID is required.");
        try { return UUID.fromString(requestId).toString(); }
        catch (IllegalArgumentException e) { throw bad("requestId must be a valid UUID."); }
    }

    private Map<String, Object> candidateMap(String id, CandidateData c, Instant now) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id); map.put("requestId", id); map.put("name", c.name()); map.put("email", c.email().toLowerCase());
        map.put("role", c.position()); map.put("position", c.position()); map.put("annualCtc", c.annualCtc());
        map.put("joiningDate", c.joiningDate()); map.put("department", c.department()); map.put("location", c.location());
        map.put("manager", c.manager()); map.put("probationMonths", c.probationMonths()); map.put("requiredSkills", c.requiredSkills());
        map.put("status", "Applied"); map.put("source", "AI Command Center"); map.put("createdAt", now.toString()); map.put("updatedAt", now.toString());
        return map;
    }

    private CandidateData fromMap(Map<String, Object> m) {
        return new CandidateData(Objects.toString(m.get("name"), ""), Objects.toString(m.get("email"), ""),
                Objects.toString(m.getOrDefault("position", m.get("role")), ""), numberObj(m.get("annualCtc")),
                Objects.toString(m.get("joiningDate"), ""), Objects.toString(m.get("department"), ""),
                Objects.toString(m.get("location"), ""), Objects.toString(m.get("manager"), ""),
                (int) number(m.get("probationMonths")), strings(m.get("requiredSkills")));
    }

    private List<String> missing(CandidateData c) {
        List<String> m = new ArrayList<>();
        if (c == null) return REQUIRED;
        if (!StringUtils.hasText(c.name())) m.add("name");
        if (!StringUtils.hasText(c.email())) m.add("email");
        if (!StringUtils.hasText(c.position())) m.add("position");
        if (c.annualCtc() == null) m.add("annualCtc");
        if (!StringUtils.hasText(c.joiningDate())) m.add("joiningDate");
        if (!StringUtils.hasText(c.department())) m.add("department");
        if (!StringUtils.hasText(c.location())) m.add("location");
        if (!StringUtils.hasText(c.manager())) m.add("manager");
        if (c.probationMonths() == null) m.add("probationMonths");
        if (c.requiredSkills() == null || c.requiredSkills().isEmpty()) m.add("requiredSkills");
        return m;
    }

    private PolicyCheck check(String name, String status, String reason, Map<String, Object> evidence) {
        return new PolicyCheck(name, status, reason, evidence);
    }
    private Map<String, Object> evidence(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) map.put(String.valueOf(values[i]), values[i + 1]);
        return map;
    }
    private Map<String, Object> audit(String requestId, String action, Map<String, Object> actor, String details) {
        return new HashMap<>(Map.of("requestId", requestId, "action", action, "actor", actor,
                "details", Objects.toString(details, ""), "timestamp", Instant.now().toString()));
    }
    private Map<String, Object> notification(String requestId, String role, String title, String message) {
        return new HashMap<>(Map.of("requestId", requestId, "targetRole", role, "title", title, "message", message,
                "read", false, "timestamp", Instant.now().toString()));
    }
    private void row(PdfPTable table, String label, Object value, Font labelFont, Font valueFont) {
        PdfPCell a = new PdfPCell(new Phrase(label, labelFont)); a.setPadding(8); a.setBackgroundColor(new BaseColor(245, 248, 252));
        PdfPCell b = new PdfPCell(new Phrase(Objects.toString(value, ""), valueFont)); b.setPadding(8);
        table.addCell(a); table.addCell(b);
    }
    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }
    private boolean containsIgnoreCase(List<String> values, String target) {
        return target != null && values.stream().anyMatch(v -> v.equalsIgnoreCase(target));
    }
    private Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return new HashMap<>();
        Map<String, Object> map = new HashMap<>(); raw.forEach((k, v) -> map.put(String.valueOf(k), v)); return map;
    }
    private Map<String, Object> findBand(Map<String, Object> bands, String role) {
        for (var entry : bands.entrySet()) if (entry.getKey().equalsIgnoreCase(role)) return castMap(entry.getValue());
        return Map.of();
    }
    private long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(Objects.toString(value, "0")); } catch (Exception e) { return 0; }
    }
    private Long numberObj(Object value) { long n = number(value); return n == 0 ? null : n; }
    private String normalizeRole(String role) {
        String r = role.toUpperCase().trim().replaceAll("[\\s-]+", "_");
        if ("MANAGER".equals(r)) return "HIRING_MANAGER";
        if ("HR".equals(r)) return "HR_ADMIN";
        return r;
    }
    private void requireRole(Authentication auth, String role) {
        requireAnyRole(auth, role);
    }
    private void requireAnyRole(Authentication auth, String... roles) {
        if (auth == null || roles == null || Arrays.stream(roles).noneMatch(r -> auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + r)))) throw forbidden("You are not authorized for this hiring action.");
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException forbidden(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException server(String prefix, Exception e) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, prefix + ": " + e.getMessage(), e);
    }
}
