import apiClient from './api';
import { Candidate } from './recruitmentService';

export interface SkillScore { name: string; score: number }
export interface CandidateAnalysis {
  id: string; name: string; role: string; email: string; overallScore: number; skills: SkillScore[];
  strengths: string[]; weaknesses: string[]; recommendation: string; resumeSummary: string;
  recommendedSalary: string; recommendedInterviewer: string; source: string; status: string;
}

const fromStoredCandidate = (candidate: Candidate): CandidateAnalysis => ({
  id: candidate.id,
  name: candidate.name,
  role: candidate.role,
  email: candidate.email,
  overallScore: Number(candidate.matchScore || 0),
  skills: [],
  strengths: candidate.strengths || [],
  weaknesses: candidate.weaknesses || [],
  recommendation: candidate.aiSummary || '',
  resumeSummary: candidate.resumeSummary || '',
  recommendedSalary: candidate.recommendedSalary || '',
  recommendedInterviewer: candidate.recommendedInterviewer || '',
  source: candidate.source || '',
  status: candidate.status,
});

export const getCandidateAnalysis = async (candidateId: string, candidateData?: Candidate): Promise<CandidateAnalysis> => {
  if (candidateData) return fromStoredCandidate(candidateData);
  const response = await apiClient.get('/candidates');
  const match = response.data.find((candidate: any) => String(candidate.id) === candidateId);
  if (!match) throw new Error('Candidate not found in Firestore.');
  return fromStoredCandidate({
    id: String(match.id),
    name: String(match.name || ''),
    role: String(match.role || match.position || ''),
    email: String(match.email || ''),
    status: match.status as Candidate['status'],
    matchScore: Number(match.matchScore || match.score || 0),
    source: String(match.source || ''),
    aiSummary: match.aiSummary ? String(match.aiSummary) : undefined,
    resumeSummary: String(match.resumeSummary || match.aiSummary || ''),
    strengths: Array.isArray(match.strengths) ? match.strengths : [],
    weaknesses: Array.isArray(match.weaknesses) ? match.weaknesses : [],
    recommendedSalary: match.annualCtc ? 'INR ' + Number(match.annualCtc).toLocaleString('en-IN') : '',
    recommendedInterviewer: String(match.manager || ''),
  });
};

export const uploadResume = async (): Promise<string> => {
  throw new Error('Resume upload is disabled because no governed backend parser is configured for that action.');
};

export default { getCandidateAnalysis, uploadResume };
