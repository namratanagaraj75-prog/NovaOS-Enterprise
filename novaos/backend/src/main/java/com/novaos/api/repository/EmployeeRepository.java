package com.novaos.api.repository;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.novaos.api.entity.Employee;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class EmployeeRepository {

    private static final String COLLECTION = "employees";

    public List<Employee> findAll() {
        try {
            return FirestoreClient.getFirestore().collection(COLLECTION).get().get().getDocuments().stream()
                    .map(this::fromDocument)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read employees from Firestore", e);
        }
    }

    public long count() {
        return findAll().size();
    }

    public Optional<Employee> findByEmail(String email) {
        try {
            var docs = FirestoreClient.getFirestore().collection(COLLECTION)
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .get()
                    .getDocuments();
            return docs.isEmpty() ? Optional.empty() : Optional.of(fromDocument(docs.get(0)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read employee by email from Firestore", e);
        }
    }

    public Employee save(Employee employee) {
        try {
            String id = employee.getId();
            if (id == null || id.isBlank()) {
                id = FirestoreClient.getFirestore().collection(COLLECTION).document().getId();
                employee.setId(id);
            }
            FirestoreClient.getFirestore().collection(COLLECTION).document(id).set(toMap(employee)).get();
            return employee;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save employee to Firestore", e);
        }
    }

    private Employee fromDocument(DocumentSnapshot document) {
        Map<String, Object> data = document.getData() != null ? document.getData() : Map.of();
        Employee employee = new Employee();
        employee.setId(document.getId());
        employee.setName(asString(data.get("name")));
        employee.setEmail(asString(data.get("email")));
        employee.setCorporateRole(asString(data.get("corporateRole")));
        employee.setDepartment(asString(data.get("department")));
        employee.setJoiningDate(asString(data.get("joiningDate")));
        employee.setCtc(asString(data.get("ctc")));
        employee.setFirebaseUid(asString(data.get("firebaseUid")));
        employee.setStatus(asString(data.get("status")));
        employee.setCreatedDate(asLocalDateTime(data.get("createdDate")));
        return employee;
    }

    private Map<String, Object> toMap(Employee employee) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", employee.getId());
        data.put("name", employee.getName());
        data.put("email", employee.getEmail());
        data.put("corporateRole", employee.getCorporateRole());
        data.put("department", employee.getDepartment());
        data.put("joiningDate", employee.getJoiningDate());
        data.put("ctc", employee.getCtc());
        data.put("firebaseUid", employee.getFirebaseUid());
        data.put("status", employee.getStatus());
        data.put("createdDate", employee.getCreatedDate() != null ? employee.getCreatedDate().toString() : LocalDateTime.now().toString());
        data.put("joinedAt", employee.getCreatedDate() != null ? employee.getCreatedDate().toString() : LocalDateTime.now().toString());
        return data;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
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
