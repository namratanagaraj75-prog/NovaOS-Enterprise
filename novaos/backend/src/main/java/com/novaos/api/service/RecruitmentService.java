package com.novaos.api.service;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.novaos.api.entity.Candidate;
import com.novaos.api.entity.Employee;
import com.novaos.api.repository.EmployeeRepository;
import com.novaos.api.ai.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecruitmentService {

    private static final Logger logger = LoggerFactory.getLogger(RecruitmentService.class);

    private final CandidateService candidateService;
    private final EmployeeRepository employeeRepository;
    private final GeminiService geminiService;
    private final ResendEmailService resendEmailService;

    public RecruitmentService(CandidateService candidateService,
                              EmployeeRepository employeeRepository,
                              GeminiService geminiService,
                              ResendEmailService resendEmailService) {
        this.candidateService = candidateService;
        this.employeeRepository = employeeRepository;
        this.geminiService = geminiService;
        this.resendEmailService = resendEmailService;
    }

    /**
     * Vets a new candidate profile, updating scores and metadata utilizing Gemini AI summaries.
     */
    public Candidate vetAndRegisterCandidate(Candidate candidate) {
        logger.info("Vetting applicant: {} for position: {}", candidate.getName(), candidate.getRole());

        // Use whatever profile context we actually have - the AI Summary from the
        // AI Command Center extraction step, if present, otherwise a minimal note.
        String profileText = (candidate.getAiSummary() != null && !candidate.getAiSummary().isBlank())
                ? candidate.getAiSummary()
                : "Candidate applying for the " + candidate.getRole() + " position.";

        GeminiService.CandidateAnalysis analysis =
                geminiService.analyzeCandidateResume(candidate.getName(), profileText, candidate.getRole());

        candidate.setMatchScore(analysis.matchScore);
        candidate.setAiSummary(analysis.roleCompatibility);
        candidate.setStatus("Vetted");

        return candidateService.saveCandidate(candidate);
    }

    /**
     * Approves an offered candidate, provisions a corporate employee record, and creates a
     * real Firebase Authentication account (no password set - the employee sets their own
     * via the emailed reset link, per Firebase's recommended passwordless provisioning flow).
     */
    public Employee approveAndPromoteToEmployee(String candidateId) {
        logger.info("Promoting candidate ID: {} to Employee status", candidateId);

        Candidate candidate = candidateService.getCandidateById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        candidate.setStatus("Onboarded");
        candidateService.saveCandidate(candidate);

        Employee employee = new Employee();
        employee.setName(candidate.getName());
        employee.setEmail(candidate.getEmail());
        employee.setCorporateRole(candidate.getRole());
        employee.setStatus("Active");
        employee.setFirebaseUid(provisionFirebaseAccount(candidate.getEmail(), candidate.getName()));

        Employee saved = employeeRepository.save(employee);
        sendWelcomeEmail(candidate.getEmail(), candidate.getName(), candidate.getRole());
        return saved;
    }

    /**
     * Creates a real Firebase Auth user for the new employee, or reuses the existing one if
     * this candidate's email was already provisioned (e.g. re-running an approval).
     */
    private String provisionFirebaseAccount(String email, String displayName) {
        try {
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setDisplayName(displayName)
                    .setEmailVerified(false)
                    .setDisabled(false);
            UserRecord userRecord = FirebaseAuth.getInstance().createUser(request);
            logger.info("Created Firebase Auth user {} for {}", userRecord.getUid(), email);
            return userRecord.getUid();
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                logger.warn("Firebase user already exists for {}, reusing existing account", email);
                try {
                    return FirebaseAuth.getInstance().getUserByEmail(email).getUid();
                } catch (FirebaseAuthException lookupEx) {
                    throw new RuntimeException("Firebase account exists for " + email
                            + " but could not be looked up: " + lookupEx.getMessage(), lookupEx);
                }
            }
            throw new RuntimeException("Failed to create Firebase Auth user for " + email
                    + ": " + e.getMessage(), e);
        }
    }

    /**
     * Emails the new employee a password-setup link generated by Firebase Auth, so they can
     * activate their account. Failure here doesn't undo the onboarding - it's logged and
     * surfaced separately since the employee/Firebase account already exist.
     */
    private void sendWelcomeEmail(String email, String name, String role) {
        try {
            String setupLink = FirebaseAuth.getInstance().generatePasswordResetLink(email);

            String body = String.format(
                "Hi %s,\n\n" +
                "Welcome aboard as %s! Your NovaOS employee account has been created.\n\n" +
                "Set your password here to activate it:\n%s\n\n" +
                "If you weren't expecting this, please contact HR.\n\n" +
                "Best,\nNovaOS Talent Acquisition Team",
                name, role, setupLink
            );
            resendEmailService.sendEmail(email, "Welcome to NovaOS - Set up your account", body, null, null);
            logger.info("Sent onboarding welcome email via Resend to {}", email);
        } catch (Exception e) {
            logger.error("Employee account was created but the welcome email failed to send to {}: {}",
                    email, e.getMessage());
        }
    }
}

