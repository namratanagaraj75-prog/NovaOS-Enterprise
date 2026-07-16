package com.novaos.api.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.novaos.api.dto.HiringRequestDtos.*;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class HiringRequestService {
    private static final Logger logger = LoggerFactory.getLogger(HiringRequestService.class);
    private final HiringCommandParser parser;
    private final HiringWorkflowRules rules;
    private final HiringPolicyEngine policyEngine;
    private final OfferLetterPdfService pdf;
    private final JavaMailSender mail;
    private final String fromName;
    private final String fromAddress;
    private final String mailHost;
    private final int mailPort;
    private final ResendEmailService resendEmailService;

    public HiringRequestService(HiringCommandParser parser, HiringWorkflowRules rules, HiringPolicyEngine policyEngine, OfferLetterPdfService pdf,
            JavaMailSender mail, @Value("${email.from.name:Nova HR}") String fromName,
            @Value("${email.from.address:${spring.mail.username:}}") String fromAddress,
            @Value("${spring.mail.host}") String mailHost, @Value("${spring.mail.port}") int mailPort,
            ResendEmailService resendEmailService) {
        this.parser=parser; this.rules=rules; this.policyEngine=policyEngine; this.pdf=pdf; this.mail=mail; this.fromName=fromName; this.fromAddress=fromAddress; this.mailHost=mailHost; this.mailPort=mailPort;
        this.resendEmailService = resendEmailService;
    }

    public ParseResponse parse(ParseRequest request, Authentication auth) {
        requireRole(auth, "HR_ADMIN");
        if (request == null || !StringUtils.hasText(request.instruction())) throw bad("Hiring instruction is required.");
        return parser.parse(request.instruction().trim());
    }

    public Map<String,Object> create(CreateRequest request, Authentication auth) {
        requireRole(auth, "HR_ADMIN");
        CandidateInput input = normalized(request == null ? null : request.candidate());
        List<String> errors = rules.validate(input, false);
        if (!errors.isEmpty()) throw unprocessable(String.join(" ", errors));
        try {
            Firestore db = db(); Map<String,Object> actor = actor(db, auth);
            Map<String,Object> manager = findManager(db, input.hiringManagerName());
            if (manager == null) throw unprocessable("No active HIRING_MANAGER user matches '" + input.hiringManagerName()
                    + "'. Create or correct that employee account before submitting approval.");
            String id = UUID.randomUUID().toString(); Timestamp now = Timestamp.now();
            Map<String,Object> passport=policyEngine.evaluate(db,input,id,manager);
            if("BLOCKED".equals(passport.get("decision"))) throw unprocessable("Policy validation blocked this request: "+String.join("; ",strings(passport.get("blockingReasons"))));
            List<String> approvalRoute=strings(passport.get("approvalRoute"));
            Map<String,Object> doc = new HashMap<>();
            doc.put("id", id); putCandidate(doc, input);
            String savedHiringManagerId = String.valueOf(manager.get("uid"));
            logManagerAssignment(input.hiringManagerName(), manager, savedHiringManagerId);
            doc.put("hiringManagerId", savedHiringManagerId); doc.put("reportingManagerId", null);
            doc.put("status", "PENDING_MANAGER_APPROVAL"); doc.put("createdBy", actor.get("uid")); doc.put("createdByName", actor.get("name"));
            doc.put("createdAt", now); doc.put("updatedAt", now); doc.put("originalInstruction", request.originalInstruction());
            doc.put("aiExtracted",candidateMap(request.aiExtractedCandidate())); doc.put("hrConfirmed",candidateMap(input));
            doc.put("fieldChangeHistory",fieldChanges(request.aiExtractedCandidate(),input,actor,now));
            doc.put("decisionPassport",passport); doc.put("policyChecks",passport.get("policyChecks")); doc.put("decision",passport.get("decision"));
            doc.put("riskLevel",passport.get("riskLevel")); doc.put("riskScore",passport.get("riskScore")); doc.put("approvalRoute",approvalRoute);
            doc.put("currentApprovalIndex",0); doc.put("currentApproverRole","HIRING_MANAGER"); doc.put("managerApprovalStatus","PENDING");
            doc.put("offerLetterStatus","NOT_READY"); doc.put("emailStatus", "NOT_READY"); doc.put("emailRetryCount",0L);
            doc.put("activityHistory", List.of(activityAt("REQUEST_CREATED",actor,"Hiring request created from HR-confirmed AI extraction.",now,id),
                    activityAt("POLICY_VALIDATED",actor,"Decision Passport generated with decision "+passport.get("decision")+" and risk "+passport.get("riskLevel")+".",now,id),
                    activityAt("ROUTED_TO_MANAGER",actor,"Request routed to the verified hiring manager.",now,id)));
            db.collection("hiringRequests").document(id).create(doc).get();
            writeAudit(db,id,"REQUEST_CREATED",actor,"Hiring request created and policy validated.");
            return get(id, auth);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw server("Firestore could not create the hiring request", e); }
    }

    public Map<String,Object> update(String id, UpdateRequest request, Authentication auth) {
        requireRole(auth, "HR_ADMIN"); CandidateInput input = normalized(request == null ? null : request.candidate());
        List<String> errors=rules.validate(input,false); if(!errors.isEmpty()) throw unprocessable(String.join(" ",errors));
        try { Firestore db=db(); DocumentSnapshot old=requireDocument(db,id); Map<String,Object> actor=actor(db,auth);
            if (!List.of("DRAFT","CHANGES_REQUESTED").contains(old.getString("status"))) throw conflict("Only draft or changes-requested items can be edited.");
            if (!Objects.equals(old.getString("createdBy"), actor.get("uid"))) throw forbidden("Only the creating HR user can edit this request.");
            Map<String,Object> manager=findManager(db,input.hiringManagerName()); if(manager==null) throw unprocessable("No active HIRING_MANAGER matches the hiring manager name.");
            String savedHiringManagerId=String.valueOf(manager.get("uid")); logManagerAssignment(input.hiringManagerName(),manager,savedHiringManagerId);
            Timestamp now=Timestamp.now(); Map<String,Object> passport=policyEngine.evaluate(db,input,id,manager); List<String> route=strings(passport.get("approvalRoute"));
            Map<String,Object> updates=new HashMap<>(); putCandidate(updates,input); updates.put("hiringManagerId",savedHiringManagerId);
            updates.put("hrConfirmed",candidateMap(input)); updates.put("decisionPassport",passport); updates.put("policyChecks",passport.get("policyChecks"));
            updates.put("decision",passport.get("decision")); updates.put("riskLevel",passport.get("riskLevel")); updates.put("riskScore",passport.get("riskScore")); updates.put("approvalRoute",route);
            boolean blocked="BLOCKED".equals(passport.get("decision")); updates.put("status",blocked?"DRAFT":"PENDING_MANAGER_APPROVAL");
            updates.put("currentApprovalIndex",blocked?-1:0); updates.put("currentApproverRole",blocked?FieldValue.delete():"HIRING_MANAGER");
            updates.put("managerApprovalStatus",blocked?"NOT_READY":"PENDING"); updates.put("updatedAt",FieldValue.serverTimestamp());
            List<Map<String,Object>> changes=fieldChanges(candidateFromDocument(old),input,actor,now);
            if(!changes.isEmpty()) updates.put("fieldChangeHistory",FieldValue.arrayUnion(changes.toArray()));
            updates.put("activityHistory",FieldValue.arrayUnion(activityAt("REQUEST_EDITED",actor,"HR corrected hiring request fields.",now,id),
                    activityAt("POLICY_VALIDATED",actor,"Policy validation re-ran after HR changes; decision: "+passport.get("decision")+".",now,id)));
            db.collection("hiringRequests").document(id).update(updates).get(); return get(id,auth);
        } catch(ResponseStatusException e){throw e;} catch(Exception e){throw server("Hiring request update failed",e);} }

    public Map<String,Object> submit(String id, Authentication auth) {
        requireRole(auth,"HR_ADMIN");
        try { Firestore db=db(); DocumentSnapshot d=requireDocument(db,id); Map<String,Object> actor=actor(db,auth);
            if(!Objects.equals(d.getString("createdBy"),actor.get("uid"))) throw forbidden("Only the creating HR user can submit this request.");
            CandidateInput input=candidateFromDocument(d); Map<String,Object> manager=findManager(db,input.hiringManagerName());
            Map<String,Object> passport=policyEngine.evaluate(db,input,id,manager);
            if("BLOCKED".equals(passport.get("decision"))) throw unprocessable("Policy validation blocked submission: "+String.join("; ",strings(passport.get("blockingReasons"))));
            String candidateName = d.getString("candidateName");
            Map<String,Object> updates=new HashMap<>();updates.put("status","PENDING_MANAGER_APPROVAL");updates.put("currentApproverRole","HIRING_MANAGER");updates.put("currentApprovalIndex",0);
            updates.put("approvalRoute",passport.get("approvalRoute"));updates.put("decisionPassport",passport);updates.put("policyChecks",passport.get("policyChecks"));updates.put("decision",passport.get("decision"));
            updates.put("riskLevel",passport.get("riskLevel"));updates.put("riskScore",passport.get("riskScore"));updates.put("managerApprovalStatus","PENDING");updates.put("updatedAt",FieldValue.serverTimestamp());
            updates.put("readBy", FieldValue.delete());
            updates.put("activityHistory",FieldValue.arrayUnion(activity("POLICY_VALIDATED",actor,"Policy validation passed and the request was routed to Manager.",id)));
            db.collection("hiringRequests").document(id).update(updates).get();
            writeNotification(db, id, "HIRING_MANAGER", "Hiring Manager approval pending", "Hiring request for " + candidateName + " is pending your approval.");
            return get(id,auth);
        } catch(ResponseStatusException e){throw e;} catch(IllegalStateException e){throw conflict(e.getMessage());} catch(Exception e){throw server("Submit failed",e);} }

    public Map<String,Object> decide(String id, DecisionRequest request, Authentication auth) {
        requireAny(auth,"HIRING_MANAGER","FINANCE","LEGAL","CEO"); String action=request==null?"":String.valueOf(request.action()).toUpperCase();
        if(!List.of("APPROVE","REJECT","REQUEST_CHANGES").contains(action)) throw bad("Action must be APPROVE, REJECT, or REQUEST_CHANGES.");
        if(!"APPROVE".equals(action) && !StringUtils.hasText(request.reason())) throw bad("A reason is required.");
        try { Firestore db=db(); DocumentSnapshot d=requireDocument(db,id); Map<String,Object> actor=actor(db,auth);
            String approverRole=role(auth);
            logger.info("Logged-in approver role={} uid={}", approverRole, actor.get("uid"));
            requireCurrentApprover(d,approverRole,actor);
            if(Objects.equals(d.getString("createdBy"),actor.get("uid"))) throw forbidden("Users cannot approve their own request.");
            if (Set.of("FINANCE", "LEGAL").contains(approverRole)) {
                CandidateInput candidate = candidateFromDocument(d);
                Map<String,Object> manager = findManager(db, candidate.hiringManagerName());
                Map<String,Object> passport = policyEngine.evaluate(db, candidate, id, manager);
                if ("BLOCKED".equals(passport.get("decision")))
                    throw unprocessable("Current policy validation blocked approval: " + String.join("; ", strings(passport.get("blockingReasons"))));
            }
            String prefix=approvalPrefix(approverRole);
            if("APPROVED".equals(d.getString(prefix+"ApprovalStatus")) || "REJECTED".equals(d.getString(prefix+"ApprovalStatus"))) {
                throw conflict("Approval decision has already been recorded for this role.");
            }
            ApprovalTransition transition=approvalTransition(d,approverRole,action,request.reason());
            Timestamp now=Timestamp.now(); Map<String,Object> updates=new HashMap<>();
            updates.put("status",transition.nextStatus()); updates.put("updatedAt",FieldValue.serverTimestamp());
            updates.put("readBy", FieldValue.delete());
            
            String decision = "APPROVE".equals(action) ? "APPROVED" : ("REJECT".equals(action) ? "REJECTED" : "CHANGES_REQUESTED");
            updates.put(prefix+"ApprovalStatus", decision);
            updates.put(prefix+"ApprovedByName", actor.get("name"));
            updates.put(prefix+"ApprovedByEmail", actor.get("email"));
            updates.put(prefix+"ApprovedBy", actor.get("uid"));
            updates.put(prefix+"ApprovedAt", FieldValue.serverTimestamp());
            updates.put(prefix+"ApprovalComment", Objects.toString(request.reason(), ""));
            updates.put(prefix+"PreviousStatus", d.getString("status"));
            updates.put(prefix+"NewStatus", transition.nextStatus());

            List<Map<String,Object>> events=new ArrayList<>(); 
            events.add(activityAt(transition.activityAction(),actor,transition.details(),now,id));
            if(transition.nextApproverRole()!=null) {
                events.add(activityAt("ROUTED_TO_"+transition.nextApproverRole(),actor,
                    "Request routed to "+transition.nextApproverRole()+" for the next governed approval.",now,id));
            }
            if(transition.finalApproval()) {
                events.add(activityAt("APPROVALS_COMPLETED",actor,"All policy-required approvals are complete.",now,id));
            }
            updates.put("activityHistory",FieldValue.arrayUnion(events.toArray()));
            if("APPROVE".equals(action)) {
                applyApprovalFields(updates,approverRole,actor,transition.nextApproverRole());
                updates.put("currentApprovalIndex",transition.nextApprovalIndex());
            } else {
                updates.put("currentApproverRole",FieldValue.delete()); 
                updates.put("rejectionReason",request.reason());
                if("REJECT".equals(action)){
                    updates.put("rejectedBy",actor.get("uid"));
                    updates.put("rejectedAt",FieldValue.serverTimestamp());
                }
            }
            Map<String,Object> approval=new HashMap<>(); approval.put("requestId",id); approval.put("approverUid",actor.get("uid")); approval.put("approverEmail",actor.get("email"));
            approval.put("approverName",actor.get("name")); approval.put("approverRole",approverRole); approval.put("status","APPROVE".equals(action)?"APPROVED":action);
            approval.put("comment",Objects.toString(request.reason(),"")); approval.put("timestamp",now);
            WriteBatch batch=db.batch(); batch.update(db.collection("hiringRequests").document(id),updates);
            batch.set(db.collection("approvals").document(id+"-"+approverRole),approval,SetOptions.merge());
            batch.create(db.collection("auditLogs").document(id+"-"+transition.activityAction()+"-"+UUID.randomUUID()),audit(id,transition.activityAction(),actor,transition.details()));

            // Synchronize status updates to the corresponding workflowRequests document
            var workflowRef = db.collection("workflowRequests").document(id);
            if (workflowRef.get().get().exists()) {
                Map<String, Object> workflowUpdates = new HashMap<>();
                String approvedState = approverRole.equals("HIRING_MANAGER") ? "MANAGER_APPROVED" : approverRole + "_APPROVED";
                String nextState = approverRole.equals("HIRING_MANAGER") ? "LEGAL_PENDING" 
                                 : approverRole.equals("LEGAL") ? "FINANCE_PENDING" 
                                 : "OFFER_GENERATING";
                workflowUpdates.put("state", "APPROVE".equals(action) ? (transition.finalApproval() ? "OFFER_GENERATED" : nextState) : ("REJECT".equals(action) ? "FAILED" : "CHANGES_REQUESTED"));
                workflowUpdates.put("lastCompletedState", "APPROVE".equals(action) ? approvedState : ("REJECT".equals(action) ? "FAILED" : "CHANGES_REQUESTED"));
                workflowUpdates.put("updatedAt", java.time.Instant.now().toString());
                batch.update(workflowRef, workflowUpdates);
            }

            batch.commit().get();
            
            String candidateName = d.getString("candidateName");
            if ("APPROVE".equals(action)) {
                if (transition.nextApproverRole() != null) {
                    String nextRole = transition.nextApproverRole();
                    String title = nextRole.replace("_", " ") + " approval pending";
                    writeNotification(db, id, nextRole, title, "Hiring request for " + candidateName + " is pending your approval.");
                } else {
                    writeNotification(db, id, "HR_ADMIN", "Request approved", "Hiring request for " + candidateName + " has been approved.");
                }
            } else {
                String title = "REJECT".equals(action) ? "Request rejected" : "Changes requested";
                String desc = "Hiring request for " + candidateName + " was " + ("REJECT".equals(action) ? "rejected." : "sent back for changes.");
                writeNotification(db, id, "HR_ADMIN", title, desc);
            }
            
            if(transition.finalApproval()) generateOfferAndSend(id); return get(id,auth);
        } catch(ResponseStatusException e){throw e;} catch(Exception e){throw server("Approval decision failed",e);} }

    public byte[] pdf(String id, Authentication auth) { requireAny(auth,"HR_ADMIN","HIRING_MANAGER","FINANCE","LEGAL","CEO","SUPER_ADMIN");
        try { Firestore db=db(); DocumentSnapshot d=requireDocument(db,id); authorizeRead(d,auth);
            if(!List.of("OFFER_GENERATED", "EMAIL_SENDING", "EMAIL_SENT", "WORKFLOW_COMPLETED", "APPROVED", "EMAIL_FAILED").contains(d.getString("status"))) {
                throw notFound("Offer PDF has not been generated yet.");
            }
            Map<String,Object> values=new HashMap<>(d.getData()); values.put("offerReferenceId","NOVA-"+id.substring(0,8).toUpperCase());
            values.put("approvalSummary",approvalSummary(d)); return pdf.generate(values);
        } catch(ResponseStatusException e){throw e;} catch(Exception e){throw server("PDF download failed",e);} }

    public Map<String,Object> send(String id, EmailRequest request, Authentication auth) { requireRole(auth,"HR_ADMIN");
        try { Firestore db=db(); DocumentSnapshot d=requireDocument(db,id); Map<String,Object> actor=actor(db,auth);
            if(request==null||!request.resendConfirmed()) throw conflict("Explicit retry confirmation is required.");
            String emailStatus = d.getString("emailStatus");
            if (!List.of("SENT", "FAILED").contains(emailStatus)) {
                throw conflict("Manual resend is available only after an initial email attempt.");
            }
            deliverEmail(id,actor,true); return get(id,auth);
        } catch(ResponseStatusException e){throw e;} catch(Exception e){throw server("Offer email retry failed",e);} }

    @SuppressWarnings("unused")
    private Map<String,Object> legacySend(String id, EmailRequest request, Authentication auth) { requireRole(auth,"HR_ADMIN");
        try { Firestore db=db(); DocumentReference ref=db.collection("hiringRequests").document(id); DocumentSnapshot d=requireDocument(db,id); Map<String,Object> actor=actor(db,auth);
            String status=d.getString("status"); if("EMAIL_SENT".equals(status)) throw conflict("Offer was already sent. Duplicate sending is blocked.");
            if(!List.of("PDF_GENERATED","EMAIL_FAILED").contains(status)) throw conflict("Offer email requires a generated PDF and manager approval.");
            if("EMAIL_FAILED".equals(status) && (request==null || !request.resendConfirmed())) throw conflict("Explicit resend confirmation is required.");
            if(!StringUtils.hasText(fromAddress)) throw server("EMAIL_FROM_ADDRESS or MAIL_USERNAME is missing",null);
            Map<String,Object> values=new HashMap<>(d.getData()); values.put("offerReferenceId","NOVA-"+id.substring(0,8).toUpperCase());
            values.put("approvalSummary",approvalSummary(d)); byte[] pdfBytes=pdf.generate(values);
            ref.update(Map.of("emailStatus","SENDING","updatedAt",Timestamp.now(),"activityHistory",FieldValue.arrayUnion(activity("EMAIL_ATTEMPTED",actor,"SMTP delivery started.")))).get();
            try { MimeMessage message=mail.createMimeMessage(); MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8");
                helper.setFrom(fromAddress,fromName); helper.setTo(d.getString("candidateEmail")); helper.setSubject("Congratulations – Offer Letter for " + d.getString("jobTitle"));
                helper.setText(emailBody(d),false); helper.addAttachment(d.getString("pdfFileName"),new ByteArrayResource(pdfBytes),"application/pdf");
                message.saveChanges(); String messageId=message.getMessageID(); mail.send(message); Timestamp now=Timestamp.now();
                ref.update(Map.of("status","EMAIL_SENT","emailStatus","SENT","emailSentAt",now,"emailMessageId",messageId==null?"":messageId,
                        "updatedAt",now,"activityHistory",FieldValue.arrayUnion(activity("EMAIL_SENT",actor,"Offer accepted by SMTP provider.")))).get();
            } catch(Exception mailError){ ref.update(Map.of("status","EMAIL_FAILED","emailStatus","FAILED","emailError","Email delivery failed. Verify SMTP configuration and retry.",
                    "updatedAt",Timestamp.now(),"activityHistory",FieldValue.arrayUnion(activity("EMAIL_FAILED",actor,"SMTP delivery failed.")))).get();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Email delivery failed: "+mailError.getMessage()); }
            return get(id,auth);
        } catch(ResponseStatusException e){throw e;} catch(Exception e){throw server("Offer email failed",e);} }

    public List<Map<String,Object>> list(Authentication auth) { requireAny(auth,"HR_ADMIN","HIRING_MANAGER","FINANCE","LEGAL","CEO","SUPER_ADMIN");
        try { Firestore db=db(); Map<String,Object> actor=actor(db,auth); String role=role(auth); List<Map<String,Object>> out=new ArrayList<>();
            List<QueryDocumentSnapshot> documents;
            if("HIRING_MANAGER".equals(role)) {
                String currentUserUid=String.valueOf(actor.get("uid"));
                logger.info("Logged-in manager uid={}", currentUserUid);
                documents=db.collection("hiringRequests")
                        .whereEqualTo("hiringManagerId",currentUserUid)
                        .whereEqualTo("status","PENDING_MANAGER_APPROVAL")
                        .get().get().getDocuments();
            } else if("FINANCE".equals(role)) {
                documents=pendingForRole(db,"PENDING_FINANCE_APPROVAL","FINANCE");
            } else if("LEGAL".equals(role)) {
                documents=pendingForRole(db,"PENDING_LEGAL_APPROVAL","LEGAL");
            } else if("CEO".equals(role)) {
                documents=pendingForRole(db,"PENDING_CEO_APPROVAL","CEO");
            } else {
                documents=db.collection("hiringRequests").get().get().getDocuments();
            }
            for(QueryDocumentSnapshot d:documents){Map<String,Object> m=new HashMap<>(d.getData());m.put("id",d.getId());out.add(m);} return out;
        } catch(Exception e){throw server("Hiring request list failed",e);} }

    public Map<String,Object> get(String id, Authentication auth) { requireAny(auth,"HR_ADMIN","HIRING_MANAGER","FINANCE","LEGAL","CEO","SUPER_ADMIN");
        try { DocumentSnapshot d=requireDocument(db(),id); authorizeRead(d,auth); Map<String,Object> out=new HashMap<>(d.getData());out.put("id",d.getId());return out; }
        catch(ResponseStatusException e){throw e;} catch(Exception e){throw server("Hiring request read failed",e);} }

    private String buildEmailBody(String candidateName) {
        return "Dear " + candidateName + ",\n\n" +
               "Congratulations! Your hiring request has completed all required approvals.\n\n" +
               "Please find your official offer letter attached to this email.\n\n" +
               "Regards,\n" +
               "NovaOS HR Team";
    }

    private void generatePdf(String id, Map<String,Object> actor) throws Exception { generatePdf(id,actor,false); }
    private void generatePdf(String id, Map<String,Object> actor, boolean force) throws Exception { Firestore db=db(); DocumentReference ref=db.collection("hiringRequests").document(id); DocumentSnapshot d=requireDocument(db,id);
        if(!force&&StringUtils.hasText(d.getString("pdfFileName")))return;
        if(!force&&!List.of("APPROVALS_COMPLETED","LEGAL_APPROVED").contains(d.getString("status")))throw conflict("All policy-required approvals are required before offer generation.");
        Timestamp started=Timestamp.now(); ref.update(Map.of("status","GENERATING_OFFER","offerLetterStatus","GENERATING","updatedAt",FieldValue.serverTimestamp(),
                "activityHistory",FieldValue.arrayUnion(activityAt("OFFER_GENERATION_STARTED",systemActor(),"Backend offer generation started after final approval.",started,id)))).get();
        try{
            d=requireDocument(db,id); Map<String,Object> values=new HashMap<>(d.getData()); values.put("offerReferenceId","NOVA-"+id.substring(0,8).toUpperCase());
            values.put("approvalSummary",approvalSummary(d));
            String safe=sanitizeFileName(d.getString("candidateName")); String filename="Offer_Letter_"+safe+"_"+id+".pdf";
            Timestamp now=Timestamp.now();
            Map<String,Object> updates=new HashMap<>(); updates.put("status","OFFER_GENERATED");updates.put("currentApproverRole",FieldValue.delete());updates.put("pdfUrl","/api/hiring/requests/"+id+"/pdf");
            updates.put("pdfFileName",filename);updates.put("pdfGeneratedAt",now);updates.put("offerReferenceId",values.get("offerReferenceId"));
            updates.put("offerLetterStatus","GENERATED");updates.put("offerLetterUrl","/api/hiring/requests/"+id+"/pdf");
            updates.put("offerLetterGeneratedAt",now);
            updates.put("emailStatus","PENDING");updates.put("updatedAt",FieldValue.serverTimestamp());updates.put("activityHistory",FieldValue.arrayUnion(activityAt(force?"OFFER_REGENERATED":"OFFER_GENERATED",actor,"Professional offer PDF generated on the backend.",now,id)));
            
            Map<String,Object> document=new HashMap<>();
            document.put("requestId",id);
            document.put("candidateName",d.getString("candidateName"));
            document.put("candidateEmail",d.getString("candidateEmail"));
            document.put("documentType","OFFER_LETTER");
            document.put("generatedAt",now);
            document.put("emailStatus","PENDING");
            document.put("sentBy",actor.get("uid"));
            document.put("finalApprovalStatus","APPROVALS_COMPLETED");
            document.put("documentFileName",filename);
            
            WriteBatch batch=db.batch();batch.update(ref,updates);batch.set(db.collection("documents").document(id+"-offer"),document);
            batch.create(db.collection("auditLogs").document(id+"-OFFER_GENERATED-"+UUID.randomUUID()),audit(id,"OFFER_GENERATED",actor,"Offer PDF generated."));batch.commit().get();
        }catch(Exception error){ref.update(Map.of("status","APPROVALS_COMPLETED","offerLetterStatus","FAILED","offerLetterFailureReason",safeMessage(error),"updatedAt",FieldValue.serverTimestamp(),
                "activityHistory",FieldValue.arrayUnion(activity("OFFER_GENERATION_FAILED",systemActor(),safeMessage(error),id)))).get();throw error;}
    }

    private void generateOfferAndSend(String id)throws Exception{
        Map<String,Object> system=systemActor(); generatePdf(id,system,false);
        try{deliverEmail(id,system,false);}catch(Exception e){logger.error("Automatic offer delivery failed for request {}: {}",id,e.getMessage());}
    }

    private void deliverEmail(String id,Map<String,Object> actor,boolean isResend)throws Exception{
        Firestore db=db();DocumentReference ref=db.collection("hiringRequests").document(id);DocumentSnapshot d=requireDocument(db,id);
        if(!isResend && "SENT".equals(d.getString("emailStatus")))throw conflict("Offer email has already been delivered; duplicate send prevented.");
        
        String email = d.getString("candidateEmail");
        
        long retries=Optional.ofNullable(d.getLong("emailRetryCount")).orElse(0L);
        boolean isRetry = "FAILED".equals(d.getString("emailStatus")) || isResend;
        Timestamp started=Timestamp.now();
        
        String startAction = isResend ? "EMAIL_RESEND_STARTED" : (isRetry ? "EMAIL_RETRY_STARTED" : "EMAIL_SEND_STARTED");
        String startDetails = isResend ? "Manual SMTP delivery resend started by " + actor.get("name") + "." : (isRetry ? "Manual SMTP delivery retry started." : "Secure backend SMTP delivery started.");
        
        ref.update(Map.of("status","EMAIL_SENDING","emailStatus","SENDING","emailRecipient",email,"emailRetryCount",isRetry?retries+1:retries,
                "updatedAt",FieldValue.serverTimestamp(),"activityHistory",FieldValue.arrayUnion(activityAt(startAction,actor,startDetails,started,id)))).get();
        
        String candidateName = d.getString("candidateName");
        String sanitized = sanitizeFileName(candidateName);
        String filename = "Offer_Letter_" + sanitized + "_" + id + ".pdf";
        byte[] pdfBytes = null;
        try{
            if ("resend".equalsIgnoreCase(System.getenv("EMAIL_PROVIDER"))) {
                String apiKey = System.getenv("RESEND_API_KEY");
                if (!StringUtils.hasText(apiKey)) {
                    throw new IllegalStateException("RESEND_API_KEY environment variable is missing.");
                }
            } else {
                if(!StringUtils.hasText(fromAddress))throw new IllegalStateException("EMAIL_FROM_ADDRESS or MAIL_USERNAME is missing in backend/.env");
            }
            if (!StringUtils.hasText(email) || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) throw bad("Invalid candidate email address.");
            
            Map<String,Object> values=new HashMap<>(d.getData());
            values.put("offerReferenceId","NOVA-"+id.substring(0,8).toUpperCase());
            values.put("approvalSummary",approvalSummary(d));
            pdfBytes = pdf.generate(values);
            
            String messageId = "";
            if ("resend".equalsIgnoreCase(System.getenv("EMAIL_PROVIDER"))) {
                messageId = resendEmailService.sendEmail(email, "Your Official Offer Letter – NovaOS", buildEmailBody(candidateName), filename, pdfBytes);
            } else {
                logger.info("Sending offer email via SMTP host={}, port={}, sender={}, recipient={}", mailHost, mailPort, fromAddress, email);
                MimeMessage message=mail.createMimeMessage();
                MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8");
                helper.setFrom(fromAddress,fromName);
                helper.setTo(email);
                helper.setSubject("Your Official Offer Letter – NovaOS");
                helper.setText(buildEmailBody(candidateName), false);
                helper.addAttachment(filename, new ByteArrayResource(pdfBytes), "application/pdf");
                
                message.saveChanges();
                messageId=Objects.toString(message.getMessageID(),"");
                mail.send(message);
            }
            Timestamp sent=Timestamp.now();
            
            String successAction = isResend ? "EMAIL_RESEND_SUCCESS" : (isRetry ? "EMAIL_RETRY_SUCCESS" : "EMAIL_SENT");
            String successDetails = isResend ? "Offer PDF manually resent to "+email+"." : (isRetry ? "Offer PDF retry delivered to "+email+"." : "Offer PDF delivered to "+email+".");
            
            ref.update(Map.of("status","WORKFLOW_COMPLETED","emailStatus","SENT","emailSentAt",sent,"emailMessageId",messageId,"emailFailureReason",FieldValue.delete(),"updatedAt",FieldValue.serverTimestamp(),
                    "activityHistory",FieldValue.arrayUnion(activityAt(successAction,actor,successDetails,sent,id),activityAt("WORKFLOW_COMPLETED",actor,"Hiring workflow completed after verified email delivery.",sent,id)))).get();
            
            Map<String,Object> docUpdates = new HashMap<>();
            docUpdates.put("requestId", id);
            docUpdates.put("candidateName", candidateName);
            docUpdates.put("candidateEmail", email);
            docUpdates.put("documentType", "OFFER_LETTER");
            docUpdates.put("generatedAt", sent);
            docUpdates.put("emailSentAt", sent);
            docUpdates.put("emailStatus", "SENT");
            docUpdates.put("sentBy", actor.get("uid"));
            docUpdates.put("finalApprovalStatus", "APPROVALS_COMPLETED");
            docUpdates.put("documentFileName", filename);
            db.collection("documents").document(id+"-offer").set(docUpdates).get();
            
            writeAudit(db,id,successAction,actor,successDetails);
            writeNotification(db, id, "HR_ADMIN", "Email sent", "Offer letter email delivered to " + email);
        }catch(Exception error){
            Timestamp failed=Timestamp.now();
            String errorMsg = safeMessage(error);
            logger.error("Email delivery failed via provider={}, recipient={}; root cause: {}", System.getenv("EMAIL_PROVIDER"), email, rootCauseMessage(error), error);
            
            String failureAction = isResend ? "EMAIL_RESEND_FAILED" : (isRetry ? "EMAIL_RETRY_FAILED" : "EMAIL_FAILED");
            String failureDetails = isResend ? "Manual email resend failed. "+errorMsg : (isRetry ? "Email retry failed; PDF generation metadata preserved. "+errorMsg : "Email failed; PDF generation metadata preserved for retry. "+errorMsg);
            
            ref.update(Map.of("status","OFFER_GENERATED","emailStatus","FAILED","emailFailureReason",errorMsg,"emailErrorMessage",errorMsg,"updatedAt",FieldValue.serverTimestamp(),
                    "readBy", FieldValue.delete(),
                    "activityHistory",FieldValue.arrayUnion(activityAt(failureAction,actor,failureDetails,failed,id)))).get();
            writeNotification(db, id, "HR_ADMIN", "Email failed", "Failed to send offer letter to " + email + ". Error: " + errorMsg);
            
            Map<String,Object> docUpdates = new HashMap<>();
            docUpdates.put("requestId", id);
            docUpdates.put("candidateName", candidateName);
            docUpdates.put("candidateEmail", email);
            docUpdates.put("documentType", "OFFER_LETTER");
            docUpdates.put("emailStatus", "FAILED");
            docUpdates.put("emailErrorMessage", errorMsg);
            docUpdates.put("sentBy", actor.get("uid"));
            docUpdates.put("finalApprovalStatus", "APPROVALS_COMPLETED");
            docUpdates.put("documentFileName", filename);
            db.collection("documents").document(id+"-offer").set(docUpdates,SetOptions.merge()).get();
            
            writeAudit(db,id,failureAction,actor,errorMsg);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Email delivery failed: " + errorMsg);
        } finally { pdfBytes = null; }
    }

    private String sanitizeFileName(String candidateName){String sanitized=Objects.toString(candidateName,"Candidate").replaceAll("[^A-Za-z0-9]","_").replaceAll("_+","_").replaceAll("^_+|_+$","");return sanitized.isBlank()?"Candidate":sanitized;}
    private String rootCauseMessage(Throwable error){Throwable cause=error;while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();return cause.getClass().getName()+": "+Objects.toString(cause.getMessage(),"No message");}

    private CandidateInput normalized(CandidateInput c){ if(c==null)return null; Double lpa=c.annualPackageLPA();Long amount=c.annualSalaryAmount();if(amount==null&&lpa!=null)amount=Math.round(lpa*100000d);if(lpa==null&&amount!=null)lpa=amount/100000d;return new CandidateInput(trim(c.candidateName()),lower(c.candidateEmail()),trim(c.jobTitle()),trim(c.department()),lpa,amount,trim(c.joiningDate()),trim(c.reportingManagerName()),trim(c.hiringManagerName()),trim(c.location()),trim(c.employmentType())); }
    private void putCandidate(Map<String,Object> m,CandidateInput c){m.put("candidateName",c.candidateName());m.put("candidateEmail",c.candidateEmail());m.put("jobTitle",c.jobTitle());m.put("department",c.department());m.put("annualPackageLPA",c.annualPackageLPA());m.put("annualSalaryAmount",c.annualSalaryAmount());m.put("joiningDate",c.joiningDate());m.put("reportingManagerName",c.reportingManagerName());m.put("hiringManagerName",c.hiringManagerName());m.put("location",c.location());m.put("employmentType",c.employmentType());}
    private Map<String,Object> findManager(Firestore db,String name)throws Exception{
        if(!StringUtils.hasText(name)) return null;
        for(String field:List.of("displayName","name")){
            for(QueryDocumentSnapshot d:db.collection("users")
                    .whereEqualTo("role","HIRING_MANAGER").whereEqualTo(field,name.trim()).get().get().getDocuments()){
                if(!Boolean.TRUE.equals(d.getBoolean("active"))) continue;
                String managerUid=d.getString("uid");
                if(!StringUtils.hasText(managerUid)) throw unprocessable("Selected hiring manager has no uid field.");
                Map<String,Object> managerData=new HashMap<>(d.getData());
                managerData.put("documentId",d.getId());
                logger.info("Selected manager uid={}",managerUid);
                return managerData;
            }
        }
        return null;
    }
    private void logManagerAssignment(String selectedManagerName,Map<String,Object> managerData,String savedHiringManagerId){
        Map<String,Object> details=new LinkedHashMap<>();
        details.put("selectedManagerName",selectedManagerName);
        details.put("managerDocumentId",managerData.get("documentId"));
        details.put("managerUid",managerData.get("uid"));
        details.put("savedHiringManagerId",savedHiringManagerId);
        logger.info("Hiring manager assignment {}",details);
    }
    private List<QueryDocumentSnapshot> pendingForRole(Firestore db,String pendingStatus,String approverRole)throws Exception{
        Map<String,QueryDocumentSnapshot> matches=new LinkedHashMap<>();
        for(QueryDocumentSnapshot d:db.collection("hiringRequests").whereEqualTo("status",pendingStatus).get().get().getDocuments()) matches.put(d.getId(),d);
        for(QueryDocumentSnapshot d:db.collection("hiringRequests").whereEqualTo("currentApproverRole",approverRole).get().get().getDocuments()) matches.put(d.getId(),d);
        return new ArrayList<>(matches.values());
    }
    private void requireCurrentApprover(DocumentSnapshot request,String approverRole,Map<String,Object> actor){
        String status=request.getString("status"); String currentRole=request.getString("currentApproverRole");
        if("HIRING_MANAGER".equals(approverRole)){
            if(!"PENDING_MANAGER_APPROVAL".equals(status) || !Objects.equals(request.getString("hiringManagerId"),actor.get("uid")))
                throw forbidden("Only the assigned hiring manager may decide this request.");
            return;
        }
        String expectedStatus=pendingStatus(approverRole);
        if(!expectedStatus.equals(status) && !approverRole.equals(currentRole))
            throw forbidden("This request is not awaiting " + approverRole + " approval.");
    }
    private ApprovalTransition approvalTransition(DocumentSnapshot request,String approverRole,String action,String reason){
        List<String> route=strings(request.get("approvalRoute"));if(route.isEmpty())route=List.of("HIRING_MANAGER","FINANCE","LEGAL");
        int current=Optional.ofNullable(request.getLong("currentApprovalIndex")).orElse((long)Math.max(0,route.indexOf(approverRole))).intValue();
        if(current>=route.size()||!approverRole.equals(route.get(current)))throw forbidden("Approval route is not awaiting "+approverRole+".");
        if("APPROVE".equals(action)){
            int next=current+1;String nextRole=next<route.size()?route.get(next):null;boolean complete=nextRole==null;
            String actionName="HIRING_MANAGER".equals(approverRole)?"MANAGER_APPROVED":approverRole+"_APPROVED";
            String details=("HIRING_MANAGER".equals(approverRole)?"Hiring manager":title(approverRole))+" approved the request."+(complete?" All required approvals are complete.":" Routed to "+title(nextRole)+".");
            return new ApprovalTransition(complete?"APPROVALS_COMPLETED":pendingStatus(nextRole),actionName,details,nextRole,next,complete);
        }
        String suffix="REJECT".equals(action)?"REJECTED":"CHANGES_REQUESTED";
        String next="REJECT".equals(action)?"REJECTED":"CHANGES_REQUESTED";
        return new ApprovalTransition(next,approverRole+"_"+suffix,reason,null,current,false);
    }
    private void applyApprovalFields(Map<String,Object> updates,String approverRole,Map<String,Object> actor,String nextApproverRole){
        String prefix=approvalPrefix(approverRole);
        updates.put(prefix+"ApprovalStatus","APPROVED");
        updates.put(prefix+"ApprovedAt",FieldValue.serverTimestamp());
        updates.put(prefix+"ApprovedBy",actor.get("uid"));
        updates.put(prefix+"ApprovedByName",actor.get("name"));
        updates.put("currentApproverRole",nextApproverRole==null?FieldValue.delete():nextApproverRole);
        if(nextApproverRole!=null)updates.put(approvalPrefix(nextApproverRole)+"ApprovalStatus","PENDING");
    }
    private String approvalPrefix(String role){return switch(role){case "HIRING_MANAGER"->"manager";case "FINANCE"->"finance";case "LEGAL"->"legal";case "CEO"->"ceo";default->throw forbidden("Unsupported approver role.");};}
    private String pendingStatus(String role){return "PENDING_"+("HIRING_MANAGER".equals(role)?"MANAGER":role)+"_APPROVAL";}
    private String title(String role){if(role==null)return "";return Arrays.stream(role.split("_")).map(v->v.substring(0,1)+v.substring(1).toLowerCase(Locale.ROOT)).reduce((a,b)->a+" "+b).orElse(role);}
    private record ApprovalTransition(String nextStatus,String activityAction,String details,String nextApproverRole,int nextApprovalIndex,boolean finalApproval){}
    private Map<String,Object> candidateMap(CandidateInput c){Map<String,Object>m=new LinkedHashMap<>();if(c==null)return m;putCandidate(m,c);return m;}
    private CandidateInput candidateFromDocument(DocumentSnapshot d){return new CandidateInput(d.getString("candidateName"),d.getString("candidateEmail"),d.getString("jobTitle"),d.getString("department"),
            d.getDouble("annualPackageLPA"),d.getLong("annualSalaryAmount"),d.getString("joiningDate"),d.getString("reportingManagerName"),d.getString("hiringManagerName"),d.getString("location"),d.getString("employmentType"));}
    private List<Map<String,Object>> fieldChanges(CandidateInput before,CandidateInput after,Map<String,Object> actor,Timestamp at){
        if(before==null||after==null)return List.of();Map<String,Object>a=candidateMap(before),b=candidateMap(after);List<Map<String,Object>>changes=new ArrayList<>();
        for(String field:List.of("annualPackageLPA","annualSalaryAmount","hiringManagerName","department","joiningDate"))if(!Objects.equals(a.get(field),b.get(field))){
            Map<String,Object>change=new LinkedHashMap<>();change.put("field",field);change.put("oldValue",a.get(field));change.put("newValue",b.get(field));change.put("changedBy",actor.get("uid"));change.put("changedByName",actor.get("name"));change.put("changedAt",at);changes.add(change);}
        return changes;
    }
    private List<String> strings(Object value){if(!(value instanceof List<?> list))return List.of();return list.stream().map(String::valueOf).toList();}
    private Map<String,Object> audit(String requestId,String action,Map<String,Object>actor,String details){Map<String,Object>m=new HashMap<>();m.put("requestId",requestId);m.put("action",action);m.put("performedBy",actor.get("uid"));m.put("performedByName",actor.get("name"));m.put("actor",actor);m.put("details",Objects.toString(details,""));m.put("timestamp",Timestamp.now());return m;}
    private void writeAudit(Firestore db,String requestId,String action,Map<String,Object>actor,String details)throws Exception{db.collection("auditLogs").document(requestId+"-"+action+"-"+UUID.randomUUID()).create(audit(requestId,action,actor,details)).get();}
    private Map<String,Object> systemActor(){return Map.of("uid","SYSTEM","name","NovaOS Automation","email","system@novaos.local","role","SYSTEM");}
    private String approvalSummary(DocumentSnapshot d){List<String>route=strings(d.get("approvalRoute"));List<String>approved=new ArrayList<>();for(String r:route)if("APPROVED".equals(d.getString(approvalPrefix(r)+"ApprovalStatus")))approved.add(title(r));return String.join(" -> ",approved);}
    private String safeMessage(Throwable error){String message=error==null?"Unknown backend failure":Objects.toString(error.getMessage(),error.getClass().getSimpleName());return message.length()>300?message.substring(0,300):message;}
    private Map<String,Object> actor(Firestore db,Authentication a)throws Exception{DocumentSnapshot d=db.collection("users").document(a.getName()).get().get();return Map.of("uid",a.getName(),"name",Objects.toString(d.get("displayName"),Objects.toString(d.get("name"),d.getString("email"))),"email",Objects.toString(d.getString("email"),""),"role",role(a));}
    private Map<String,Object> activity(String action,Map<String,Object>a,String details,String requestId){return activityAt(action,a,details,Timestamp.now(),requestId);}
    private Map<String,Object> activity(String action,Map<String,Object>a,String details){return activityAt(action,a,details,Timestamp.now(),"");}
    private Map<String,Object> activityAt(String action,Map<String,Object>a,String details,Timestamp timestamp,String requestId){
        Map<String,Object> m=new HashMap<>();
        m.put("action",action);
        m.put("eventType",action);
        m.put("performedBy",a.get("uid"));
        m.put("performedByName",a.get("name"));
        m.put("actorName",a.get("name"));
        m.put("actorEmail",Objects.toString(a.get("email"),""));
        m.put("actorRole",Objects.toString(a.get("role"),""));
        m.put("timestamp",timestamp);
        m.put("details",details==null?"":details);
        m.put("message",details==null?"":details);
        m.put("requestId",requestId);
        return m;
    }
    private Map<String,Object> activityAt(String action,Map<String,Object>a,String details,Timestamp timestamp){
        return activityAt(action,a,details,timestamp,"");
    }
    private String emailBody(DocumentSnapshot d){return "Dear "+d.getString("candidateName")+",\n\nCongratulations! Your offer has completed all required approvals.\n\nPlease find your official offer letter attached to this email. Kindly review the document carefully.\n\nRegards,\nNovaOS HR Team";}
    private void authorizeRead(DocumentSnapshot d,Authentication a){String r=role(a);if("HIRING_MANAGER".equals(r)&&!Objects.equals(d.getString("hiringManagerId"),a.getName()))throw forbidden("This request is not assigned to you.");if("FINANCE".equals(r)&&!"FINANCE".equals(d.getString("currentApproverRole"))&&!Objects.equals(d.getString("financeApprovedBy"),a.getName()))throw forbidden("This request is not assigned to Finance.");if("LEGAL".equals(r)&&!"LEGAL".equals(d.getString("currentApproverRole"))&&!Objects.equals(d.getString("legalApprovedBy"),a.getName()))throw forbidden("This request is not assigned to Legal.");if("CEO".equals(r)&&!"CEO".equals(d.getString("currentApproverRole"))&&!Objects.equals(d.getString("ceoApprovedBy"),a.getName()))throw forbidden("This request is not assigned to the CEO.");}
    private String trim(String s){return s==null||s.isBlank()?null:s.trim();}private String lower(String s){String v=trim(s);return v==null?null:v.toLowerCase(Locale.ROOT);}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}private ResponseStatusException unprocessable(String m){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,m);}private ResponseStatusException forbidden(String m){return new ResponseStatusException(HttpStatus.FORBIDDEN,m);}private ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}private ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);}private ResponseStatusException server(String m,Throwable e){return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,m,e);}

    private Firestore db() { return FirestoreClient.getFirestore(); }
    private DocumentSnapshot requireDocument(Firestore db, String id) throws Exception {
        DocumentSnapshot d = db.collection("hiringRequests").document(id).get().get();
        if (!d.exists()) throw notFound("Hiring request not found: " + id);
        return d;
    }
    private String role(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return "";
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("");
    }
    private void requireRole(Authentication auth, String role) {
        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_" + role))) {
            throw forbidden("You are not authorized for this action.");
        }
    }
    private void requireAny(Authentication auth, String... roles) {
        if (auth == null || roles == null || Arrays.stream(roles)
                .noneMatch(r -> auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_" + r)))) {
            throw forbidden("You are not authorized for this action.");
        }
    }

    private void writeNotification(Firestore db, String id, String targetRole, String title, String message) {
        try {
            Map<String, Object> n = new HashMap<>();
            n.put("requestId", id);
            n.put("targetRole", targetRole);
            n.put("title", title);
            n.put("message", message);
            n.put("read", false);
            n.put("timestamp", java.time.Instant.now().toString());
            db.collection("notifications").document(id + "-" + targetRole + "-" + UUID.randomUUID().toString().substring(0,8)).set(n).get();
        } catch (Exception e) {
            logger.error("Failed to write notification: {}", e.getMessage());
        }
    }
}
