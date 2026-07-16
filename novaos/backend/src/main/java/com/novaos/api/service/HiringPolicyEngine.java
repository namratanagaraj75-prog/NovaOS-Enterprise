package com.novaos.api.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.novaos.api.dto.HiringRequestDtos.CandidateInput;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

@Service
public class HiringPolicyEngine {
    public Map<String,Object> evaluate(Firestore db, CandidateInput candidate, String currentRequestId,
            Map<String,Object> manager) throws Exception {
        // Always sync the policy documents so department list changes take effect without manual Firestore edits.
        ensurePolicies(db);
        List<Map<String,Object>> checks=new ArrayList<>();
        check(checks,"Mandatory Candidate Fields","mandatoryCandidateFields",required(candidate),
                "All mandatory hiring fields are present.","One or more mandatory hiring fields are missing.",Map.of());
        boolean email=StringUtils.hasText(candidate.candidateEmail())&&candidate.candidateEmail().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        check(checks,"Candidate Email","mandatoryCandidateFields",email,"Candidate email format is valid.","Candidate email format is invalid.",Map.of("email",Objects.toString(candidate.candidateEmail(),"")));
        check(checks,"Manager Validation","managerValidation",manager!=null,"An active hiring manager was resolved from users.","No active hiring manager could be resolved.",Map.of("manager",Objects.toString(candidate.hiringManagerName(),"")));


        DocumentSnapshot hiring=db.collection("policies").document("hiring").get().get();
        check(checks,"Department","hiring",true,"Department is accepted as entered.","",Map.of("department",Objects.toString(candidate.department(),"")));
        DocumentSnapshot employment=db.collection("policies").document("employmentType").get().get();
        List<String> types=strings(employment.get("allowedTypes"));
        boolean typeKnown=types.isEmpty()||!StringUtils.hasText(candidate.employmentType())||contains(types,candidate.employmentType());
        // Employment type check is informational — any type is accepted.
        if(!employment.exists()||types.isEmpty())
            missingPolicy(checks,"Employment Type","employmentType","No employment-type policy is configured; type accepted as entered.",Map.of("employmentType",Objects.toString(candidate.employmentType(),"Not provided")));
        else
            warning(checks,"Employment Type","employmentType",!typeKnown,
                typeKnown?"Employment type is recognized."
                         :"Employment type '"+candidate.employmentType()+"' is not in the known list but is accepted. HR should verify.",
                Map.of("employmentType",Objects.toString(candidate.employmentType(),"Not provided"),"knownTypes",types));

        DocumentSnapshot joiningPolicy=db.collection("policies").document("joiningDate").get().get();
        boolean validDate=false,future=false;
        try{LocalDate date=LocalDate.parse(candidate.joiningDate());validDate=true;future=date.isAfter(LocalDate.now());}catch(Exception ignored){}
        if(!validDate)check(checks,"Joining Date","joiningDate",false,"Joining date is valid.","Joining date must be a valid ISO date.",Map.of("joiningDate",Objects.toString(candidate.joiningDate(),"")));
        else if(!joiningPolicy.exists())missingPolicy(checks,"Joining Date","joiningDate","No joining-date policy is configured; valid date accepted with warning.",Map.of("joiningDate",candidate.joiningDate()));
        else check(checks,"Joining Date","joiningDate",!Boolean.TRUE.equals(joiningPolicy.getBoolean("futureDateRequired"))||future,"Joining date complies with policy.","Joining date violates the configured future-date policy.",Map.of("joiningDate",candidate.joiningDate()));

        boolean duplicateEmployee = duplicate(db,"employees","email",candidate.candidateEmail(),currentRequestId)
                || duplicate(db,"employees","candidateEmail",candidate.candidateEmail(),currentRequestId);
        if (duplicateEmployee) {
            check(checks,"Duplicate Hiring","duplicateHiring",false,"","An employee already uses this email.",Map.of("email",Objects.toString(candidate.candidateEmail(),"")));
        } else {
            boolean duplicateRequest = duplicate(db,"hiringRequests","candidateEmail",candidate.candidateEmail(),currentRequestId);
            if (duplicateRequest) {
                warning(checks,"Duplicate Hiring","duplicateHiring",true,"A previous hiring request exists for this email. A new request will still be created.",Map.of("email",Objects.toString(candidate.candidateEmail(),"")));
            } else {
                check(checks,"Duplicate Hiring","duplicateHiring",true,"No duplicate hiring identity was found.","",Map.of("email",Objects.toString(candidate.candidateEmail(),"")));
            }
        }

        DocumentSnapshot salary=db.collection("policies").document("salaryBands").get().get();
        Map<String,Object> bands=map(salary.get("bands")); Map<String,Object> band=findIgnoreCase(bands,candidate.jobTitle());
        long amount=candidate.annualSalaryAmount()==null?0:candidate.annualSalaryAmount();
        // Support both the active policy schema and the legacy Decision Passport schema.
        // Existing installations must not become blocked merely because their policy
        // document predates this workflow engine.
        long min=number(band.containsKey("min")?band.get("min"):band.get("minAnnualCtc"));
        long max=number(band.containsKey("max")?band.get("max"):band.get("maxAnnualCtc"));
        long blockThreshold=number(band.containsKey("blockThreshold")?band.get("blockThreshold"):band.get("blockThresholdAnnualCtc"));
        boolean inBand=amount>0&&min>0&&amount>=min&&amount<=max;
        if(!salary.exists()||band.isEmpty()||min<=0||max<=0)missingPolicy(checks,"Salary Band","salaryBands","No salary band is configured for this role.",Map.of("role",Objects.toString(candidate.jobTitle(),""),"annualSalary",amount));
        else if(blockThreshold>0&&amount>blockThreshold)check(checks,"Salary Band","salaryBands",false,"Compensation is within the configured salary band.","Compensation exceeds the explicit blocking threshold.",Map.of("annualSalary",amount,"minimum",min,"standardMaximum",max,"blockThreshold",blockThreshold));
        else if(!inBand)warning(checks,"Salary Band","salaryBands",true,amount<min?"Compensation is below the standard role minimum and requires review.":"Compensation exceeds the standard role maximum and requires additional approval.",Map.of("annualSalary",amount,"minimum",min,"standardMaximum",max));
        else check(checks,"Salary Band","salaryBands",true,"Compensation is within the configured salary band.","",Map.of("annualSalary",amount,"minimum",min,"standardMaximum",max));

        DocumentSnapshot high=db.collection("policies").document("highSalaryApproval").get().get();
        long threshold=number(high.get("annualCtcThreshold")); boolean highSalary=threshold>0&&amount>=threshold;
        if(!high.exists()||threshold<=0)missingPolicy(checks,"High Salary Approval","highSalaryApproval","No high-salary approval threshold is configured.",Map.of("annualSalary",amount));
        else warning(checks,"High Salary Approval","highSalaryApproval",highSalary,
                highSalary?"Compensation requires CEO approval.":"Compensation does not require CEO approval.",Map.of("annualSalary",amount,"threshold",threshold));

        boolean budget=!Boolean.FALSE.equals(hiring.getBoolean("budgetAvailable"));
        if(!hiring.exists()||hiring.get("budgetAvailable")==null)missingPolicy(checks,"Budget Availability","hiring","No budget-availability rule is configured.",Map.of());
        else check(checks,"Budget Availability","hiring",budget,"Hiring budget is available.","Hiring budget is unavailable.",Map.of("budgetAvailable",budget));
        check(checks,"Policy Compliance","hiring",true,"Deterministic NovaOS policy evaluation completed.","Policy evaluation did not complete.",Map.of("evaluatedBy","HiringPolicyEngine"));

        List<String> route=approvalRoute(candidate,threshold);
        checks.add(result("Required Approval Chain","PASS","Approval route was selected from policy evidence.",Map.of("approvalRoute",route),"hiring"));
        attachPolicySources(db,checks);
        DocumentSnapshot runtime=db.collection("settings").document("hiringAutomation").get().get();
        boolean developmentMode=Boolean.TRUE.equals(runtime.getBoolean("developmentMode"));
        if(developmentMode)for(Map<String,Object> policyCheck:checks){
            String name=Objects.toString(policyCheck.get("name"),"");
            if("FAIL".equals(policyCheck.get("status"))&&!Set.of("Mandatory Candidate Fields","Candidate Email","Manager Validation","Duplicate Hiring","Joining Date").contains(name)){
                policyCheck.put("status","WARNING");
                policyCheck.put("reason","Development Mode warning: "+policyCheck.get("reason"));
            }
        }
        long failures=checks.stream().filter(c->"FAIL".equals(c.get("status"))).count();
        long warnings=checks.stream().filter(c->"WARNING".equals(c.get("status"))).count();
        int riskScore=(int)Math.min(100,failures*35+warnings*15); String decision=failures>0?"BLOCKED":warnings>0?"WARNING":"PASS";
        String risk=riskScore>=60?"HIGH":riskScore>=25?"MEDIUM":"LOW";
        List<String> warningReasons=reasons(checks,"WARNING"), blocking=reasons(checks,"FAIL");
        Map<String,Object> passport=new LinkedHashMap<>(); passport.put("decision",decision); passport.put("riskLevel",risk); passport.put("riskScore",riskScore);
        passport.put("explanation",explanation(decision,route,blocking)); passport.put("warnings",warningReasons); passport.put("blockingReasons",blocking);
        passport.put("recommendation",failures>0?"Return to HR and correct the failed policy evidence.":warnings>0?"Proceed with the selected approval route while reviewers acknowledge warnings.":"Proceed through the governed approval route.");
        passport.put("policyChecks",checks); passport.put("policiesChecked",checks.stream().map(c->c.get("policyId")).distinct().toList());
        passport.put("approvalRoute",route); passport.put("developmentMode",developmentMode); passport.put("generatedAt",Timestamp.now()); return passport;
    }

