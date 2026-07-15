import apiClient from './api';

export interface HiringCandidate {
  name: string;
  email: string;
  position: string;
  annualCtc: number | null;
  joiningDate: string;
  department: string;
  location: string;
  manager: string;
  probationMonths: number | null;
  requiredSkills: string[];
}
export interface HiringParseResponse {
  intent: 'HIRE_CANDIDATE';
  candidate: HiringCandidate;
  missingFields: string[];
  confidence: number;
}
export interface PolicyCheck {
  name: string;
  status: 'PASS' | 'WARNING' | 'FAIL';
  reason: string;
  evidence: Record<string, unknown>;
}
export const emptyHiringCandidate = (): HiringCandidate => ({
  name: '', email: '', position: '', annualCtc: null, joiningDate: '', department: '',
  location: '', manager: '', probationMonths: null, requiredSkills: [],
});

export const parseInstruction = async (requestId: string, instruction: string): Promise<HiringParseResponse> =>
  (await apiClient.post('/hiring/passports/parse', { requestId, instruction })).data;

export const confirmHiring = async (payload: {
  requestId: string; originalInstruction: string; candidate: HiringCandidate; confidence: number;
}) => (await apiClient.post('/hiring/passports/confirm', payload)).data;

export const approveHiring = async (requestId: string, comment: string) =>
  (await apiClient.post('/hiring/passports/' + requestId + '/approve', { comment })).data;

export const sendOffer = async (requestId: string, resend = false) =>
  (await apiClient.post('/hiring/passports/' + requestId + '/offer/send?resend=' + resend)).data;

export const retryDocument = async (requestId: string) =>
  (await apiClient.post('/hiring/passports/' + requestId + '/offer/generate')).data;

export const previewWhatIf = async (requestId: string, annualCtc: number | null, joiningDate: string) =>
  (await apiClient.post('/hiring/passports/' + requestId + '/what-if', { annualCtc, joiningDate })).data;

export const getPassport = async (requestId: string) =>
  (await apiClient.get('/hiring/passports/' + requestId)).data;
