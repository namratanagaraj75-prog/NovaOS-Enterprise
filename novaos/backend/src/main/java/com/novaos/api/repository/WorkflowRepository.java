package com.novaos.api.repository;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.novaos.api.entity.Workflow;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class WorkflowRepository {

    private static final String COLLECTION = "workflows";

    public List<Workflow> findAll() {
        try {
            return FirestoreClient.getFirestore().collection(COLLECTION).get().get().getDocuments().stream()
                    .map(this::fromDocument)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read workflows from Firestore", e);
        }
    }

    public List<Workflow> findByActive(Boolean active) {
        try {
            return FirestoreClient.getFirestore().collection(COLLECTION)
                    .whereEqualTo("active", active)
                    .get()
                    .get()
                    .getDocuments()
                    .stream()
                    .map(this::fromDocument)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read active workflows from Firestore", e);
        }
    }

    public Workflow save(Workflow workflow) {
        try {
            String id = workflow.getId();
            if (id == null || id.isBlank()) {
                id = FirestoreClient.getFirestore().collection(COLLECTION).document().getId();
                workflow.setId(id);
            }
            FirestoreClient.getFirestore().collection(COLLECTION).document(id).set(toMap(workflow)).get();
            return workflow;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save workflow to Firestore", e);
        }
    }

    private Workflow fromDocument(DocumentSnapshot document) {
        Map<String, Object> data = document.getData() != null ? document.getData() : Map.of();
        Workflow workflow = new Workflow();
        workflow.setId(document.getId());
        workflow.setName(asString(data.get("name")));
        workflow.setDescription(asString(data.get("description")));
        workflow.setTriggerEvent(asString(data.get("triggerEvent")));
        workflow.setActive(asBoolean(data.get("active"), true));
        workflow.setSuccessRate(asDouble(data.get("successRate"), 100.0));
        workflow.setCreatedAt(asLocalDateTime(data.get("createdAt")));
        return workflow;
    }

    private Map<String, Object> toMap(Workflow workflow) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", workflow.getId());
        data.put("name", workflow.getName());
        data.put("description", workflow.getDescription());
        data.put("triggerEvent", workflow.getTriggerEvent());
        data.put("active", workflow.getActive());
        data.put("successRate", workflow.getSuccessRate());
        data.put("createdAt", workflow.getCreatedAt() != null ? workflow.getCreatedAt().toString() : LocalDateTime.now().toString());
        return data;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Boolean asBoolean(Object value, Boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private Double asDouble(Object value, Double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
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
