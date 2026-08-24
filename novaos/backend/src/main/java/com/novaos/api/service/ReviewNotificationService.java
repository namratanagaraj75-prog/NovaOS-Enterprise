package com.novaos.api.service;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class ReviewNotificationService {
    public void queueReviewNotification(WriteBatch batch, Firestore db, String requestId,
                                        String candidateName, String jobTitle, String targetRole,
                                        String targetUserId) {
        String stage = stageForRole(targetRole);
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "REVIEW_REQUIRED");
        notification.put("requestId", requestId);
        notification.put("candidateId", requestId);
        notification.put("candidateName", Objects.toString(candidateName, "Candidate"));
        notification.put("jobTitle", Objects.toString(jobTitle, ""));
        notification.put("targetRole", targetRole);
        notification.put("targetUserId", targetUserId);
        notification.put("workflowStage", stage);
        notification.put("title", titleForRole(targetRole));
        notification.put("message", Objects.toString(candidateName, "Candidate") + " is awaiting your review.");
        notification.put("read", false);
        notification.put("cancelled", false);
        notification.put("createdAt", FieldValue.serverTimestamp());
        notification.put("readAt", null);
        batch.set(db.collection("notifications").document(requestId + "-" + stage), notification);
    }

    public void resolveCurrent(WriteBatch batch, Firestore db, String requestId, String role) {
        batch.set(db.collection("notifications").document(requestId + "-" + stageForRole(role)), Map.of(
                "read", true,
                "readAt", FieldValue.serverTimestamp(),
                "resolved", true
        ), SetOptions.merge());
    }

    public void markRead(String notificationId, Authentication auth) {
        try {
            DocumentReference ref = FirestoreClient.getFirestore().collection("notifications").document(notificationId);
            DocumentSnapshot notification = ref.get().get();
            if (!notification.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found.");
            authorize(notification, auth);
            if (!Boolean.TRUE.equals(notification.getBoolean("read"))) {
                ref.update(Map.of("read", true, "readAt", FieldValue.serverTimestamp())).get();
            }
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Notification could not be marked read.", error);
        }
    }

    public void markReviewRead(String requestId, Authentication auth) {
        String role = role(auth);
        String notificationId = requestId + "-" + stageForRole(role);
        markRead(notificationId, auth);
    }

    public void cancelForRequest(String requestId) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        List<QueryDocumentSnapshot> matches = db.collection("notifications")
                .whereEqualTo("requestId", requestId).get().get().getDocuments();
        if (matches.isEmpty()) return;
        WriteBatch batch = db.batch();
        for (QueryDocumentSnapshot notification : matches) {
            batch.update(notification.getReference(), Map.of(
                    "read", true,
                    "readAt", FieldValue.serverTimestamp(),
                    "cancelled", true
            ));
        }
        batch.commit().get();
    }

    private void authorize(DocumentSnapshot notification, Authentication auth) {
        String role = role(auth);
        String targetRole = notification.getString("targetRole");
        String targetUserId = notification.getString("targetUserId");
        if (!Objects.equals(role, targetRole)
                || (targetUserId != null && !targetUserId.isBlank() && !targetUserId.equals(auth.getName()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This notification belongs to another reviewer.");
        }
    }

    private String role(Authentication auth) {
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        return auth.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "A reviewer role is required."));
    }

    private String stageForRole(String role) {
        return switch (role) {
            case "HIRING_MANAGER" -> "MANAGER_REVIEW";
            case "FINANCE" -> "FINANCE_REVIEW";
            case "LEGAL" -> "LEGAL_REVIEW";
            case "CEO" -> "CEO_REVIEW";
            case "HR_ADMIN" -> "HR_ACTION";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported notification role.");
        };
    }

    private String titleForRole(String role) {
        return switch (role) {
            case "HIRING_MANAGER" -> "Hiring Manager review required";
            case "FINANCE" -> "Finance review required";
            case "LEGAL" -> "Legal review required";
            case "CEO" -> "CEO review required";
            default -> "HR action required";
        };
    }
}
