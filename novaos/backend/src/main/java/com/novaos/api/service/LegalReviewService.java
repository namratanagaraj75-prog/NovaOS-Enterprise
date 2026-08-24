package com.novaos.api.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.novaos.api.dto.LegalDtos.LegalChangeRequest;
import com.novaos.api.dto.LegalDtos.LegalReviewRequest;
import com.novaos.api.dto.LegalDtos.PolicyResultRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LegalReviewService {
    private static final String COLLECTION = "legalReviews";
    private static final Set<String> STATUSES = Set.of("COMPLIANT", "NON_COMPLIANT", "REQUIRES_REVIEW", "NOT_APPLICABLE");
    private final LegalPolicyService policyService;

    public LegalReviewService(LegalPolicyService policyService) { this.policyService = policyService; }

    public Map<String, Object> get(String requestId, Authentication auth) {
        requireAny(auth, "LEGAL", "SUPER_ADMIN", "CEO", "HR_ADMIN");
        try {
            DocumentSnapshot request = requireRequest(requestId);
            return riskReview(requestId, request);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw server("Legal review could not be loaded.", e); }
    }

    private Map<String, Object> riskReview(String requestId, DocumentSnapshot candidate) {
        List<Map<String, Object>> results = new ArrayList<>();

        double salary = number(candidate, "annualPackageLPA", "annualSalaryAmount") ;
        if (salary > 1000) salary = salary / 100_000d;
        double minSalary = numberOr(candidate, 6d, "approvedSalaryMinLPA", "salaryRangeMinLPA", "minSalaryLPA");
        double maxSalary = numberOr(candidate, 10d, "approvedSalaryMaxLPA", "salaryRangeMaxLPA", "maxSalaryLPA");
        String salaryRisk; String salaryExplanation;
        if (salary <= 0) { salaryRisk="HIGH"; salaryExplanation="Candidate salary is missing and needs Legal review."; }
        else if (salary >= minSalary && salary <= maxSalary) { salaryRisk="LOW"; salaryExplanation="Candidate salary is within the approved range."; }
        else if (salary <= maxSalary * 1.2 && salary >= minSalary * .8) { salaryRisk="MEDIUM"; salaryExplanation="Candidate salary is slightly outside the approved range and may need additional review."; }
        else { salaryRisk="HIGH"; salaryExplanation="Candidate salary significantly exceeds or falls outside the approved range."; }
        results.add(risk("SALARY_RANGE", "Salary Range Policy", salary > 0 ? formatLpa(salary) : "Not provided",
                formatLpa(minSalary)+" - "+formatLpa(maxSalary), salaryRisk, salaryExplanation));

        int notice = integer(candidate, "noticePeriodDays", "noticePeriod");
        String noticeRisk = notice <= 0 ? "MEDIUM" : notice >= 120 ? "HIGH" : notice > 60 ? "MEDIUM" : notice >= 30 ? "LOW" : "MEDIUM";
        String noticeText = notice <= 0 ? "Notice period is not recorded." : notice >= 120 ? "Notice period is substantially longer than the company preference." : notice > 60 ? "Notice period is longer than the preferred range." : notice >= 30 ? "Notice period is within the preferred range." : "Notice period is outside the preferred range and needs attention.";
        results.add(risk("NOTICE_PERIOD", "Notice Period Policy", notice <= 0 ? "Not provided" : notice+" days", "30 - 60 days", noticeRisk, noticeText));

        int probation = integer(candidate, "probationPeriodMonths", "probationPeriod");
        String probationRisk = probation <= 0 ? "MEDIUM" : probation >= 3 && probation <= 6 ? "LOW" : probation >= 2 && probation <= 8 ? "MEDIUM" : "HIGH";
        String probationText = probation <= 0 ? "Probation period is not recorded." : "LOW".equals(probationRisk) ? "Probation period is within the company range." : "MEDIUM".equals(probationRisk) ? "Probation period is slightly outside the company range." : "Probation period is far outside the company range.";
        results.add(risk("PROBATION_PERIOD", "Probation Period Policy", probation <= 0 ? "Not provided" : probation+" months", "3 - 6 months", probationRisk, probationText));

        String employment = text(candidate, "employmentType");
        String approvedEmployment = text(candidate, "approvedEmploymentType");
        if (!StringUtils.hasText(approvedEmployment)) approvedEmployment = employment;
        boolean employmentMatches = StringUtils.hasText(employment) && normalize(employment).equals(normalize(approvedEmployment));
        results.add(risk("EMPLOYMENT_TYPE", "Employment Type Policy", display(employment), display(approvedEmployment),
                employmentMatches ? "LOW" : "HIGH", employmentMatches ? "Employment type matches the approved job opening." : "Employment type does not match the approved job requirement."));

        List<String> missing = new ArrayList<>();
        for (String field : List.of("candidateName","jobTitle","department","joiningDate","employmentType")) if (!StringUtils.hasText(text(candidate, field))) missing.add(label(field));
        boolean salaryMissing = salary <= 0; boolean joiningMissing = !StringUtils.hasText(text(candidate,"joiningDate"));
        String completenessRisk = salaryMissing || joiningMissing ? "HIGH" : missing.isEmpty() ? "LOW" : "MEDIUM";
        String completenessText = missing.isEmpty() && !salaryMissing ? "All important candidate and offer information is present." : "Missing information: " + String.join(", ", missingWithSalary(missing, salaryMissing)) + ".";
        results.add(risk("OFFER_COMPLETENESS", "Offer Information Completeness", completenessText.startsWith("All") ? "Complete" : "Incomplete", "Required offer fields", completenessRisk, completenessText));

        String bgv = normalize(text(candidate, "backgroundVerificationStatus"));
        if (!StringUtils.hasText(bgv)) bgv = "PENDING";
        String bgvRisk = "FAILED".equals(bgv) ? "HIGH" : "PENDING".equals(bgv) ? "MEDIUM" : "LOW";
        String bgvText = switch (bgv) { case "FAILED" -> "Background verification requires important review."; case "PENDING" -> "Background verification is still pending."; case "NOT_REQUIRED" -> "Background verification is not required for this opening."; default -> "Background verification is complete."; };
        results.add(risk("BACKGROUND_VERIFICATION", "Background Verification", display(bgv), "VERIFIED / PENDING / FAILED / NOT REQUIRED", bgvRisk, bgvText));

        String documents = normalize(text(candidate, "documentStatus"));
        if (!StringUtils.hasText(documents)) documents = "PENDING";
        String documentsRisk = Set.of("MISSING","IMPORTANT_MISSING","FAILED").contains(documents) ? "HIGH" : Set.of("COMPLETE","COMPLETED","VERIFIED","NOT_REQUIRED").contains(documents) ? "LOW" : "MEDIUM";
        String documentsText = "LOW".equals(documentsRisk) ? "Required document status is complete." : "HIGH".equals(documentsRisk) ? "Important required documents are missing." : "Some required documents are still pending.";
        results.add(risk("DOCUMENT_STATUS", "Document Status", display(documents), "Required documents completed", documentsRisk, documentsText));

        Map<String, Long> summary = new LinkedHashMap<>();
        for (String risk : List.of("LOW","MEDIUM","HIGH")) summary.put(risk, results.stream().filter(row -> risk.equals(row.get("riskLevel"))).count());
        summary.put("TOTAL", (long) results.size());
        String overall = summary.get("HIGH") > 0 ? "HIGH" : summary.get("MEDIUM") > 0 ? "MEDIUM" : "LOW";
        Map<String,Object> review = new LinkedHashMap<>();
        review.put("id",requestId); review.put("candidateId",requestId); review.put("overallRisk",overall);
        review.put("summary",summary); review.put("policyResults",results); review.put("candidateSummary",candidateSummary(candidate));
        review.put("advisoryOnly",true);
        return review;
    }

    private Map<String,Object> risk(String id,String name,String current,String policy,String level,String explanation) {
        Map<String,Object> row=new LinkedHashMap<>(); row.put("policyId",id); row.put("title",name); row.put("currentValue",current);
        row.put("policyValue",policy); row.put("riskLevel",level); row.put("explanation",explanation); return row;
    }
    private double number(DocumentSnapshot d,String...fields){for(String field:fields){Object value=d.get(field);if(value instanceof Number n&&n.doubleValue()!=0)return n.doubleValue();}return 0;}
    private double numberOr(DocumentSnapshot d,double fallback,String...fields){double value=number(d,fields);return value==0?fallback:value;}
    private int integer(DocumentSnapshot d,String...fields){for(String field:fields){Object value=d.get(field);if(value instanceof Number n)return n.intValue();if(value!=null){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\d+").matcher(String.valueOf(value));if(m.find())return Integer.parseInt(m.group());}}return 0;}
    private String text(DocumentSnapshot d,String field){return Objects.toString(d.get(field),"").trim();}
    private String display(String value){return StringUtils.hasText(value)?value.replace('_',' '):"Not provided";}
    private String formatLpa(double value){return "₹"+(value==Math.rint(value)?String.valueOf((long)value):String.format(Locale.ROOT,"%.1f",value))+" LPA";}
    private String label(String value){return value.replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' ');}
    private List<String> missingWithSalary(List<String> fields,boolean salaryMissing){List<String> value=new ArrayList<>(fields);if(salaryMissing)value.add("salary");return value;}

    public Map<String, Object> save(String requestId, LegalReviewRequest request, Authentication auth) {
        requireRole(auth, "LEGAL");
        if (request == null || request.policyResults() == null || request.policyResults().isEmpty()) throw bad("Policy results are required.");
        policyService.ensureDefaults();
        try {
            Firestore db = db(); DocumentSnapshot candidate = requireRequest(requestId);
            if (!"PENDING_LEGAL_APPROVAL".equals(candidate.getString("status")) || !"LEGAL".equals(candidate.getString("currentApproverRole")))
                throw conflict("This candidate is not awaiting Legal review.");
            Map<String, Map<String, Object>> policies = activePolicies();
            Map<String, PolicyResultRequest> supplied = request.policyResults().stream().collect(Collectors.toMap(
                    r -> normalize(r.policyId()), Function.identity(), (a,b) -> b));
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : policies.entrySet()) {
                PolicyResultRequest input = supplied.get(entry.getKey());
                if (input == null) throw bad("A result is required for active policy " + entry.getKey() + ".");
                String status = normalize(input.status()); if (!STATUSES.contains(status)) throw bad("Invalid status for policy " + entry.getKey() + ".");
                if (Set.of("NON_COMPLIANT", "REQUIRES_REVIEW").contains(status) && !StringUtils.hasText(input.reviewerComment()))
                    throw bad("A reviewer comment is required for " + entry.getKey() + " when it is not compliant.");
                Map<String, Object> snapshot = new LinkedHashMap<>(entry.getValue());
                snapshot.remove("createdAt"); snapshot.remove("updatedAt");
                snapshot.put("status", status); snapshot.put("reviewerComment", Objects.toString(input.reviewerComment(), "").trim());
                results.add(snapshot);
            }
            Map<String, Long> counts = counts(results); String overall = overall(results);
            Timestamp now = Timestamp.now(); Map<String, Object> actor = actor(db, auth);
            Map<String, Object> review = new LinkedHashMap<>();
            String overallRisk="NON_COMPLIANT".equals(overall)?"HIGH":"REQUIRES_REVIEW".equals(overall)?"MEDIUM":"LOW";
            review.put("candidateId", requestId);review.put("requestId",requestId); review.put("reviewerId", actor.get("uid")); review.put("reviewerName", actor.get("name"));
            review.put("reviewedAt", now); review.put("overallStatus", overall);review.put("overallRisk",overallRisk); review.put("policyResults", results); review.put("summary", counts);
            DocumentReference ref = db.collection(COLLECTION).document(requestId);
            WriteBatch batch = db.batch(); batch.set(ref, review, SetOptions.merge());
            Map<String, Object> history = new LinkedHashMap<>(review); history.put("versionId", UUID.randomUUID().toString());
            batch.create(ref.collection("history").document(), history);
            batch.update(db.collection("hiringRequests").document(requestId), Map.of(
                    "legalReviewStatus", overall, "legalReviewUpdatedAt", now,
                    "activityHistory", FieldValue.arrayUnion(activity(actor, "LEGAL_REVIEW_SAVED", "Legal policy checklist saved.", now))));
            batch.set(db.collection("candidates").document(requestId),Map.of("legalRisk",overallRisk,"updatedAt",now),SetOptions.merge());
            batch.create(db.collection("workflowEvents").document(),event(requestId,"LEGAL_RISK_CALCULATED",actor,"Legal policy risk calculated as "+overallRisk+".",now));
            batch.commit().get();
            return get(requestId, auth);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw server("Legal review could not be saved.", e); }
    }

    public void assertApprovable(String requestId) {
        try {
            DocumentSnapshot review = db().collection(COLLECTION).document(requestId).get().get();
            if (!review.exists()) throw unprocessable("Approval blocked. Complete the legal policy checklist before approving this candidate.");
            List<Map<String, Object>> results = maps(review.get("policyResults"));
            List<String> blocked = results.stream()
                    .filter(r -> Boolean.TRUE.equals(r.get("mandatory")))
                    .filter(r -> !Set.of("COMPLIANT", "NOT_APPLICABLE").contains(Objects.toString(r.get("status"), "")))
                    .map(r -> Objects.toString(r.get("policyId"), "Policy") + " (" + Objects.toString(r.get("status"), "not reviewed") + ")")
                    .toList();
            if (!blocked.isEmpty()) throw unprocessable("Approval blocked. Resolve all mandatory legal policy violations before approving this candidate: " + String.join(", ", blocked));
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw server("Legal approval validation failed.", e); }
    }

    public Map<String, Object> requestChanges(String requestId, LegalChangeRequest request, Authentication auth) {
        requireRole(auth, "LEGAL");
        if (request == null || !StringUtils.hasText(request.reason()) || !StringUtils.hasText(request.policyId())) throw bad("Reason and relevant policy are required.");
        String target = normalize(request.targetDepartment());
        if (!Set.of("HR", "HR_ADMIN", "FINANCE").contains(target)) throw bad("Target department must be HR or FINANCE.");
        target = target.startsWith("HR") ? "HR_ADMIN" : "FINANCE";
        try {
            assertPolicyInReview(requestId, request.policyId());
            Firestore db = db(); DocumentSnapshot candidate = requireRequest(requestId); Map<String, Object> actor = actor(db, auth);
            if (!"PENDING_LEGAL_APPROVAL".equals(candidate.getString("status"))) throw conflict("This candidate is not awaiting Legal review.");
            Timestamp now = Timestamp.now(); Map<String, Object> updates = new HashMap<>();
            updates.put("status", "CHANGES_REQUESTED"); updates.put("currentApproverRole", FieldValue.delete());
            updates.put("changesRequestedFrom", target); updates.put("changesRequestedPolicyId", normalize(request.policyId()));
            updates.put("changesRequestedReason", request.reason().trim()); updates.put("changesRecommendation", Objects.toString(request.recommendation(), "").trim());
            updates.put("legalApprovalStatus", "CHANGES_REQUESTED"); updates.put("updatedAt", now);
            updates.put("activityHistory", FieldValue.arrayUnion(activity(actor, "LEGAL_CHANGES_REQUESTED", "Changes requested from " + target.replace("_ADMIN", "") + " for policy " + normalize(request.policyId()) + ".", now)));
            db.collection("hiringRequests").document(requestId).update(updates).get();
            writeAudit(db, requestId, actor, request, target, now);
            Map<String, Object> response = new HashMap<>(db.collection("hiringRequests").document(requestId).get().get().getData()); response.put("id", requestId); return response;
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw server("Legal change request failed.", e); }
    }

    private Map<String, Object> emptyReview(String id) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> policy : activePolicies().values()) {
            Map<String, Object> row = new LinkedHashMap<>(policy); row.remove("createdAt"); row.remove("updatedAt");
            row.put("status", "REQUIRES_REVIEW"); row.put("reviewerComment", ""); results.add(row);
        }
        return new LinkedHashMap<>(Map.of("candidateId", id, "overallStatus", "REQUIRES_REVIEW", "policyResults", results, "summary", counts(results)));
    }

    private Map<String, Map<String, Object>> activePolicies() throws Exception {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        for (QueryDocumentSnapshot d : db().collection(LegalPolicyService.COLLECTION).whereEqualTo("active", true).get().get().getDocuments()) {
            Map<String, Object> value = new LinkedHashMap<>(d.getData()); value.put("id", d.getId()); result.put(normalize(d.getString("policyId")), value);
        }
        return result;
    }

    private Map<String, Object> candidateSummary(DocumentSnapshot d) {
        Map<String,Object> value=new LinkedHashMap<>();
        for(String field:List.of("candidateName","candidateEmail","jobTitle","department","employmentType","location","annualPackageLPA","annualSalaryAmount","joiningDate","probationPeriod","noticePeriod","contractType","reportingManagerName","hiringManagerName","workMode","documentStatus","backgroundVerificationStatus","workAuthorizationStatus")) value.put(field,d.get(field));
        return value;
    }
    private Map<String, Long> counts(List<Map<String, Object>> results) { Map<String,Long> value=new LinkedHashMap<>(); for(String s:STATUSES)value.put(s,results.stream().filter(r->s.equals(r.get("status"))).count()); value.put("TOTAL",(long)results.size()); return value; }
    private String overall(List<Map<String,Object>> r){if(r.stream().anyMatch(v->"NON_COMPLIANT".equals(v.get("status"))))return "NON_COMPLIANT";if(r.stream().anyMatch(v->"REQUIRES_REVIEW".equals(v.get("status"))))return "REQUIRES_REVIEW";return "COMPLIANT";}
    private void assertPolicyInReview(String id,String policy)throws Exception{DocumentSnapshot d=db().collection(COLLECTION).document(id).get().get();if(!d.exists()||maps(d.get("policyResults")).stream().noneMatch(r->normalize(Objects.toString(r.get("policyId"),"")).equals(normalize(policy))))throw bad("Relevant policy is not part of the saved Legal review.");}
    private DocumentSnapshot requireRequest(String id)throws Exception{DocumentSnapshot d=db().collection("hiringRequests").document(id).get().get();if(!d.exists())throw notFound("Hiring request not found: "+id);return d;}
    private Map<String,Object> actor(Firestore db,Authentication auth)throws Exception{DocumentSnapshot u=db.collection("users").document(auth.getName()).get().get();return Map.of("uid",auth.getName(),"name",Optional.ofNullable(u.getString("displayName")).orElse("Legal Reviewer"),"role","LEGAL");}
    private Map<String,Object> activity(Map<String,Object>a,String action,String details,Timestamp at){return Map.of("action",action,"performedBy",a.get("uid"),"performedByName",a.get("name"),"details",details,"timestamp",at);}
    private Map<String,Object> event(String id,String type,Map<String,Object>a,String details,Timestamp at){return Map.of("requestId",id,"candidateId",id,"eventType",type,"action",type,"department","LEGAL","performedByUserId",a.get("uid"),"performedBy",a.get("uid"),"performedByName",a.get("name"),"details",details,"timestamp",at);}
    private void writeAudit(Firestore db,String id,Map<String,Object>a,LegalChangeRequest r,String target,Timestamp at)throws Exception{Map<String,Object>e=new HashMap<>(event(id,"LEGAL_CHANGES_REQUESTED",a,r.reason(),at));e.put("metadata",Map.of("policyId",normalize(r.policyId()),"targetDepartment",target,"reason",r.reason()));db.collection("workflowEvents").document().create(e).get();}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> maps(Object v){if(!(v instanceof List<?> l))return List.of();return l.stream().filter(Map.class::isInstance).map(x->(Map<String,Object>)x).toList();}
    private String normalize(String v){return Objects.toString(v,"").trim().toUpperCase(Locale.ROOT).replaceAll("[ -]+","_");}
    private Firestore db(){return FirestoreClient.getFirestore();}
    private void requireRole(Authentication a,String role){if(a==null||a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("ROLE_"+role)))throw forbidden("Only Legal reviewers may perform this action.");}
    private void requireAny(Authentication a,String...roles){if(a==null||Arrays.stream(roles).noneMatch(r->a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_"+r))))throw forbidden("You are not authorized to view Legal reviews.");}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}private ResponseStatusException forbidden(String m){return new ResponseStatusException(HttpStatus.FORBIDDEN,m);}private ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}private ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);}private ResponseStatusException unprocessable(String m){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,m);}private ResponseStatusException server(String m,Throwable e){return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,m,e);}
}
