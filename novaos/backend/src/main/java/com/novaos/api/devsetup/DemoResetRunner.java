package com.novaos.api.devsetup;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "novaos.demo-reset.enabled", havingValue = "true")
public class DemoResetRunner implements ApplicationRunner {
    private static final List<String> TRANSACTIONAL_COLLECTIONS = List.of(
            "hiringRequests", "candidates", "workflowEvents", "notifications", "legalReviews",
            "candidateIntelligence", "documents", "emailNotifications",
            "workflowRequests", "employees", "approvals", "auditLogs", "securityAuditLogs",
            "aiRequests", "offers", "workflows", "metrics", "policies", "policyDocuments"
    );
    private static final Set<String> PRESERVED_COLLECTIONS = Set.of(
            "users", "legalPolicies", "settings", "accessAuditLogs", "departments", "accessRequests"
    );

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        System.out.println("DemoResetRunner: starting explicitly enabled transactional-data reset.");
        for (String collection : TRANSACTIONAL_COLLECTIONS) {
            int deleted = deleteCollection(db, collection);
            System.out.println("DemoResetRunner: " + collection + " deleted=" + deleted);
        }
        System.out.println("DemoResetRunner: preserved configuration collections=" + PRESERVED_COLLECTIONS);
        System.out.println("DemoResetRunner: reset complete. Firebase Authentication was not accessed.");
    }

    private int deleteCollection(Firestore db, String collectionName) throws Exception {
        List<QueryDocumentSnapshot> docs = db.collection(collectionName).get().get().getDocuments();
        for (QueryDocumentSnapshot doc : docs) {
            for (var child : doc.getReference().listCollections()) {
                deleteCollection(db, child.getPath());
            }
            doc.getReference().delete().get();
        }
        return docs.size();
    }
}
