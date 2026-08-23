package com.novaos.api.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.novaos.api.dto.LegalDtos.LegalPolicyRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class LegalPolicyService {
    public static final String COLLECTION = "legalPolicies";
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public List<Map<String, Object>> list(boolean includeInactive, Authentication auth) {
        requireAny(auth, "LEGAL", "SUPER_ADMIN", "CEO", "HR_ADMIN");
        ensureDefaults();
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            for (QueryDocumentSnapshot d : db().collection(COLLECTION).get().get().getDocuments()) {
                if (!includeInactive && !Boolean.TRUE.equals(d.getBoolean("active"))) continue;
                Map<String, Object> value = new HashMap<>(d.getData()); value.put("id", d.getId()); result.add(value);
            }
            result.sort(Comparator.comparing(v -> Objects.toString(v.get("policyId"), "")));
            return result;
        } catch (Exception e) { throw server("Legal policies could not be loaded.", e); }
    }

    public Map<String, Object> create(LegalPolicyRequest request, Authentication auth) {
        requireRole(auth, "SUPER_ADMIN"); validate(request);
        try {
            DocumentReference ref = db().collection(COLLECTION).document(request.policyId().trim().toUpperCase(Locale.ROOT));
            if (ref.get().get().exists()) throw conflict("Policy ID already exists.");
            Map<String, Object> value = value(request, "1.0", Timestamp.now());
            ref.create(value).get(); value.put("id", ref.getId()); return value;
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw server("Legal policy could not be created.", e); }
    }

    public Map<String, Object> update(String id, LegalPolicyRequest request, Authentication auth) {
        requireRole(auth, "SUPER_ADMIN"); validate(request);
        try {
            DocumentReference ref = db().collection(COLLECTION).document(id);
            DocumentSnapshot old = ref.get().get(); if (!old.exists()) throw notFound("Legal policy not found.");
            if (!normalize(request.policyId()).equals(normalize(old.getString("policyId"))))
                throw conflict("Policy IDs are immutable; create a new policy instead.");
            String nextVersion = increment(Objects.toString(old.get("version"), "1.0"));
            Map<String, Object> value = value(request, nextVersion, old.getTimestamp("createdAt"));
            value.put("updatedAt", Timestamp.now()); ref.set(value).get(); value.put("id", id); return value;
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw server("Legal policy could not be updated.", e); }
    }

    public void ensureDefaults() {
        try {
            Firestore db = db();
            if (!db.collection(COLLECTION).limit(1).get().get().isEmpty()) return;
            WriteBatch batch = db.batch(); Timestamp now = Timestamp.now();
            for (String[] p : defaults()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("policyId", p[0]); value.put("title", p[1]); value.put("description", p[2]);
                value.put("category", p[3]); value.put("severity", p[4]); value.put("mandatory", Boolean.parseBoolean(p[5]));
                value.put("active", true); value.put("policyType", "COMPANY_POLICY"); value.put("version", "1.0");
                value.put("createdAt", now); value.put("updatedAt", now);
                batch.create(db.collection(COLLECTION).document(p[0]), value);
            }
            batch.commit().get();
        } catch (Exception e) { throw server("Default legal policies could not be initialized.", e); }
    }

    private List<String[]> defaults() {
        return List.of(
                new String[]{"COMP-001","Salary Range Compliance","Proposed compensation must remain within the approved range for the role and grade.","COMPENSATION","HIGH","true"},
                new String[]{"COMP-002","Variable Compensation Documentation","Bonus, commission, and other variable compensation must be documented.","COMPENSATION","MEDIUM","true"},
                new String[]{"EMP-001","Employment Type Validation","Employment type must match the approved role classification.","EMPLOYMENT_CONTRACT","HIGH","true"},
                new String[]{"EMP-002","Probation Period Compliance","Probation terms must fall within company-approved limits.","EMPLOYMENT_CONTRACT","MEDIUM","true"},
                new String[]{"EMP-003","Notice Period Compliance","Notice period must comply with company employment guidelines.","EMPLOYMENT_CONTRACT","MEDIUM","true"},
                new String[]{"DOC-001","Required Candidate Documentation","Only necessary candidate documentation must be complete and recorded.","DOCUMENTATION","HIGH","true"},
                new String[]{"OFFER-001","Offer Letter Mandatory Fields","The offer must contain all company-required identity, role, compensation, and terms fields.","OFFER_LETTER","CRITICAL","true"},
                new String[]{"CONF-001","Confidentiality and Data Protection","Required confidentiality and employee data notice clauses must be present.","DATA_PROTECTION","HIGH","true"},
                new String[]{"BGV-001","Background Verification","Background verification status must be reviewed; pending does not itself imply rejection.","BACKGROUND_VERIFICATION","HIGH","false"},
                new String[]{"COI-001","Conflict of Interest Declaration","Potential conflicts of interest must be declared or marked not applicable.","CONFLICT_OF_INTEREST","MEDIUM","true"},
                new String[]{"WORK-001","Work Authorization Review","Applicable work authorization information must be reviewed without inferring protected traits.","WORK_AUTHORIZATION","HIGH","false"}
        );
    }

    private Map<String, Object> value(LegalPolicyRequest r, String version, Timestamp created) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("policyId", r.policyId().trim().toUpperCase(Locale.ROOT)); value.put("title", r.title().trim());
        value.put("description", r.description().trim()); value.put("category", normalize(r.category()));
        value.put("severity", normalize(r.severity())); value.put("active", r.active()); value.put("mandatory", r.mandatory());
        value.put("policyType", StringUtils.hasText(r.policyType()) ? normalize(r.policyType()) : "COMPANY_POLICY");
        value.put("version", version); value.put("createdAt", created == null ? Timestamp.now() : created); value.put("updatedAt", Timestamp.now());
        return value;
    }
    private void validate(LegalPolicyRequest r) { if (r == null) throw bad("Policy is required."); if (!SEVERITIES.contains(normalize(r.severity()))) throw bad("Severity must be LOW, MEDIUM, HIGH, or CRITICAL."); }
    static String increment(String value) { try { String[] v=value.split("\\."); return v[0]+"."+(Integer.parseInt(v.length>1?v[1]:"0")+1); } catch(Exception e){ return "1.1"; } }
    private String normalize(String v) { return Objects.toString(v, "").trim().toUpperCase(Locale.ROOT).replaceAll("[ -]+", "_"); }
    private Firestore db() { return FirestoreClient.getFirestore(); }
    private void requireRole(Authentication a,String role){if(a==null||a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("ROLE_"+role)))throw forbidden("You are not authorized to manage legal policies.");}
    private void requireAny(Authentication a,String...roles){if(a==null||Arrays.stream(roles).noneMatch(r->a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_"+r))))throw forbidden("You are not authorized to view legal policies.");}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private ResponseStatusException forbidden(String m){return new ResponseStatusException(HttpStatus.FORBIDDEN,m);} private ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);} private ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);} private ResponseStatusException server(String m,Throwable e){return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,m,e);}
}
