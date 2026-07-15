package com.novaos.api.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Query;
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
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5175", "http://localhost:5176"}, allowCredentials = "true")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String ACCESS_DENIED = "Access Denied\nYou are not an authorized NovaOS employee.";

    private ResponseEntity<?> processVerification(String idToken, HttpServletRequest request) {
        // Dev-safe logging: record masked token previews (never log full tokens)
        try {
            String authHeader = request.getHeader("Authorization");
            String bodyPreview = idToken != null && idToken.length() > 12 ? idToken.substring(0,6) + "..." + idToken.substring(idToken.length()-6) : idToken;
            String headerPreview = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                headerPreview = token.length() > 12 ? token.substring(0,6) + "..." + token.substring(token.length()-6) : token;
            }
            logger.info("Auth verify called - idTokenPreview={}, authHeaderPreview={}", bodyPreview, headerPreview);
        } catch (Exception e) {
            // don't fail verification due to logging
            logger.debug("Could not compute token preview: {}", e.getMessage());
        }
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
                    writeAuditLog(decodedToken, null, "Login", "Denied", request, ACCESS_DENIED);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ACCESS_DENIED));
                }
            }

            Boolean activeVal = document.getBoolean("active");
            String role = document.getString("role");

            if (activeVal == null || !activeVal) {
                writeAuditLog(decodedToken, document, "Login", "Denied", request, "Inactive account");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access Denied\nYour NovaOS employee account is inactive."));
            }
            if (role == null || role.trim().isEmpty()) {
                writeAuditLog(decodedToken, document, "Login", "Denied", request, "Missing role");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access Denied\nYour NovaOS employee account has no assigned role."));
            }

            String normalizedRole = normalizeRole(role);
            if (normalizedRole.isBlank()) {
                writeAuditLog(decodedToken, document, "Login", "Denied", request, "Invalid role");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access Denied\nYour NovaOS employee account has an invalid role."));
            }

            Map<String, Object> profile = toProfile(document, decodedToken, normalizedRole);
            writeAuditLog(decodedToken, document, "Login", "Success", request, "Session created");
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
    public ResponseEntity<?> verifyToken(@RequestBody Map<String, String> requestBody, HttpServletRequest request) {
        return processVerification(requestBody.get("idToken"), request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> requestBody, HttpServletRequest request) {
        String token = requestBody.get("idToken") != null ? requestBody.get("idToken") : requestBody.get("token");
        return processVerification(token, request);
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

    private void writeAuditLog(FirebaseToken token, DocumentSnapshot userDoc, String action, String status,
                               HttpServletRequest request, String details) {
        try {
            Map<String, Object> log = new HashMap<>();
            log.put("timestamp", Instant.now().toString());
            log.put("user", token.getEmail());
            log.put("uid", token.getUid());
            log.put("role", userDoc != null ? normalizeRole(userDoc.getString("role")) : null);
            log.put("action", action);
            log.put("status", status);
            log.put("ip", clientIp(request));
            log.put("details", details);
            FirestoreClient.getFirestore().collection("auditLogs").add(log).get();
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