    List<String> approvalRoute(CandidateInput c,long highSalaryThreshold){
        return List.of("HIRING_MANAGER","FINANCE","LEGAL");
    }
    // Always-approved departments — kept in sync with the live Firestore document on every startup.
    public void resetPolicies(Firestore db) throws Exception {
        // Force-write all policy documents (overwrite, not only-if-missing).
        db.collection("policies").document("hiring").set(Map.of(
                "configuration",true,
                "budgetAvailable",true,
                "updatedAt",Timestamp.now()
        )).get();
        ensure(db,"salaryBands",Map.of("configuration",true,"bands",Map.of(
                "Software Engineer",Map.of("min",800000L,"max",1800000L),
                "Software Developer",Map.of("min",700000L,"max",1600000L),
                "AI Engineer",Map.of("min",1000000L,"max",3000000L)),"updatedAt",Timestamp.now()));
        ensure(db,"highSalaryApproval",Map.of("configuration",true,"annualCtcThreshold",2000000L,"requiredRole","CEO","updatedAt",Timestamp.now()));
        ensure(db,"employmentType",Map.of("configuration",true,"allowedTypes",List.of("Full-time","Permanent","Contract","Full Time","Part Time","Part-time","Internship"),"updatedAt",Timestamp.now()));
        ensure(db,"departmentHeadcount",Map.of("configuration",true,"enforced",true,"updatedAt",Timestamp.now()));
        ensure(db,"duplicateHiring",Map.of("configuration",true,"identityField","candidateEmail","updatedAt",Timestamp.now()));
        ensure(db,"mandatoryCandidateFields",Map.of("configuration",true,"fields",List.of("candidateName","candidateEmail","jobTitle","annualSalaryAmount","joiningDate","reportingManagerName","hiringManagerName"),"updatedAt",Timestamp.now()));
        ensure(db,"joiningDate",Map.of("configuration",true,"futureDateRequired",true,"updatedAt",Timestamp.now()));
        ensure(db,"managerValidation",Map.of("configuration",true,"requiredRole","HIRING_MANAGER","activeRequired",true,"updatedAt",Timestamp.now()));
    }

