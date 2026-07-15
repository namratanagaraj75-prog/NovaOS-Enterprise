package com.novaos.api.devsetup;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/** One-time, idempotent migration for requests stranded by the former generic APPROVED state. */
@Component
@ConditionalOnProperty(name="novaos.migrate.legacy-hiring-approvals",havingValue="true")
public class LegacyHiringApprovalMigrationRunner implements ApplicationRunner {
    @Override public void run(ApplicationArguments args)throws Exception{
        Firestore db=FirestoreClient.getFirestore();int migrated=0;
        for(QueryDocumentSnapshot request:db.collection("hiringRequests").whereEqualTo("status","APPROVED").get().get().getDocuments()){
            List<String> route=route(request.getString("employmentType"),Optional.ofNullable(request.getLong("annualSalaryAmount")).orElse(0L));
            String next=route.size()>1?route.get(1):null;Timestamp now=Timestamp.now();
            Map<String,Object> actor=new LinkedHashMap<>();actor.put("uid",Objects.toString(request.get("approvedBy"),request.getString("hiringManagerId")));
            actor.put("name",Objects.toString(request.get("approvedByName"),request.getString("hiringManagerName")));
            Map<String,Object> event=new LinkedHashMap<>();event.put("action","MANAGER_APPROVED");event.put("performedBy",actor.get("uid"));event.put("performedByName",actor.get("name"));
            event.put("timestamp",now);event.put("details","Legacy manager approval normalized and routed to "+next+".");
            Map<String,Object> updates=new HashMap<>();updates.put("status",pending(next));updates.put("approvalRoute",route);updates.put("currentApprovalIndex",1);updates.put("currentApproverRole",next);
            updates.put("managerApprovalStatus","APPROVED");updates.put("managerApprovedBy",actor.get("uid"));updates.put("managerApprovedByName",actor.get("name"));
            updates.put("managerApprovedAt",Optional.ofNullable(request.getTimestamp("approvedAt")).orElse(now));updates.put(prefix(next)+"ApprovalStatus","PENDING");updates.put("updatedAt",FieldValue.serverTimestamp());
            updates.put("activityHistory",FieldValue.arrayUnion(event));request.getReference().update(updates).get();migrated++;
            System.out.println("Migrated legacy hiring request "+request.getId()+" -> "+updates.get("status"));
        }
        System.out.println("Legacy hiring approval migration completed. Requests migrated: "+migrated);
    }
    private List<String> route(String employment,long salary){if(employment!=null&&employment.toUpperCase(Locale.ROOT).contains("CONTRACT"))return List.of("HIRING_MANAGER","LEGAL");if(salary>=2_000_000L)return List.of("HIRING_MANAGER","FINANCE","CEO","LEGAL");return List.of("HIRING_MANAGER","FINANCE","LEGAL");}
    private String pending(String role){return "PENDING_"+role+"_APPROVAL";}
    private String prefix(String role){return role.toLowerCase(Locale.ROOT);}
}
