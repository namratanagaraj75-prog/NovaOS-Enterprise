package com.novaos.api.devsetup;

import com.google.firebase.cloud.FirestoreClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Explicit, read-only Firestore inventory for architecture verification. */
@Component
@ConditionalOnProperty(name="novaos.firestore-audit.enabled",havingValue="true")
public class FirestoreArchitectureAuditRunner implements ApplicationRunner {
    @Override public void run(ApplicationArguments args) throws Exception {
        var db=FirestoreClient.getFirestore();
        System.out.println("FirestoreArchitectureAuditRunner: live top-level collections");
        for(var collection:db.listCollections()) {
            int count=collection.get().get().size();
            if(count>0)System.out.println("FirestoreArchitectureAuditRunner: "+collection.getId()+"="+count);
        }
        System.out.println("FirestoreArchitectureAuditRunner: read-only audit complete. Firebase Authentication was not accessed.");
    }
}