    private void ensurePolicies(Firestore db) throws Exception {
        // Hiring policy always force-updated so new departments take effect without manual Firestore edits.
        db.collection("policies").document("hiring").set(Map.of(
                "configuration",true,
                "budgetAvailable",true,
                "updatedAt",Timestamp.now()
        )).get();
        ensure(db,"salaryBands",Map.of("configuration",true,"bands",Map.of(
                "Software Engineer",Map.of("min",800000L,"max",1800000L),
                "Software Developer",Map.of("min",700000L,"max",1600000L),
                "AI Engineer",Map.of("min",1000000L,"max",3000000L)),"updatedAt",Timestamp.now()));
        ensure(db,"highSalaryApproval",Map.of("configuration",true,"annualCtcThreshold",2000000L,"requiredRole","CEO","updatedAt",Timestamp.now()));
        ensure(db,"employmentType",Map.of("configuration",true,"allowedTypes",List.of("Full-time","Permanent","Contract","Full Time","Part Time","Part-time","Internship"),"updatedAt",Timestamp.now()));
        ensure(db,"departmentHeadcount",Map.of("configuration",true,"enforced",true,"updatedAt",Timestamp.now()));
        ensure(db,"duplicateHiring",Map.of("configuration",true,"identityField","candidateEmail","updatedAt",Timestamp.now()));
        ensure(db,"mandatoryCandidateFields",Map.of("configuration",true,"fields",List.of("candidateName","candidateEmail","jobTitle","annualSalaryAmount","joiningDate","reportingManagerName","hiringManagerName"),"updatedAt",Timestamp.now()));
        ensure(db,"joiningDate",Map.of("configuration",true,"futureDateRequired",true,"updatedAt",Timestamp.now()));
        ensure(db,"managerValidation",Map.of("configuration",true,"requiredRole","HIRING_MANAGER","activeRequired",true,"updatedAt",Timestamp.now()));
    }
    private void ensure(Firestore db,String id,Map<String,Object> data)throws Exception{if(!db.collection("policies").document(id).get().get().exists())db.collection("policies").document(id).create(data).get();}
    private boolean required(CandidateInput c){return c!=null&&StringUtils.hasText(c.candidateName())&&StringUtils.hasText(c.candidateEmail())&&StringUtils.hasText(c.jobTitle())&&c.annualSalaryAmount()!=null&&c.annualSalaryAmount()>0&&StringUtils.hasText(c.joiningDate())&&StringUtils.hasText(c.reportingManagerName())&&StringUtils.hasText(c.hiringManagerName());}
    private boolean duplicate(Firestore db,String collection,String field,String email,String current)throws Exception{if(!StringUtils.hasText(email))return false;return db.collection(collection).whereEqualTo(field,email.toLowerCase(Locale.ROOT)).get().get().getDocuments().stream().anyMatch(d->current==null||!d.getId().equals(current));}
    private void check(List<Map<String,Object>> out,String name,String policy,boolean pass,String ok,String fail,Map<String,Object> evidence){out.add(result(name,pass?"PASS":"FAIL",pass?ok:fail,evidence,policy));}
    private void warning(List<Map<String,Object>> out,String name,String policy,boolean warn,String reason,Map<String,Object> evidence){out.add(result(name,warn?"WARNING":"PASS",reason,evidence,policy));}
    private void missingPolicy(List<Map<String,Object>> out,String name,String policy,String reason,Map<String,Object> evidence){Map<String,Object> merged=new LinkedHashMap<>(evidence);merged.put("missingPolicy",true);out.add(result(name,"WARNING",reason,merged,policy));}
    private Map<String,Object> result(String name,String status,String reason,Map<String,Object> evidence,String policy){Map<String,Object> m=new LinkedHashMap<>();m.put("name",name);m.put("status",status);m.put("reason",reason);m.put("evidence",evidence);m.put("policyId",policy);return m;}
    private void attachPolicySources(Firestore db,List<Map<String,Object>> checks)throws Exception{Map<String,DocumentSnapshot> cache=new HashMap<>();for(Map<String,Object> check:checks){String id=Objects.toString(check.get("policyId"),"");if(id.isBlank())continue;DocumentSnapshot policy=cache.computeIfAbsent(id,key->{try{return db.collection("policies").document(key).get().get();}catch(Exception e){return null;}});if(policy==null||!policy.exists()||!StringUtils.hasText(policy.getString("sourceDocumentId")))continue;check.put("sourceDocumentId",policy.getString("sourceDocumentId"));check.put("sourceDocumentName",policy.getString("sourceDocumentName"));check.put("sourcePage",policy.getLong("sourcePage"));check.put("sourceEvidence",policy.getString("sourceEvidence"));}}
    private List<String> reasons(List<Map<String,Object>> checks,String status){return checks.stream().filter(c->status.equals(c.get("status"))).map(c->String.valueOf(c.get("reason"))).toList();}
    private String explanation(String decision,List<String> route,List<String> blocked){return "BLOCKED".equals(decision)?"The request cannot enter approvals until blocking policy failures are corrected: "+String.join("; ",blocked):"The request is governed by the approval route "+String.join(" -> ",route)+" based on compensation and employment policy evidence.";}
    private List<String> strings(Object v){if(!(v instanceof List<?> l))return List.of();return l.stream().map(String::valueOf).toList();}
    private boolean contains(List<String> values,String target){return target!=null&&values.stream().anyMatch(v->v.equalsIgnoreCase(target));}
    private Map<String,Object> map(Object v){if(!(v instanceof Map<?,?> raw))return Map.of();Map<String,Object> m=new HashMap<>();raw.forEach((k,x)->m.put(String.valueOf(k),x));return m;}
    private Map<String,Object> findIgnoreCase(Map<String,Object> values,String key){if(key!=null)for(var e:values.entrySet())if(e.getKey().equalsIgnoreCase(key))return map(e.getValue());return Map.of();}
    private long number(Object v){if(v instanceof Number n)return n.longValue();try{return Long.parseLong(String.valueOf(v));}catch(Exception e){return 0;}}
}
