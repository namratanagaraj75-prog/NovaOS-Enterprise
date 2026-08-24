package com.novaos.api.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.novaos.api.dto.CandidateRejectionDtos.RejectCandidateRequest;
import com.novaos.api.exception.EmailProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class CandidateRejectionService {
    private static final Logger logger = LoggerFactory.getLogger(CandidateRejectionService.class);
    private static final Set<String> REJECTION_ROLES = Set.of("HR_ADMIN", "HIRING_MANAGER", "FINANCE", "LEGAL", "SUPER_ADMIN");
    private static final Set<String> COMPLETED = Set.of("APPROVALS_COMPLETED", "GENERATING_OFFER", "OFFER_GENERATED",
            "EMAIL_SENDING", "EMAIL_SENT", "WORKFLOW_COMPLETED", "APPROVED", "PDF_GENERATED");

    private final ResendEmailService emailService;
    private final OfferLetterPdfService pdfService;
    private final ReviewNotificationService reviewNotifications;

    public CandidateRejectionService(ResendEmailService emailService, OfferLetterPdfService pdfService,
                                     ReviewNotificationService reviewNotifications) {
        this.emailService = emailService;
        this.pdfService = pdfService;
        this.reviewNotifications = reviewNotifications;
    }

    public Map<String, Object> reject(String requestId, RejectCandidateRequest request, Authentication auth) {
        String reason = request == null ? null : trim(request.internalRejectionReason());
        if (!StringUtils.hasText(reason)) throw bad("An internal rejection reason is required.");
        String role = role(auth);
        if (!REJECTION_ROLES.contains(role)) throw forbidden("You are not authorized to reject candidates.");

        Firestore db = db();
        DocumentReference ref = db.collection("hiringRequests").document(requestId);
        try {
            Map<String, Object> actor = actor(db, auth, role);
            db.runTransaction(transaction -> {
                DocumentSnapshot candidate = transaction.get(ref).get();
                if (!candidate.exists()) throw notFound("Hiring request not found: " + requestId);
                String status = candidate.getString("status");
                if ("REJECTED".equals(status)) throw conflict("Candidate has already been rejected.");
                if (COMPLETED.contains(status)) throw conflict("A completed hiring workflow cannot be rejected.");
                authorize(candidate, actor, role);

                Timestamp now = Timestamp.now();
                String department = department(role);
                Map<String, Object> notification = new LinkedHashMap<>();
                notification.put("status", "PENDING");
                notification.put("provider", "RESEND");
                notification.put("sentAt", null);
                notification.put("messageId", null);
                notification.put("lastError", null);
                notification.put("attemptCount", 0L);

                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "REJECTED");
                updates.put("rejected", true);
                updates.put("rejectedByDepartment", department);
                updates.put("rejectedByUserId", actor.get("uid"));
                updates.put("rejectedByName", actor.get("name"));
                updates.put("internalRejectionReason", reason);
                updates.put("rejectionReason", reason); // legacy internal field
                if (StringUtils.hasText(request.candidateRejectionMessage()))
                    updates.put("candidateRejectionMessage", trim(request.candidateRejectionMessage()));
                updates.put("rejectedAt", now);
                updates.put("updatedAt", now);
                updates.put("currentApproverRole", FieldValue.delete());
                updates.put("workflowTerminated", true);
                updates.put("candidateEmailNotification", notification);
                updates.put(approvalPrefix(role) + "ApprovalStatus", "REJECTED");
                updates.put("activityHistory", FieldValue.arrayUnion(activity("CANDIDATE_REJECTED", actor,
                        "Candidate rejected by " + title(department) + "; workflow terminated.", now)));
                transaction.update(ref, updates);
                transaction.set(db.collection("candidates").document(requestId),Map.of(
                        "currentStatus","REJECTED","currentStage","TERMINATED","updatedAt",now),SetOptions.merge());
                DocumentReference audit = db.collection("workflowEvents").document();
                transaction.create(audit, audit(requestId, department+"_REJECTED", actor, department,
                        Map.of("previousStatus", Objects.toString(status, ""), "newStatus", "REJECTED"), now));
                transaction.create(db.collection("workflowEvents").document(),audit(requestId,"WORKFLOW_TERMINATED",actor,department,
                        Map.of("reason","Candidate rejected by "+department),now));
                return null;
            }).get();

            reviewNotifications.cancelForRequest(requestId);
            sendNotification(db, ref, requestId, actor, false);
            return response(ref);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Candidate rejection failed.", error);
        }
    }

    public Map<String, Object> retry(String requestId, Authentication auth) {
        String role = role(auth);
        if (!REJECTION_ROLES.contains(role)) throw forbidden("You are not authorized to retry rejection emails.");
        Firestore db = db();
        DocumentReference ref = db.collection("hiringRequests").document(requestId);
        try {
            DocumentSnapshot candidate = ref.get().get();
            if (!candidate.exists()) throw notFound("Hiring request not found: " + requestId);
            if (!"REJECTED".equals(candidate.getString("status"))) throw conflict("Only rejected candidates have rejection notifications.");
            if (!Set.of("HR_ADMIN", "SUPER_ADMIN").contains(role)
                    && !department(role).equals(candidate.getString("rejectedByDepartment")))
                throw forbidden("Only HR, an administrator, or the rejecting department may retry this notification.");
            Map<String, Object> notification = map(candidate.get("candidateEmailNotification"));
            if (!"FAILED".equals(notification.get("status")))
                throw conflict("The rejection email is not in a failed state.");
            Map<String, Object> actor = actor(db, auth, role);
            sendNotification(db, ref, requestId, actor, true);
            return response(ref);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rejection email retry failed.", error);
        }
    }

    public byte[] rejectionPdf(String requestId, Authentication auth) {
        String role = role(auth);
        if (!REJECTION_ROLES.contains(role) && !"CEO".equals(role)) throw forbidden("You are not authorized to view rejection documents.");
        try {
            DocumentSnapshot candidate = db().collection("hiringRequests").document(requestId).get().get();
            if (!candidate.exists()) throw notFound("Hiring request not found: " + requestId);
            if (!"REJECTED".equals(candidate.getString("status")) || !"GENERATED".equals(candidate.getString("rejectionLetterStatus")))
                throw notFound("Rejection letter has not been generated.");
            return pdfService.generateRejection(candidate.getData());
        } catch (ResponseStatusException error) { throw error; }
        catch (Exception error) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rejection letter preview failed.", error); }
    }

    private void sendNotification(Firestore db, DocumentReference ref, String requestId,
                                  Map<String, Object> actor, boolean retry) throws Exception {
        Timestamp attemptedAt = Timestamp.now();
        DocumentSnapshot candidate = db.runTransaction(transaction -> {
            DocumentSnapshot value=transaction.get(ref).get();
            Map<String,Object> state=map(value.get("candidateEmailNotification"));
            if(Set.of("SENT","SENDING").contains(Objects.toString(state.get("status"),"")))return null;
            long nextAttempt=number(state.get("attemptCount"))+1;
            transaction.update(ref,Map.of("candidateEmailNotification",notification("SENDING",null,null,nextAttempt,attemptedAt),"rejectionEmailStatus","SENDING"));
            return value;
        }).get();
        if(candidate==null)return;
        long attempt=number(map(candidate.get("candidateEmailNotification")).get("attemptCount"))+1;
        byte[] pdfBytes = null;
        String safeName = sanitize(candidate.getString("candidateName"));
        String filename = "NovaOS_Rejection_Letter_" + safeName + ".pdf";
        db.collection("emailNotifications").document(requestId+"-rejection").set(Map.of(
                "candidateId",requestId,"requestId",requestId,"type","REJECTION","recipient",Objects.toString(candidate.getString("candidateEmail"),""),
                "status","SENDING","provider","RESEND","attemptedAt",attemptedAt,"attemptCount",attempt),SetOptions.merge()).get();
        try {
            pdfBytes = pdfService.generateRejection(candidate.getData());
            if (!"GENERATED".equals(candidate.getString("rejectionLetterStatus"))) {
                Timestamp generatedAt = Timestamp.now();
                Map<String,Object> document = new LinkedHashMap<>();
                document.put("requestId", requestId); document.put("candidateId", requestId);
                document.put("candidateName", candidate.getString("candidateName")); document.put("candidateEmail", candidate.getString("candidateEmail"));
                document.put("documentType", "REJECTION_LETTER"); document.put("status","GENERATED"); document.put("generatedAt", generatedAt);
                document.put("documentFileName", filename); document.put("fileName",filename);document.put("fileUrl","/api/hiring/requests/"+requestId+"/rejection-letter");document.put("emailStatus", "PENDING");
                WriteBatch batch = db.batch();
                batch.update(ref, Map.of("rejectionLetterStatus", "GENERATED", "rejectionPdfUrl", "/api/hiring/requests/"+requestId+"/rejection-letter",
                        "rejectionPdfFileName", filename, "rejectionPdfGeneratedAt", generatedAt,
                        "activityHistory", FieldValue.arrayUnion(activity("REJECTION_PDF_GENERATED", actor, "Professional candidate-facing rejection letter generated.", generatedAt))));
                batch.set(db.collection("documents").document(requestId+"-rejection"), document, SetOptions.merge());
                batch.commit().get();
            }
            String candidateName = Objects.toString(candidate.getString("candidateName"), "Candidate");
            String jobTitle = Objects.toString(candidate.getString("jobTitle"), "the role");
            String custom = candidate.getString("candidateRejectionMessage");
            String body = rejectionBody(candidateName, jobTitle, custom);
            EmailDeliveryReceipt receipt = emailService.send(new EmailDeliveryRequest(
                    "rejection-" + requestId, candidate.getString("candidateEmail"), "Application Update \u2013 Nova HR", body, filename, pdfBytes));
            if (!StringUtils.hasText(receipt.messageId())) throw new IllegalStateException("Email provider returned no message identifier.");
            Timestamp sentAt = Timestamp.now();
            ref.update(Map.of("candidateEmailNotification", notification("SENT", receipt.messageId(), null, attempt, sentAt),
                    "rejectionEmailStatus", "SENT", "rejectionEmailSentAt", sentAt,
                    "activityHistory", FieldValue.arrayUnion(activity("REJECTION_EMAIL_SENT", actor, "Rejection email delivered with PDF attachment.", sentAt)))).get();
            db.collection("documents").document(requestId+"-rejection").set(Map.of("emailStatus","SENT","emailSentAt",sentAt,"emailMessageId",receipt.messageId()),SetOptions.merge()).get();
            db.collection("emailNotifications").document(requestId+"-rejection").set(Map.of(
                    "candidateId",requestId,"requestId",requestId,"type","REJECTION","recipient",Objects.toString(candidate.getString("candidateEmail"),""),
                    "status","SENT","provider",receipt.provider(),"messageId",receipt.messageId(),"sentAt",sentAt,"attemptCount",attempt),SetOptions.merge()).get();
            writeAudit(db, requestId, "REJECTION_EMAIL_SENT", actor,
                    Map.of("provider", "RESEND", "messageId", receipt.messageId(), "retry", retry), sentAt);
        } catch (Exception error) {
            String code = error instanceof EmailProviderException provider ? provider.getErrorCode() : "EMAIL_DELIVERY_FAILED";
            String safeError = error instanceof EmailProviderException provider ? provider.getMessage() : "Email delivery failed. Please retry or contact the administrator.";
            logger.error("Rejection email delivery failed for request {} with code {}: {}", requestId, code, safeError);
            Timestamp failedAt = Timestamp.now();
            Map<String,Object> failure = new HashMap<>();
            failure.put("candidateEmailNotification", notification("FAILED", null, safeError, attempt, failedAt));
            failure.put("rejectionEmailStatus", "FAILED");
            failure.put("rejectionEmailErrorCode", code); failure.put("rejectionEmailErrorMessage", safeError);
            if (pdfBytes == null) failure.put("rejectionLetterStatus", "FAILED");
            failure.put("activityHistory", FieldValue.arrayUnion(activity("REJECTION_EMAIL_FAILED", actor, "Candidate remains rejected; document/email processing failed.", failedAt)));
            ref.update(failure).get();
            db.collection("documents").document(requestId+"-rejection").set(Map.of("emailStatus","FAILED","emailErrorCode",code,"emailErrorMessage",safeError),SetOptions.merge()).get();
            db.collection("emailNotifications").document(requestId+"-rejection").set(Map.of(
                    "candidateId",requestId,"requestId",requestId,"type","REJECTION","recipient",Objects.toString(candidate.getString("candidateEmail"),""),
                    "status","FAILED","provider","RESEND","lastError",safeError,"attemptCount",attempt,"updatedAt",failedAt),SetOptions.merge()).get();
            writeAudit(db, requestId, "REJECTION_EMAIL_FAILED", actor,
                    Map.of("errorCode", code, "retry", retry), failedAt);
        } finally { if (pdfBytes != null) Arrays.fill(pdfBytes, (byte)0); }
    }

    static String rejectionBody(String candidateName, String jobTitle, String candidateVisibleMessage) {
        String message = StringUtils.hasText(candidateVisibleMessage)
                ? "\n\n" + candidateVisibleMessage.trim() : "";
        return "Dear " + candidateName + ",\n\n"
                + "Thank you for taking the time to participate in our recruitment process and for your interest in joining our organization.\n\n"
                + "After careful consideration, we regret to inform you that we will not be moving forward with your application for the "
                + jobTitle + " position at this time."
                + message + "\n\nWe sincerely appreciate the time and effort you invested throughout the process. "
                + "We encourage you to consider future opportunities that may align with your experience and skills.\n\n"
                + "We wish you continued success in your career.\n\nWarm regards,\nNova HR Team";
    }

    private Map<String, Object> response(DocumentReference ref) throws Exception {
        DocumentSnapshot document = ref.get().get();
        Map<String, Object> result = new HashMap<>(document.getData());
        result.put("id", document.getId());
        return result;
    }

    private void authorize(DocumentSnapshot candidate, Map<String, Object> actor, String role) {
        if ("SUPER_ADMIN".equals(role) || "HR_ADMIN".equals(role)) return;
        if ("HIRING_MANAGER".equals(role)) {
            if (!"PENDING_MANAGER_APPROVAL".equals(candidate.getString("status"))
                    || !Objects.equals(candidate.getString("hiringManagerId"), actor.get("uid")))
                throw forbidden("Only the assigned hiring manager may reject this request.");
            return;
        }
        if (!role.equals(candidate.getString("currentApproverRole")))
            throw forbidden("This request is not awaiting " + role + " review.");
    }

    private Map<String, Object> actor(Firestore db, Authentication auth, String role) throws Exception {
        DocumentSnapshot user = db.collection("users").document(auth.getName()).get().get();
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("uid", auth.getName());
        actor.put("name", Optional.ofNullable(user.getString("displayName")).orElseGet(() ->
                Optional.ofNullable(user.getString("name")).orElse("NovaOS Reviewer")));
        actor.put("email", Objects.toString(user.getString("email"), ""));
        actor.put("role", role);
        return actor;
    }

    private Map<String, Object> notification(String status, String messageId, String error,
                                             long attempt, Timestamp at) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", status); value.put("provider", "RESEND");
        value.put("sentAt", "SENT".equals(status) ? at : null);
        value.put("lastAttemptAt", at); value.put("messageId", messageId);
        value.put("lastError", error); value.put("attemptCount", attempt);
        return value;
    }

    private Map<String, Object> activity(String action, Map<String, Object> actor, String details, Timestamp at) {
        return Map.of("action", action, "performedBy", actor.get("uid"), "performedByName", actor.get("name"),
                "timestamp", at, "details", details);
    }

    private Map<String, Object> audit(String id, String action, Map<String, Object> actor, String department,
                                      Map<String, Object> metadata, Timestamp at) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("requestId", id); value.put("candidateId", id); value.put("action", action);value.put("eventType",action);
        value.put("department", department); value.put("performedBy", actor.get("uid"));
        value.put("performedByName", actor.get("name")); value.put("timestamp", at); value.put("metadata", metadata);
        return value;
    }

    private void writeAudit(Firestore db, String id, String action, Map<String, Object> actor,
                            Map<String, Object> metadata, Timestamp at) throws Exception {
        db.collection("workflowEvents").document().create(audit(id, action, actor,
                Objects.toString(actor.get("role"), ""), metadata, at)).get();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private Firestore db() { return FirestoreClient.getFirestore(); }
    private String role(Authentication auth) { if (auth == null) return ""; return auth.getAuthorities().stream().map(a -> a.getAuthority())
            .filter(v -> v.startsWith("ROLE_")).map(v -> v.substring(5)).findFirst().orElse(""); }
    private String department(String role) { return "HIRING_MANAGER".equals(role) ? "HIRING_MANAGER" : role.replace("_ADMIN", ""); }
    private String approvalPrefix(String role) { return switch (role) { case "HIRING_MANAGER" -> "manager"; case "FINANCE" -> "finance";
        case "LEGAL" -> "legal"; case "HR_ADMIN", "SUPER_ADMIN" -> "hr"; default -> "review"; }; }
    private String title(String value) { return Arrays.stream(value.split("_")).map(v -> v.substring(0, 1) + v.substring(1).toLowerCase(Locale.ROOT)).reduce((a,b)->a+" "+b).orElse(value); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String sanitize(String value) { String safe=Objects.toString(value,"Candidate").replaceAll("[^A-Za-z0-9]","_").replaceAll("_+","_").replaceAll("^_+|_+$","");return safe.isBlank()?"Candidate":safe; }
    private ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private ResponseStatusException forbidden(String m) { return new ResponseStatusException(HttpStatus.FORBIDDEN, m); }
    private ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
    private ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
