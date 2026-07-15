import apiClient from './api';

export type HiringStatus = 'DRAFT' | 'PENDING_MANAGER_APPROVAL' | 'PENDING_FINANCE_APPROVAL' |
  'PENDING_LEGAL_APPROVAL' | 'PENDING_CEO_APPROVAL' | 'MANAGER_APPROVED' | 'FINANCE_APPROVED' |
  'LEGAL_APPROVED' | 'CEO_APPROVED' | 'APPROVALS_COMPLETED' | 'GENERATING_OFFER' |
  'OFFER_GENERATED' | 'EMAIL_SENDING' | 'EMAIL_SENT' | 'WORKFLOW_COMPLETED' | 'APPROVED' |
  'REJECTED' | 'CHANGES_REQUESTED' | 'PDF_GENERATED' | 'EMAIL_FAILED';

export interface HiringCandidateInput {
  candidateName: string; candidateEmail: string; jobTitle: string; department: string;
  annualPackageLPA: number | null; annualSalaryAmount: number | null; joiningDate: string;
  reportingManagerName: string; hiringManagerName: string; location: string; employmentType: string;
}
export interface HiringParseResult { intent: string; candidate: HiringCandidateInput; missingFields: string[]; followUpQuestion?: string; confidence: number; }
export interface ActivityEntry { action: string; performedBy: string; performedByName: string; timestamp: any; details: string; }
export interface HiringRequest extends HiringCandidateInput {
  id: string; status: HiringStatus; createdBy: string; createdByName: string; createdAt: any; updatedAt: any;
  hiringManagerId: string; currentApproverRole?: 'HIRING_MANAGER' | 'FINANCE' | 'LEGAL' | 'CEO';
  managerApprovalStatus?: string; managerApprovedBy?: string; managerApprovedByName?: string; managerApprovedAt?: any;
  financeApprovalStatus?: string; financeApprovedBy?: string; financeApprovedByName?: string; financeApprovedAt?: any;
  legalApprovalStatus?: string; legalApprovedBy?: string; legalApprovedByName?: string; legalApprovedAt?: any;
  ceoApprovalStatus?: string; ceoApprovedBy?: string; ceoApprovedByName?: string; ceoApprovedAt?: any;
  decision?: 'PASS'|'WARNING'|'CONDITIONAL'|'BLOCKED'; riskLevel?: 'LOW'|'MEDIUM'|'HIGH'; riskScore?: number;
  decisionPassport?: any; policyChecks?: any[]; approvalRoute?: string[]; currentApprovalIndex?: number;
  fieldChangeHistory?: any[]; offerLetterStatus?: string; offerLetterUrl?: string; offerLetterGeneratedAt?: any;
  emailRecipient?: string; emailRetryCount?: number; emailFailureReason?: string;
  approvedByName?: string; approvedAt?: any; rejectionReason?: string;
  pdfUrl?: string; pdfFileName?: string; pdfGeneratedAt?: any; emailStatus: string; emailSentAt?: any;
  emailMessageId?: string; emailError?: string; activityHistory: ActivityEntry[];
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
export const sendHiringEmail = async (id:string,resendConfirmed=false):Promise<HiringRequest> => (await apiClient.post('/hiring/requests/'+id+'/email',{resendConfirmed})).data;
export const fetchHiringPdf = async (id:string) => (await apiClient.get('/hiring/requests/'+id+'/pdf',{responseType:'blob'})).data as Blob;
