package com.novaos.api.repository;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.novaos.api.entity.Candidate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class CandidateRepository {

    private static final String COLLECTION = "candidates";

    public List<Candidate> findAll() {
        try {
            return db().collection(COLLECTION).get().get().getDocuments().stream()
                    .map(this::fromDocument)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read candidates from Firestore", e);
        }
    }

    public List<Candidate> findByStatus(String status) {
        try {
            return db().collection(COLLECTION).whereEqualTo("status", status).get().get().getDocuments().stream()
                    .map(this::fromDocument)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read candidates by status from Firestore", e);
        }
    }

    public long count() {
        return findAll().size();
    }

    public long countByStatus(String status) {
        return findByStatus(status).size();
    }

    public Optional<Candidate> findById(String id) {
        try {
            DocumentSnapshot document = db().collection(COLLECTION).document(id).get().get();
            return document.exists() ? Optional.of(fromDocument(document)) : Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read candidate from Firestore", e);
        }
    }

    public Candidate save(Candidate candidate) {
        try {
            String id = candidate.getId();
            if (id == null || id.isBlank()) {
                id = db().collection(COLLECTION).document().getId();
                candidate.setId(id);
            }
            db().collection(COLLECTION).document(id).set(toMap(candidate)).get();
            return candidate;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save candidate to Firestore", e);
        }
    }

    public void deleteById(String id) {
        try {
            db().collection(COLLECTION).document(id).delete().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete candidate from Firestore", e);
        }
    }

    private Firestore db() {
        return FirestoreClient.getFirestore();
    }

    private Candidate fromDocument(DocumentSnapshot document) {
        Candidate candidate = new Candidate();
        Map<String, Object> data = document.getData() != null ? document.getData() : Map.of();
        candidate.setId(document.getId());
        candidate.setName(asString(data.get("name")));
        candidate.setRole(asString(data.get("role")));
        candidate.setEmail(asString(data.get("email")));
        candidate.setStatus(asString(data.get("status")));
        candidate.setMatchScore(asInteger(data.get("matchScore")));
        candidate.setSource(asString(data.get("source")));
        candidate.setAiSummary(asString(data.get("aiSummary")));
        candidate.setJoiningDate(asString(data.get("joiningDate")));
        candidate.setCtc(asString(data.get("ctc")));
        candidate.setDepartment(asString(data.get("department")));
        candidate.setResumeUrl(asString(data.get("resumeUrl")));
        candidate.setOfferLetterUrl(asString(data.get("offerLetterUrl")));
        candidate.setCreatedAt(asLocalDateTime(data.get("createdAt")));
        return candidate;
    }

    private Map<String, Object> toMap(Candidate candidate) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", candidate.getId());
        data.put("name", candidate.getName());
        data.put("role", candidate.getRole());
        data.put("email", candidate.getEmail());
        data.put("status", candidate.getStatus());
        data.put("matchScore", candidate.getMatchScore());
        data.put("source", candidate.getSource());
        data.put("aiSummary", candidate.getAiSummary());
        data.put("joiningDate", candidate.getJoiningDate());
        data.put("ctc", candidate.getCtc());
        data.put("department", candidate.getDepartment());
        data.put("resumeUrl", candidate.getResumeUrl());
        data.put("offerLetterUrl", candidate.getOfferLetterUrl());
        data.put("createdAt", candidate.getCreatedAt() != null ? candidate.getCreatedAt().toString() : LocalDateTime.now().toString());
        return data;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && !value.toString().isBlank()) {
            return Integer.parseInt(value.toString());
        }
        return 0;
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value == null || value.toString().isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value.toString());
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }
}
