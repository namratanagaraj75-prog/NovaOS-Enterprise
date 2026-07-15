package com.novaos.api.devsetup;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit, one-time development utility for provisioning the NovaOS approval roles.
 * Disabled unless NOVAOS_SETUP_HIRING_MANAGER_ENABLED=true is supplied at runtime.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "novaos.setup.hiring-manager.enabled", havingValue = "true")
public class HiringManagerSetupRunner implements ApplicationRunner {
    private static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("rahul.manager@nova.com", "Rahul Verma", "HIRING_MANAGER", "Hiring Manager", "Engineering"),
            new DemoAccount("finance@nova.com", "Priya Shah", "FINANCE", "Finance Approver", "Finance"),
            new DemoAccount("legal@nova.com", "Aarav Mehta", "LEGAL", "Legal Approver", "Legal")
    );

    private final String password;
    private final boolean exitAfterRun;
    private final ConfigurableApplicationContext applicationContext;

    public HiringManagerSetupRunner(
            @Value("${novaos.setup.hiring-manager.password:}") String password,
            @Value("${novaos.setup.hiring-manager.exit-after-run:true}") boolean exitAfterRun,
            ConfigurableApplicationContext applicationContext) {
        this.password = password;
        this.exitAfterRun = exitAfterRun;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            validatePassword();
            System.out.println();
            System.out.println("NovaOS approval-role setup completed");
            for (DemoAccount account : ACCOUNTS) {
                AuthResult authResult = ensureAuthenticationUser(account);
                FirestoreResult firestoreResult = ensureFirestoreUser(authResult.uid(), account);
                System.out.println(account.email() + " (" + account.role() + ")");
                System.out.println("Authentication UID: " + authResult.uid());
                System.out.println("Firestore document path: users/" + authResult.uid());
                System.out.println("Authentication user: " + (authResult.created() ? "CREATED" : "REUSED"));
                System.out.println("Firestore document: " + firestoreResult.action());
            }
            System.out.println();
        } catch (Exception error) {
            throw new IllegalStateException("Hiring Manager setup failed: " + error.getMessage(), error);
        } finally {
            if (exitAfterRun) {
                Thread shutdown = new Thread(() -> {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    applicationContext.close();
                }, "novaos-hiring-manager-setup-shutdown");
                shutdown.setDaemon(false);
                shutdown.start();
            }
        }
    }

    private void validatePassword() {
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "NOVAOS_SETUP_HIRING_MANAGER_PASSWORD is missing. Set it only for the setup command.");
        }
        if (password.length() < 6) {
            throw new IllegalStateException("The Firebase development password must contain at least 6 characters.");
        }
    }

    private AuthResult ensureAuthenticationUser(DemoAccount account) throws FirebaseAuthException {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        try {
            UserRecord existing = auth.getUserByEmail(account.email());
            auth.updateUser(new UserRecord.UpdateRequest(existing.getUid())
                    .setDisplayName(account.displayName())
                    .setPassword(password)
                    .setDisabled(false));
            return new AuthResult(existing.getUid(), false);
        } catch (FirebaseAuthException lookupError) {
            if (lookupError.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                throw lookupError;
            }
        }

        UserRecord created = auth.createUser(new UserRecord.CreateRequest()
                .setEmail(account.email())
                .setPassword(password)
                .setDisplayName(account.displayName())
                .setDisabled(false));
        return new AuthResult(created.getUid(), true);
    }

    private FirestoreResult ensureFirestoreUser(String uid, DemoAccount account) throws Exception {
        DocumentReference userRef = FirestoreClient.getFirestore().collection("users").document(uid);
        DocumentSnapshot existing = userRef.get().get();

        Map<String, Object> required = new LinkedHashMap<>();
        required.put("uid", uid);
        required.put("displayName", account.displayName());
        required.put("name", account.displayName());
        required.put("email", account.email());
        required.put("role", account.role());
        required.put("active", true);
        required.put("approved", true);
        required.put("status", "ACTIVE");
        required.put("designation", account.designation());
        required.put("department", account.department());
        required.put("createdAt", FieldValue.serverTimestamp());

        if (!existing.exists()) {
            userRef.create(required).get();
            return new FirestoreResult("CREATED");
        }

        Map<String, Object> missing = new LinkedHashMap<>();
        Map<String, Object> current = existing.getData();
        for (Map.Entry<String, Object> field : required.entrySet()) {
            Object currentValue = current == null ? null : current.get(field.getKey());
            if (isMissing(currentValue)) {
                missing.put(field.getKey(), field.getValue());
            }
        }

        if (missing.isEmpty()) {
            return new FirestoreResult("REUSED (no missing fields)");
        }

        userRef.update(missing).get();
        return new FirestoreResult("UPDATED (missing fields added: " + String.join(", ", missing.keySet()) + ")");
    }

    private boolean isMissing(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private record AuthResult(String uid, boolean created) {}
    private record FirestoreResult(String action) {}
    private record DemoAccount(String email, String displayName, String role, String designation, String department) {}
}
