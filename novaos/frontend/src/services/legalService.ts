import apiClient from './api';
import { HiringRequest } from './hiringRequestService';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
export interface LegalPolicy { id?:string; policyId:string; title:string; description:string; category:string; severity:'LOW'|'MEDIUM'|'HIGH'|'CRITICAL'; active:boolean; mandatory:boolean; policyType:string; version:string; }
export interface PolicyRisk { policyId:string; title:string; currentValue:string; policyValue:string; riskLevel:RiskLevel; explanation:string; }
export interface LegalReview { id:string; candidateId:string; overallRisk:RiskLevel; policyResults:PolicyRisk[]; summary:Record<RiskLevel|'TOTAL',number>; candidateSummary:Record<string,unknown>; advisoryOnly:boolean; backendUnavailable?:boolean; }

const fallbackRisks:PolicyRisk[] = [
  ['SALARY_RANGE','Salary Range Policy','Unavailable','₹6 LPA - ₹10 LPA'],
  ['NOTICE_PERIOD','Notice Period Policy','Unavailable','30 - 60 days'],
  ['PROBATION_PERIOD','Probation Period Policy','Unavailable','3 - 6 months'],
  ['EMPLOYMENT_TYPE','Employment Type Policy','Unavailable','Approved opening type'],
  ['OFFER_COMPLETENESS','Offer Information Completeness','Unavailable','Required offer fields'],
  ['BACKGROUND_VERIFICATION','Background Verification','Unavailable','Verification status'],
  ['DOCUMENT_STATUS','Document Status','Unavailable','Required documents completed'],
].map(([policyId,title,currentValue,policyValue])=>({policyId,title,currentValue,policyValue,riskLevel:'MEDIUM',explanation:'Restart the updated backend to calculate this advisory risk.'} as PolicyRisk));

export const listLegalPolicies = async (includeInactive=false):Promise<LegalPolicy[]> => (await apiClient.get('/legal/policies',{params:{includeInactive}})).data;
export const createLegalPolicy = async (policy:Partial<LegalPolicy>):Promise<LegalPolicy> => (await apiClient.post('/legal/policies',policy)).data;
export const updateLegalPolicy = async (id:string,policy:Partial<LegalPolicy>):Promise<LegalPolicy> => (await apiClient.put('/legal/policies/'+id,policy)).data;
export const getLegalReview = async (id:string):Promise<LegalReview> => {
  try { return (await apiClient.get('/legal/reviews/'+id)).data; }
  catch (error:any) {
    if (error.response?.status !== 404) throw error;
    return {id,candidateId:id,overallRisk:'MEDIUM',policyResults:fallbackRisks,summary:{LOW:0,MEDIUM:7,HIGH:0,TOTAL:7},candidateSummary:{},advisoryOnly:true,backendUnavailable:true};
  }
};
export const approveLegalReview = async (id:string,reason=''):Promise<HiringRequest> => (await apiClient.post('/legal/reviews/'+id+'/approve',{action:'APPROVE',reason})).data;
export const rejectLegalReview = async (id:string,internalRejectionReason:string):Promise<HiringRequest> => (await apiClient.post('/legal/reviews/'+id+'/reject',{internalRejectionReason})).data;
