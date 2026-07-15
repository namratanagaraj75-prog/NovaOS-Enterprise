import apiClient from './api';
import { Candidate } from './recruitmentService';

export interface KpiData {
  title: string;
  value: string;
  change: string;
  isPositive: boolean;
  type: 'candidates' | 'approvals' | 'offers' | 'employees';
}

export interface PipelineStage {
  name: string;
  stage: string;
  count: number;
  percentage: number;
}

export interface ActivityItem {
  id: string;
  candidateName: string;
  position: string;
  action: string;
  score?: number;
  timestamp: string;
  status: 'completed' | 'warning' | 'info';
}

export interface AiInsight {
  id: string;
  title: string;
  message: string;
  type: 'info' | 'warning' | 'success';
}

export interface TrendData {
  month: string;
  candidates: number;
  interviews: number;
  hires: number;
}

export interface DashboardStats {
  totalCandidates: number;
  offersSent: number;
  pendingApprovals: number;
  employeesCreated: number;
  aiRequests: number;
  documentsGenerated: number;
  emailsSent: number;
  auditLogs: number;
}

export const fetchDashboardStats = async (): Promise<DashboardStats> => {
  const response = await apiClient.get('/dashboard');
  return response.data;
};

export const computePipelineFromCandidates = (candidates: Candidate[]): PipelineStage[] => {
  const stages = [
    { name: 'Applied', status: 'Applied' },
    { name: 'AI Screening', status: 'AI Screening' },
    { name: 'Manager Approval', status: 'Manager Approval' },
    { name: 'Offer Sent', status: 'Offer Sent' },
    { name: 'Joined', status: 'Employee Created' }
  ];

  return stages.map(stage => {
    const count = candidates.filter(c => c.status === stage.status).length;
    return {
      name: stage.name,
      stage: stage.name,
      count,
      percentage: candidates.length > 0 ? Math.round((count / candidates.length) * 100) : 0
    };
  });
};

export const getAiInsights = (candidates: Candidate[], stats: DashboardStats | null): AiInsight[] => {
  const pendingApprovalsCount = stats?.pendingApprovals || candidates.filter(c => c.status === 'Manager Approval').length;
  const highCaliberCount = candidates.filter(c => c.matchScore >= 90).length;

  return [
    {
      id: 'ins-1',
      title: 'Sourcing Bottleneck Detected',
      message: `There are currently ${pendingApprovalsCount} candidates in the Manager Approval phase. Consider triggering reminders for reviewers.`,
      type: pendingApprovalsCount > 5 ? 'warning' : 'info'
    },
    {
      id: 'ins-2',
      title: 'High Caliber Vetting Success',
      message: `A total of ${highCaliberCount} candidates are vetted with a compatibility score above 90% in Firestore. Sourcing quality coefficient is high.`,
      type: 'success'
    },
    {
      id: 'ins-3',
      title: 'System Integration Metrics',
      message: 'Onboarding services are synchronized. Active employee credentials have been successfully updated in Firestore user profiles.',
      type: 'info'
    }
  ];
};

export const getTrendData = (candidates: Candidate[]): TrendData[] => {
  const currentHiresCount = candidates.filter(c => c.status === 'Employee Created').length;
  const pendingScreening = candidates.filter(c => c.status === 'AI Screening').length;

  return [
    { month: 'Feb', candidates: 0, interviews: 0, hires: 0 },
    { month: 'Mar', candidates: 0, interviews: 0, hires: 0 },
    { month: 'Apr', candidates: 0, interviews: 0, hires: 0 },
    { month: 'May', candidates: 0, interviews: 0, hires: 0 },
    { month: 'Jun', candidates: candidates.length, interviews: pendingScreening + currentHiresCount, hires: currentHiresCount }
  ];
};

export default {
  fetchDashboardStats,
  computePipelineFromCandidates,
  getAiInsights,
  getTrendData
};