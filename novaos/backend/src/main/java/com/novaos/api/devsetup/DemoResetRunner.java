package com.novaos.api.devsetup;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DemoResetRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        long count = db.collection("hiringRequests").get().get().size();
        if (count > 0) {
            System.out.println("DemoResetRunner: Detected existing requests. Resetting database to clean initial state...");
            deleteCollection(db, "hiringRequests");
            deleteCollection(db, "workflowRequests");
            deleteCollection(db, "candidates");
            deleteCollection(db, "employees");
            deleteCollection(db, "approvals");
            deleteCollection(db, "documents");
            deleteCollection(db, "notifications");
            deleteCollection(db, "auditLogs");
            db.collection("metrics").document("dashboard").set(Map.of(
                "aiRequests", 0L,
                "documentsGenerated", 0L,
                "emailsSent", 0L
            )).get();
            System.out.println("DemoResetRunner: Database successfully reset.");
        }
    }

    private void deleteCollection(Firestore db, String collectionName) throws Exception {
        List<QueryDocumentSnapshot> docs = db.collection(collectionName).get().get().getDocuments();
        for (QueryDocumentSnapshot doc : docs) {
            doc.getReference().delete().get();
        }
    }
}
