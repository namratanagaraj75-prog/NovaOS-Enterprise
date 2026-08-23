import apiClient from './api';
import { TimestampInput } from '../lib/dateUtils';

export type HiringStatus = 'DRAFT' | 'PENDING_MANAGER_APPROVAL' | 'PENDING_FINANCE_APPROVAL' |
  'PENDING_LEGAL_APPROVAL' | 'PENDING_CEO_APPROVAL' | 'MANAGER_APPROVED' | 'FINANCE_APPROVED' |
  'LEGAL_APPROVED' | 'CEO_APPROVED' | 'APPROVALS_COMPLETED' | 'GENERATING_OFFER' |
  'OFFER_GENERATED' | 'EMAIL_SENDING' | 'EMAIL_SENT' | 'WORKFLOW_COMPLETED' | 'APPROVED' |
  'REJECTED' | 'CHANGES_REQUESTED' | 'PDF_GENERATED' | 'EMAIL_FAILED' | 'EMPLOYEE_CREATED' | 'AI_SCREENING';

export interface HiringCandidateInput {
  candidateName: string; candidateEmail: string; jobTitle: string; department: string;
  annualPackageLPA: number | null; annualSalaryAmount: number | null; joiningDate: string;
  reportingManagerName: string; hiringManagerName: string; location: string; employmentType: string;
}
export interface HiringParseResult { intent: string; candidate: HiringCandidateInput; missingFields: string[]; followUpQuestion?: string; confidence: number; }
export interface ActivityEntry { action: string; performedBy: string; performedByName: string; timestamp: TimestampInput; details: string; }
export interface HiringRequest extends HiringCandidateInput {
  id: string; status: HiringStatus; createdBy: string; createdByName: string; createdAt: TimestampInput; updatedAt: TimestampInput;
  hiringManagerId: string; currentApproverRole?: 'HIRING_MANAGER' | 'FINANCE' | 'LEGAL' | 'CEO';
  managerApprovalStatus?: string; managerApprovedBy?: string; managerApprovedByName?: string; managerApprovedAt?: TimestampInput;
  managerApprovalComment?: string; managerApprovedByEmail?: string;
  financeApprovalStatus?: string; financeApprovedBy?: string; financeApprovedByName?: string; financeApprovedAt?: TimestampInput;
  financeApprovalComment?: string; financeApprovedByEmail?: string;
  legalApprovalStatus?: string; legalApprovedBy?: string; legalApprovedByName?: string; legalApprovedAt?: TimestampInput;
  legalApprovalComment?: string; legalApprovedByEmail?: string;
  ceoApprovalStatus?: string; ceoApprovedBy?: string; ceoApprovedByName?: string; ceoApprovedAt?: TimestampInput;
  emailErrorMessage?: string;
  decision?: 'PASS'|'WARNING'|'CONDITIONAL'|'BLOCKED'; riskLevel?: 'LOW'|'MEDIUM'|'HIGH'; riskScore?: number;
  decisionPassport?: any; policyChecks?: any[]; approvalRoute?: string[]; currentApprovalIndex?: number;
  fieldChangeHistory?: any[]; offerLetterStatus?: string; offerLetterUrl?: string; offerLetterGeneratedAt?: TimestampInput;
  emailRecipient?: string; emailRetryCount?: number; emailFailureReason?: string;
  emailAttemptCount?: number; lastEmailAttemptAt?: TimestampInput; emailErrorCode?: string;
  approvedByName?: string; approvedAt?: TimestampInput; rejectionReason?: string;
  pdfUrl?: string; pdfFileName?: string; pdfGeneratedAt?: TimestampInput; emailStatus: string; emailSentAt?: TimestampInput;
  emailMessageId?: string; emailError?: string; activityHistory: ActivityEntry[];
  readBy?: string[];
  rejected?: boolean; rejectedByDepartment?: string; rejectedByName?: string; rejectedAt?: TimestampInput;
  internalRejectionReason?: string; workflowTerminated?: boolean;
  candidateEmailNotification?: { status: 'PENDING'|'SENDING'|'SENT'|'FAILED'; provider: string; sentAt?: TimestampInput; lastAttemptAt?: TimestampInput; messageId?: string; lastError?: string; attemptCount?: number };
  rejectionLetterStatus?: 'GENERATING'|'GENERATED'|'FAILED'; rejectionPdfUrl?:string; rejectionPdfFileName?:string; rejectionPdfGeneratedAt?:TimestampInput;
  rejectionEmailStatus?:'PENDING'|'SENDING'|'SENT'|'FAILED'; rejectionEmailSentAt?:TimestampInput;
  finalApprovedAt?: TimestampInput;
}

export const emptyCandidate = (): HiringCandidateInput => ({ candidateName:'',candidateEmail:'',jobTitle:'',department:'',
  annualPackageLPA:null,annualSalaryAmount:null,joiningDate:'',reportingManagerName:'',hiringManagerName:'',location:'',employmentType:'' });
export const parseHiring = async (instruction:string):Promise<HiringParseResult> => (await apiClient.post('/hiring/requests/parse',{instruction})).data;
export const createHiring = async (candidate:HiringCandidateInput,originalInstruction:string,aiExtractedCandidate?:HiringCandidateInput):Promise<HiringRequest> => (await apiClient.post('/hiring/requests',{candidate,originalInstruction,aiExtractedCandidate})).data;
export const listHiring = async ():Promise<HiringRequest[]> => (await apiClient.get('/hiring/requests')).data;
export const getHiring = async (id:string):Promise<HiringRequest> => (await apiClient.get('/hiring/requests/'+id)).data;
export const updateHiring = async (id:string,candidate:HiringCandidateInput):Promise<HiringRequest> => (await apiClient.put('/hiring/requests/'+id,{candidate})).data;
export const submitHiring = async (id:string):Promise<HiringRequest> => (await apiClient.post('/hiring/requests/'+id+'/submit')).data;
export const decideHiring = async (id:string,action:string,reason=''):Promise<HiringRequest> => (await apiClient.post('/hiring/requests/'+id+'/decision',{action,reason})).data;
export const rejectHiring = async (id:string,internalRejectionReason:string,candidateRejectionMessage?:string):Promise<HiringRequest> => (await apiClient.post('/hiring/requests/'+id+'/reject',{internalRejectionReason,candidateRejectionMessage})).data;
export const retryRejectionEmail = async (id:string):Promise<HiringRequest> => (await apiClient.post('/hiring/requests/'+id+'/rejection-email/retry')).data;
export const sendHiringEmail = async (id:string,resendConfirmed=false):Promise<HiringRequest> => (await apiClient.post('/hiring/requests/'+id+'/email',{resendConfirmed})).data;
export const fetchHiringPdf = async (id:string) => (await apiClient.get('/hiring/requests/'+id+'/pdf',{responseType:'blob'})).data as Blob;
export const fetchRejectionPdf = async (id:string) => (await apiClient.get('/hiring/requests/'+id+'/rejection-letter',{responseType:'blob'})).data as Blob;
