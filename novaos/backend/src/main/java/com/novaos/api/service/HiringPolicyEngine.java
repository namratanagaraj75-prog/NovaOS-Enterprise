package com.novaos.api.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.novaos.api.dto.HiringRequestDtos.CandidateInput;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

/** Evaluates hiring configuration from settings/hiringPolicy without creating Firestore data. */
@Service
public class HiringPolicyEngine {
    public Map<String,Object> evaluate(Firestore db, CandidateInput candidate, String currentRequestId,
            Map<String,Object> manager) throws Exception {
        DocumentSnapshot settings=db.collection("settings").document("hiringPolicy").get().get();
        List<Map<String,Object>> checks=new ArrayList<>();
        check(checks,"Mandatory Candidate Fields","MANDATORY_FIELDS",required(candidate),"All mandatory hiring fields are present.","One or more mandatory hiring fields are missing.",Map.of());
        boolean validEmail=StringUtils.hasText(candidate.candidateEmail())&&candidate.candidateEmail().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        check(checks,"Candidate Email","MANDATORY_FIELDS",validEmail,"Candidate email format is valid.","Candidate email format is invalid.",Map.of("email",Objects.toString(candidate.candidateEmail(),"")));
        check(checks,"Manager Validation","MANAGER_VALIDATION",manager!=null,"An active hiring manager was resolved from users.","No active hiring manager could be resolved.",Map.of("manager",Objects.toString(candidate.hiringManagerName(),"")));

        List<String> types=strings(settings.get("allowedEmploymentTypes"));
        boolean knownType=types.isEmpty()||!StringUtils.hasText(candidate.employmentType())||contains(types,candidate.employmentType());
        warning(checks,"Employment Type","EMPLOYMENT_TYPE",!knownType,knownType?"Employment type is recognized.":"Employment type is outside the configured list; HR should verify it.",Map.of("employmentType",Objects.toString(candidate.employmentType(),"Not provided")));

        boolean validDate=false,future=false;
        try{LocalDate date=LocalDate.parse(candidate.joiningDate());validDate=true;future=date.isAfter(LocalDate.now());}catch(Exception ignored){}
        boolean futureRequired=Boolean.TRUE.equals(settings.getBoolean("futureJoiningDateRequired"));
        check(checks,"Joining Date","JOINING_DATE",validDate&&(!futureRequired||future),"Joining date is valid.",validDate?"Joining date must be in the future.":"Joining date must be a valid ISO date.",Map.of("joiningDate",Objects.toString(candidate.joiningDate(),"")));

        boolean duplicate=duplicate(db,"candidates","email",candidate.candidateEmail(),currentRequestId)
                ||duplicate(db,"candidates","candidateEmail",candidate.candidateEmail(),currentRequestId)
                ||duplicate(db,"hiringRequests","candidateEmail",candidate.candidateEmail(),currentRequestId);
        warning(checks,"Duplicate Hiring","DUPLICATE_HIRING",duplicate,duplicate?"A candidate or hiring request already uses this email; HR should verify the identity.":"No duplicate hiring identity was found.",Map.of("email",Objects.toString(candidate.candidateEmail(),"")));

        long amount=candidate.annualSalaryAmount()==null?0:candidate.annualSalaryAmount();
        Map<String,Object> band=findIgnoreCase(map(settings.get("salaryBands")),candidate.jobTitle());
        long min=number(band.get("min")),max=number(band.get("max")),block=number(band.get("blockThreshold"));
        if(band.isEmpty()||min<=0||max<=0)warning(checks,"Salary Band","SALARY_RANGE",true,"No salary range is configured for this role; Finance review remains required.",Map.of("role",Objects.toString(candidate.jobTitle(),""),"annualSalary",amount));
        else if(block>0&&amount>block)check(checks,"Salary Band","SALARY_RANGE",false,"Compensation is within range.","Compensation exceeds the configured blocking threshold.",Map.of("annualSalary",amount,"minimum",min,"maximum",max,"blockThreshold",block));
        else warning(checks,"Salary Band","SALARY_RANGE",amount<min||amount>max,amount<min||amount>max?"Compensation is outside the configured range and requires Finance review.":"Compensation is within the configured range.",Map.of("annualSalary",amount,"minimum",min,"maximum",max));

        boolean budget=!Boolean.FALSE.equals(settings.getBoolean("budgetAvailable"));
        check(checks,"Budget Availability","SALARY_RANGE",budget,"Hiring budget is available.","Hiring budget is unavailable.",Map.of("budgetAvailable",budget));
        long threshold=number(settings.get("highSalaryThreshold"));
        warning(checks,"High Salary Review","SALARY_RANGE",threshold>0&&amount>=threshold,threshold>0&&amount>=threshold?"Compensation meets the configured high-salary review threshold.":"Standard Finance and Legal review applies.",Map.of("annualSalary",amount,"threshold",threshold));

        List<String> route=approvalRoute(candidate,threshold);
        checks.add(result("Required Approval Chain","PASS","Approval route selected.",Map.of("approvalRoute",route),"WORKFLOW"));
        DocumentSnapshot runtime=db.collection("settings").document("hiringAutomation").get().get();
        boolean developmentMode=Boolean.TRUE.equals(runtime.getBoolean("developmentMode"));
        long failures=checks.stream().filter(c->"FAIL".equals(c.get("status"))).count();
        long warnings=checks.stream().filter(c->"WARNING".equals(c.get("status"))).count();
        int riskScore=(int)Math.min(100,failures*35+warnings*15);
        String decision=failures>0?"BLOCKED":warnings>0?"WARNING":"PASS",risk=riskScore>=60?"HIGH":riskScore>=25?"MEDIUM":"LOW";
        List<String> blocking=reasons(checks,"FAIL");
        Map<String,Object> passport=new LinkedHashMap<>();
        passport.put("decision",decision);passport.put("riskLevel",risk);passport.put("riskScore",riskScore);
        passport.put("explanation","BLOCKED".equals(decision)?"Correct blocking evidence: "+String.join("; ",blocking):"Proceed through "+String.join(" -> ",route)+" using current settings.");
        passport.put("warnings",reasons(checks,"WARNING"));passport.put("blockingReasons",blocking);
        passport.put("recommendation",failures>0?"Return to HR and correct failed evidence.":"Proceed through the governed approval route.");
        passport.put("policyChecks",checks);passport.put("policiesChecked",checks.stream().map(c->c.get("policyId")).distinct().toList());
        passport.put("approvalRoute",route);passport.put("developmentMode",developmentMode);passport.put("generatedAt",Timestamp.now());return passport;
    }

