import apiClient from "./api";

export interface ParsedCandidateData {
  id?: string;
  name: string;
  role: string;
  email: string;
  status?: string;
  matchScore: number;
  score?: number;
  source: string;
  aiSummary?: string;
  resumeSummary?: string;
  ctc?: string;
  joiningDate?: string;
  department?: string;
  strengths?: string[];
  weaknesses?: string[];
}

export interface ChatResponseData {
  responseText: string;
  executionSteps: string[];
  success: boolean;
  intent?: "hire" | "vet" | "chat";
  liveModel?: boolean;
  workflowId?: string;
  parsedCandidate?: ParsedCandidateData;
}

const API_BASE = "";

export const sendPrompt = async (
  message: string,
): Promise<ChatResponseData> => {
  const response = await apiClient.post(`${API_BASE}/chat`, {
    message,
    contextSessionId: "session-copilot-user",
  });
  return response.data;
};

export const confirmCandidate = async (
  candidate: ParsedCandidateData,
): Promise<boolean> => {
  const response = await apiClient.post(`${API_BASE}/hire`, {
    name: candidate.name,
    role: candidate.role,
    email: candidate.email,
    status: "Applied",
    matchScore: candidate.matchScore || 85,
    source: candidate.source || "AI Command Center Sourced",
    aiSummary:
      candidate.aiSummary || candidate.resumeSummary || "Vetted details.",
  });
  return response.status === 200 || response.status === 201;
};

export default { sendPrompt, confirmCandidate };



