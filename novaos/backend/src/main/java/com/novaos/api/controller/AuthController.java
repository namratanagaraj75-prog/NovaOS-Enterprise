package com.novaos.api.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.FieldValue;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String ACCESS_DENIED = "Access Denied\nYou are not an authorized NovaOS employee.";

    private ResponseEntity<?> processVerification(String idToken, boolean recordAccess, HttpServletRequest request) {
        if (idToken == null || idToken.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing Firebase ID Token"));
        }

        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String uid = decodedToken.getUid();
            DocumentSnapshot document = FirestoreClient.getFirestore()
                    .collection("users")
                    .document(uid)
                    .get()
                    .get();

                        if (!document.exists()) {
                // Attempt fallback migration by email
                DocumentSnapshot emailDoc = FirestoreClient.getFirestore()
                        .collection("users")
                        .whereEqualTo("email", decodedToken.getEmail())
                        .limit(1)
                        .get()
                        .get()
                        .getDocuments()
                        .stream()
                        .findFirst()
                        .orElse(null);
                if (emailDoc != null && emailDoc.exists()) {
                    // Copy data to UID-keyed document
                    Map<String, Object> data = emailDoc.getData();
                    FirestoreClient.getFirestore().collection("users").document(uid).set(data).get();
                    // Delete old email-keyed document
                    FirestoreClient.getFirestore().collection("users").document(emailDoc.getId()).delete().get();
                    // Reload the UID document reference
                    document = FirestoreClient.getFirestore().collection("users").document(uid).get().get();
                } else {
                    writeAccessAudit(decodedToken, null, "DENIED", request, ACCESS_DENIED);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ACCESS_DENIED));
                }
            }

            Boolean activeVal = document.getBoolean("active");
            String role = document.getString("role");

            if (activeVal == null || !activeVal) {
                writeAccessAudit(decodedToken, document, "DENIED", request, "Inactive account");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access Denied\nYour NovaOS employee account is inactive."));
            }
            if (role == null || role.trim().isEmpty()) {
                writeAccessAudit(decodedToken, document, "DENIED", request, "Missing role");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access Denied\nYour NovaOS employee account has no assigned role."));
            }

            String normalizedRole = normalizeRole(role);
            if (normalizedRole.isBlank()) {
                writeAccessAudit(decodedToken, document, "DENIED", request, "Invalid role");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access Denied\nYour NovaOS employee account has an invalid role."));
            }

            Map<String, Object> profile = toProfile(document, decodedToken, normalizedRole);
            if (recordAccess) {
                writeAccessAudit(decodedToken, document, "SUCCESS", request, "Authenticated session established");
            }
            return ResponseEntity.ok(Map.of(
                    "token", idToken,
                    "user", profile
            ));
        } catch (Exception e) {
            logger.error("Verify: Failed Firebase verification", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication check failed: " + e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyToken(@RequestBody Map<String, Object> requestBody, HttpServletRequest request) {
        return processVerification((String) requestBody.get("idToken"), Boolean.TRUE.equals(requestBody.get("recordAccess")), request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> requestBody, HttpServletRequest request) {
        String token = requestBody.get("idToken") != null ? requestBody.get("idToken") : requestBody.get("token");
        return processVerification(token, true, request);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Successfully logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                   HttpServletRequest request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authorization header is missing or invalid"));
        }

        try {
            String idToken = authHeader.substring(7).trim();
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            DocumentSnapshot document = FirestoreClient.getFirestore()
                    .collection("users")
                    .document(decodedToken.getUid())
                    .get()
                    .get();

            if (!document.exists()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ACCESS_DENIED));
            }
            if (document.getBoolean("active") == null || !document.getBoolean("active")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access Denied\nYour NovaOS employee account is inactive."));
            }

            String normalizedRole = normalizeRole(document.getString("role"));
            if (normalizedRole.isBlank()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access Denied\nYour NovaOS employee account has no assigned role."));
            }

            return ResponseEntity.ok(toProfile(document, decodedToken, normalizedRole));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Session verification failed: " + e.getMessage()));
        }
    }

    private Map<String, Object> toProfile(DocumentSnapshot document, FirebaseToken decodedToken, String normalizedRole) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", decodedToken.getUid());
        profile.put("email", valueOrFallback(document.getString("email"), decodedToken.getEmail()));
        profile.put("displayName", valueOrFallback(document.getString("displayName"), document.getString("name")));
        profile.put("role", normalizedRole);
        profile.put("department", document.getString("department"));
        profile.put("designation", document.getString("designation"));
        profile.put("photoURL", valueOrFallback(document.getString("photoURL"), decodedToken.getPicture()));
        profile.put("active", true);
        profile.put("createdAt", document.get("createdAt") != null ? document.get("createdAt").toString() : null);
        return profile;
    }

    private String normalizeRole(String roleStr) {
        String r = (roleStr != null ? roleStr : "").toUpperCase().trim().replaceAll("[\\s-]+", "_");
        if (r.equals("CEO")) return "CEO";
        if (r.equals("SUPER_ADMIN")) return "SUPER_ADMIN";
        if (r.equals("HR") || r.equals("HR_ADMIN")) return "HR_ADMIN";
        if (r.equals("MANAGER") || r.equals("HIRING_MANAGER")) return "HIRING_MANAGER";
        if (r.equals("FINANCE")) return "FINANCE";
        if (r.equals("LEGAL")) return "LEGAL";
        return "";
    }

    private String valueOrFallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private void writeAccessAudit(FirebaseToken token, DocumentSnapshot userDoc, String status,
                               HttpServletRequest request, String details) {
        if (!"SUCCESS".equals(status)) return;
        try {
            Map<String, Object> log = new HashMap<>();
            log.put("eventType", "LOGIN");
            log.put("timestamp", FieldValue.serverTimestamp());
            log.put("userId", token.getUid());
            log.put("userEmail", token.getEmail());
            log.put("userName", userDoc != null
                    ? valueOrFallback(userDoc.getString("displayName"), userDoc.getString("name"))
                    : token.getName());
            log.put("role", userDoc != null ? normalizeRole(userDoc.getString("role")) : null);
            log.put("status", status);
            log.put("ip", clientIp(request));
            log.put("details", details);
            FirestoreClient.getFirestore().collection("accessAuditLogs").add(log).get();
        } catch (Exception e) {
            logger.warn("Could not write auth audit log: {}", e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