    List<String> approvalRoute(CandidateInput candidate,long highSalaryThreshold){return List.of("HIRING_MANAGER","FINANCE","LEGAL");}
    private boolean required(CandidateInput c){return c!=null&&StringUtils.hasText(c.candidateName())&&StringUtils.hasText(c.candidateEmail())&&StringUtils.hasText(c.jobTitle())&&c.annualSalaryAmount()!=null&&c.annualSalaryAmount()>0&&StringUtils.hasText(c.joiningDate())&&StringUtils.hasText(c.reportingManagerName())&&StringUtils.hasText(c.hiringManagerName());}
    private boolean duplicate(Firestore db,String collection,String field,String email,String current)throws Exception{if(!StringUtils.hasText(email))return false;return db.collection(collection).whereEqualTo(field,email.toLowerCase(Locale.ROOT)).get().get().getDocuments().stream().anyMatch(d->current==null||!d.getId().equals(current));}
    private void check(List<Map<String,Object>> out,String name,String policy,boolean pass,String ok,String fail,Map<String,Object> evidence){out.add(result(name,pass?"PASS":"FAIL",pass?ok:fail,evidence,policy));}
    private void warning(List<Map<String,Object>> out,String name,String policy,boolean warn,String reason,Map<String,Object> evidence){out.add(result(name,warn?"WARNING":"PASS",reason,evidence,policy));}
    private Map<String,Object> result(String name,String status,String reason,Map<String,Object> evidence,String policy){Map<String,Object>m=new LinkedHashMap<>();m.put("name",name);m.put("status",status);m.put("reason",reason);m.put("evidence",evidence);m.put("policyId",policy);return m;}
    private List<String> reasons(List<Map<String,Object>> checks,String status){return checks.stream().filter(c->status.equals(c.get("status"))).map(c->String.valueOf(c.get("reason"))).toList();}
    private List<String> strings(Object v){if(!(v instanceof List<?> l))return List.of();return l.stream().map(String::valueOf).toList();}
    private boolean contains(List<String> values,String target){return target!=null&&values.stream().anyMatch(v->v.equalsIgnoreCase(target));}
    private Map<String,Object> map(Object v){if(!(v instanceof Map<?,?> raw))return Map.of();Map<String,Object>m=new HashMap<>();raw.forEach((k,x)->m.put(String.valueOf(k),x));return m;}
    private Map<String,Object> findIgnoreCase(Map<String,Object> values,String key){if(key!=null)for(var e:values.entrySet())if(e.getKey().equalsIgnoreCase(key))return map(e.getValue());return Map.of();}
    private long number(Object v){if(v instanceof Number n)return n.longValue();try{return Long.parseLong(String.valueOf(v));}catch(Exception e){return 0;}}
}
