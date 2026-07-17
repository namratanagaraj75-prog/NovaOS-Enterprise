package com.novaos.api.service;

import com.novaos.api.dto.HiringRequestDtos.CandidateInput;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class HiringWorkflowRules {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final Clock clock;

    public HiringWorkflowRules() { this(Clock.systemDefaultZone()); }
    HiringWorkflowRules(Clock clock) { this.clock = clock; }

    public List<String> missing(CandidateInput c) {
        List<String> fields = new ArrayList<>();
        if (c == null) return List.of("candidateName", "candidateEmail", "jobTitle", "department", "annualPackageLPA",
                "joiningDate", "reportingManagerName", "hiringManagerName", "location", "employmentType");
        if (blank(c.candidateName())) fields.add("candidateName");
        if (blank(c.candidateEmail())) fields.add("candidateEmail");
        if (blank(c.jobTitle())) fields.add("jobTitle");
        if (blank(c.department())) fields.add("department");
        if ((c.annualPackageLPA() == null || c.annualPackageLPA() <= 0)
                && (c.annualSalaryAmount() == null || c.annualSalaryAmount() <= 0)) fields.add("annualPackageLPA");
        if (blank(c.joiningDate())) fields.add("joiningDate");
        if (blank(c.reportingManagerName())) fields.add("reportingManagerName");
        if (blank(c.hiringManagerName())) fields.add("hiringManagerName");
        if (blank(c.location())) fields.add("location");
        if (blank(c.employmentType())) fields.add("employmentType");
        return fields;
    }

    public List<String> validate(CandidateInput c, boolean pastDateConfirmed) {
        List<String> errors = new ArrayList<>();
        List<String> missing = missing(c);
        if (!missing.isEmpty()) errors.add("Missing required fields: " + String.join(", ", missing));
        if (c != null && !blank(c.candidateEmail()) && !EMAIL.matcher(c.candidateEmail().trim()).matches())
            errors.add("Candidate email is invalid.");
        if (c != null && c.annualSalaryAmount() != null && c.annualSalaryAmount() <= 0)
            errors.add("Annual salary must be positive.");
        if (c != null && !blank(c.joiningDate())) {
            try {
                LocalDate date = LocalDate.parse(c.joiningDate());
                if (date.isBefore(LocalDate.now(clock)) && !pastDateConfirmed)
                    errors.add("Joining date is in the past and requires explicit HR confirmation.");
            } catch (Exception e) { errors.add("Joining date must use yyyy-MM-dd."); }
        }
        return errors;
    }

    public boolean canTransition(String from, String to) {
        return switch (from) {
            case "DRAFT" -> List.of("PENDING_MANAGER_APPROVAL").contains(to);
            case "PENDING_MANAGER_APPROVAL" -> List.of("PENDING_FINANCE_APPROVAL", "REJECTED", "CHANGES_REQUESTED").contains(to);
            case "PENDING_FINANCE_APPROVAL" -> List.of("PENDING_LEGAL_APPROVAL", "REJECTED", "CHANGES_REQUESTED").contains(to);
            case "PENDING_LEGAL_APPROVAL" -> List.of("APPROVALS_COMPLETED", "REJECTED", "CHANGES_REQUESTED").contains(to);
            case "CHANGES_REQUESTED" -> "DRAFT".equals(to);
            case "APPROVED", "LEGAL_APPROVED", "APPROVALS_COMPLETED" -> List.of("GENERATING_OFFER", "OFFER_GENERATED").contains(to);
            case "OFFER_GENERATED" -> List.of("EMAIL_SENDING", "EMAIL_SENT", "EMAIL_FAILED", "WORKFLOW_COMPLETED").contains(to);
            case "EMAIL_SENDING" -> List.of("EMAIL_SENT", "EMAIL_FAILED").contains(to);
            case "EMAIL_FAILED" -> List.of("EMAIL_SENDING", "EMAIL_SENT", "EMAIL_FAILED").contains(to);
            default -> false;
        };
    }

    public void requireTransition(String from, String to) {
        if (!canTransition(from, to)) throw new IllegalStateException("Invalid status transition: " + from + " -> " + to);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
