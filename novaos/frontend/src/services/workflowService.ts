import apiClient from './api';

export interface WorkflowStep {
  id: string;
  title: string;
  icon?: string;
  status: 'completed' | 'running' | 'pending' | 'failed';
  time: string;
  description?: string;
}

export interface WorkflowLog {
  time: string;
  message: string;
  type?: string;
}

export interface WorkflowState {
  steps: WorkflowStep[];
  currentStep: string;
  logs: WorkflowLog[];
  status: 'Idle' | 'Running' | 'Paused' | 'Completed';
  candidateName: string;
  candidateRole?: string;
  progress: number;
}

const API_BASE = '/workflow';

export const getWorkflow = async (): Promise<WorkflowState> => {
  const res = await apiClient.get(API_BASE);
  return res.data;
};

export const startWorkflow = async (name: string): Promise<WorkflowState> => {
  const res = await apiClient.post(`${API_BASE}/start?name=${encodeURIComponent(name)}`);
  return res.data;
};

export const startHiringWorkflow = async (prompt: string, workflowId: string): Promise<{ status: string; workflowId: string }> => {
  const res = await apiClient.post(`${API_BASE}/hiring`, { prompt, workflowId });
  return res.data;
};

export const advanceWorkflow = async (): Promise<WorkflowState> => {
  const res = await apiClient.post(`${API_BASE}/approve`);
  return res.data;
};

export const approveWorkflow = advanceWorkflow;

export const rejectWorkflow = async (): Promise<WorkflowState> => {
  const res = await apiClient.post(`${API_BASE}/reject`);
  return res.data;
};

export default {
  getWorkflow,
  startWorkflow,
  startHiringWorkflow,
  advanceWorkflow,
  approveWorkflow,
  rejectWorkflow
};
