import apiClient from "./api";

export interface Candidate {
  id: string;
  name: string;
  role: string;
  email: string;
  status:
    | "Applied"
    | "AI Screening"
    | "Manager Review"
    | "Finance Review"
    | "Legal Review"
    | "Offer Generated"
    | "Offer Sent"
    | "Employee Created"
    | "Rejected";
  matchScore: number;
  score?: number;
  source: string;
  aiSummary?: string;
  resumeSummary: string;
  strengths: string[];
  weaknesses: string[];
  recommendedSalary: string;
  recommendedInterviewer: string;
  department?: string;
  riskLevel?: string;
  appliedAt?: any;
  currentStatus?: string;
  annualPackageLPA?: number | null;
  annualSalaryAmount?: number | null;
  joiningDate?: string;
  location?: string;
  employmentType?: string;
  experience?: string;
  skills?: string[];
  hiringRequest?: any;
}

const API_BASE = "";

const normalizeCandidate = (c: any): Candidate => {
  const matchScore = c.matchScore || c.score || 0;
  return {
    id: String(c.id),
    name: c.name,
    role: c.role,
    email: c.email,
    status: c.status as Candidate["status"],
    matchScore,
    score: matchScore,
    source: c.source || "No Data Yet",
    aiSummary: c.aiSummary || c.resumeSummary || "No Data Yet",
    resumeSummary: c.resumeSummary || c.aiSummary || "No Data Yet",
    strengths: c.strengths || [],
    weaknesses: c.weaknesses || [],
    recommendedSalary: c.recommendedSalary || c.ctc || "No Data Yet",
    recommendedInterviewer: c.recommendedInterviewer || "No Data Yet",
    department: c.department || "",
    riskLevel: c.riskLevel || "",
    appliedAt: c.appliedAt || c.createdAt,
    currentStatus: c.currentStatus || c.status,
    annualPackageLPA: c.annualPackageLPA ?? null,
    annualSalaryAmount: c.annualSalaryAmount ?? null,
    joiningDate: c.joiningDate || "",
    location: c.location || "",
    employmentType: c.employmentType || "",
    experience: c.experience || "",
    skills: Array.isArray(c.skills) ? c.skills : [],
    hiringRequest: c.hiringRequest,
  };
};

export const getCandidates = async (): Promise<Candidate[]> => {
  const response = await apiClient.get(`${API_BASE}/candidates`);
  return response.data.map(normalizeCandidate);
};

export const approveCandidate = async (
  candidateId: string,
): Promise<Candidate> => {
  const candidates = await getCandidates();
  const candidate = candidates.find((c) => c.id === candidateId);
  if (!candidate) throw new Error("Candidate not found: " + candidateId);

  const stages: Candidate["status"][] = [
    "Applied",
    "AI Screening",
    "Manager Review",
    "Finance Review",
    "Legal Review",
    "Offer Generated",
    "Offer Sent",
    "Employee Created",
  ];
  const currentIdx = stages.indexOf(candidate.status);
  if (currentIdx !== -1 && currentIdx < stages.length - 1) {
    return moveCandidate(candidateId, stages[currentIdx + 1]);
  }
  return candidate;
};

export const rejectCandidate = async (
  candidateId: string,
): Promise<Candidate> => {
  return moveCandidate(candidateId, "Rejected");
};

export const moveCandidate = async (
  candidateId: string,
  targetColumn: Candidate["status"] | "Rejected",
): Promise<Candidate> => {
  if (targetColumn === "Employee Created") {
    await apiClient.post(`${API_BASE}/approve`, {
      candidateId,
      comments: "Candidate approved and provisioned as Employee.",
    });
    const candidates = await getCandidates();
    const updated = candidates.find((c) => c.id === candidateId);
    if (updated) return updated;
    throw new Error("Candidate status promotion failed.");
  }

  const response = await apiClient.put(
    `${API_BASE}/candidates/${candidateId}/status?status=${encodeURIComponent(targetColumn)}`,
  );
  return normalizeCandidate(response.data);
};

export interface OfferLetterRequest {
  name: string;
  email: string;
  role: string;
  salary: string;
  joiningDate: string;
  branch: string;
  location: string;
  managerName: string;
  hrName: string;
}

export const addCandidate = async (
  candidate: Omit<Candidate, "id">,
): Promise<Candidate> => {
  const response = await apiClient.post(`${API_BASE}/hire`, candidate);
  return normalizeCandidate(response.data);
};

export const sendOfferLetter = async (
  request: OfferLetterRequest,
): Promise<any> => {
  const response = await apiClient.post(`${API_BASE}/offer-letter`, request);
  return response.data;
};

export default {
  getCandidates,
  approveCandidate,
  rejectCandidate,
  moveCandidate,
  addCandidate,
  sendOfferLetter,
};
