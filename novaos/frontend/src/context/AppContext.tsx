import React, { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useRef } from 'react';
import { collection, onSnapshot, doc, updateDoc, writeBatch } from 'firebase/firestore';
import { useAuth } from './AuthContext';
import { db } from '../lib/firebase';
import apiClient from '../services/api';
import { normalizeDate, formatNormalizedDate } from '../lib/dateUtils';
import { Candidate } from '../services/recruitmentService';
import { WorkflowState, WorkflowStep } from '../services/workflowService';
import { HiringRequest } from '../services/hiringRequestService';

export type LoadingKey = 'app' | 'dashboard' | 'commandCenter' | 'pipeline' | 'workflow' | 'intelligence';
export interface AppActivity { id: string; message: string; sub?: string; time: string; timestamp: string; type: 'info' | 'success' | 'warning' | 'error' }
export interface AppNotification { id: string; message: string; sub?: string; createdAt: string; type: AppActivity['type']; read: boolean; requestId?: string }
export interface KPIs { totalCandidates: number; pendingApprovals: number; offersSent: number; employeesCreated: number; executionsToday: number }
export interface AppState {
  currentUser: ReturnType<typeof useAuth>['user'];
  candidates: Candidate[];
  employees: Candidate[];
  workflows: Record<string, WorkflowState>;
  hiringRequests: HiringRequest[];
  selectedCandidateId: string | null;
  activities: AppActivity[];
  notifications: AppNotification[];
  kpis: KPIs;
  executionsToday: number;
  backendOnline: boolean;
  lastSync: string | null;
  hydrated: boolean;
  loading: Record<LoadingKey, boolean>;
}

type Action =
  | { type: 'USER'; payload: AppState['currentUser'] }
  | { type: 'CANDIDATES'; payload: Candidate[] }
  | { type: 'EMPLOYEES'; payload: Candidate[] }
  | { type: 'WORKFLOWS'; payload: Record<string, WorkflowState> }
  | { type: 'HIRING_REQUESTS'; payload: HiringRequest[] }
  | { type: 'ACTIVITIES'; payload: AppActivity[] }
  | { type: 'NOTIFICATIONS'; payload: AppNotification[] }
  | { type: 'NOTIFY'; payload: AppNotification }
  | { type: 'SELECT'; payload: string | null }

  | { type: 'ONLINE'; payload: boolean }
  | { type: 'LOADING'; payload: { key: LoadingKey; value: boolean } }
  | { type: 'READ'; payload?: string };

const initialState: AppState = {
  currentUser: null,
  candidates: [],
  employees: [],
  workflows: {},
  hiringRequests: [],
  selectedCandidateId: null,
  activities: [],
  notifications: [],
  kpis: { totalCandidates: 0, pendingApprovals: 0, offersSent: 0, employeesCreated: 0, executionsToday: 0 },
  executionsToday: 0,
  backendOnline: false,
  lastSync: null,
  hydrated: false,
  loading: { app: true, dashboard: false, commandCenter: false, pipeline: false, workflow: false, intelligence: false },
};

const candidateFrom = (id: string, data: any): Candidate => ({
  id,
  name: String(data.name || ''),
  role: String(data.role || data.position || ''),
  email: String(data.email || ''),
  status: (data.status || 'Applied') as Candidate['status'],
  matchScore: Number(data.matchScore || data.score || 0),
  score: Number(data.matchScore || data.score || 0),
  source: String(data.source || ''),
  aiSummary: data.aiSummary ? String(data.aiSummary) : undefined,
  resumeSummary: String(data.resumeSummary || data.aiSummary || ''),
  strengths: Array.isArray(data.strengths) ? data.strengths.map(String) : [],
  weaknesses: Array.isArray(data.weaknesses) ? data.weaknesses.map(String) : [],
  recommendedSalary: data.annualCtc ? 'INR ' + Number(data.annualCtc).toLocaleString('en-IN') : String(data.recommendedSalary || ''),
  recommendedInterviewer: String(data.manager || data.recommendedInterviewer || ''),
});

const stageOrder = ['APPLIED', 'POLICY_REVIEWED', 'MANAGER_PENDING', 'MANAGER_APPROVED', 'LEGAL_PENDING',
  'LEGAL_APPROVED', 'FINANCE_PENDING', 'FINANCE_APPROVED', 'OFFER_GENERATING', 'OFFER_GENERATED',
  'EMAIL_PENDING', 'EMAIL_SENT', 'EMPLOYEE_CREATED'];

const workflowFrom = (data: any): WorkflowState => {
  const state = String(data.state || 'APPLIED');
  const stateIndex = stageOrder.indexOf(state);
  const definitions: Array<[string, string, string]> = [
    ['intake', 'Request Intake', 'APPLIED'],
    ['policy', 'Policy Review', 'POLICY_REVIEWED'],
    ['manager', 'Manager Approval', 'MANAGER_APPROVED'],
    ['legal', 'Legal Approval', 'LEGAL_APPROVED'],
    ['finance', 'Finance Approval', 'FINANCE_APPROVED'],
    ['offer', 'Offer Generation', 'OFFER_GENERATED'],
    ['email', 'Email Delivery', 'EMAIL_SENT'],
    ['employee', 'Employee Creation', 'EMPLOYEE_CREATED'],
  ];
  const steps: WorkflowStep[] = definitions.map(([id, title, completedState]) => {
    const completedIndex = stageOrder.indexOf(completedState);
    const completed = state === 'EMPLOYEE_CREATED' || stateIndex >= completedIndex;
    const running = !completed && state !== 'FAILED' && definitions.findIndex(item => item[0] === id)
      === definitions.findIndex(([, , s]) => stateIndex < stageOrder.indexOf(s));
    return { id, title, status: state === 'FAILED' && data.failedStage?.toLowerCase().includes(id) ? 'failed'
      : completed ? 'completed' : running ? 'running' : 'pending', time: '' };
  });
  const current = steps.find(step => step.status === 'running' || step.status === 'failed')?.id
    || (state === 'EMPLOYEE_CREATED' ? 'employee' : '');
  return {
    steps,
    currentStep: current,
    logs: [],
    status: state === 'EMPLOYEE_CREATED' ? 'Completed' : state === 'FAILED' ? 'Paused' : 'Running',
    candidateName: String(data.extractedDetails?.name || ''),
    candidateRole: String(data.extractedDetails?.position || ''),
    progress: Math.round((steps.filter(step => step.status === 'completed').length / steps.length) * 100),
  };
};

const deriveKpis = (state: AppState): KPIs => {
  const hiringRequests = state.hiringRequests || [];
  const candidates = state.candidates || [];
  const employees = state.employees || [];
  
  // 1. Total Candidates (unique email addresses from candidates and hiringRequests)
  const candidateEmails = new Set<string>();
  candidates.forEach(c => {
    if (c.email) candidateEmails.add(c.email.toLowerCase().trim());
  });
  hiringRequests.forEach(d => {
    if (d.candidateEmail) candidateEmails.add(d.candidateEmail.toLowerCase().trim());
  });
  const totalCandidatesCount = candidateEmails.size || candidates.length;

  // 2. Pending Approvals (for HrAdmin/SuperAdmin, count all pending)
  const pendingApprovalsCount = hiringRequests.filter(d => 
    ['PENDING_MANAGER_APPROVAL', 'PENDING_FINANCE_APPROVAL', 'PENDING_LEGAL_APPROVAL', 'PENDING_CEO_APPROVAL'].includes(d.status)
  ).length;

  // 3. Offers Sent (Offers Generated)
  const offersSentCount = hiringRequests.filter(d => 
    d.pdfUrl || 
    d.pdfGeneratedAt || 
    d.offerLetterStatus === 'GENERATED' || 
    ['OFFER_GENERATED', 'EMAIL_SENDING', 'EMAIL_SENT', 'WORKFLOW_COMPLETED'].includes(d.status)
  ).length;

  // 4. Employees Created
  const employeeEmails = new Set<string>();
  employees.forEach(e => {
    if (e.email) employeeEmails.add(e.email.toLowerCase().trim());
  });
  hiringRequests.forEach(d => {
    if (['WORKFLOW_COMPLETED', 'APPROVED', 'EMPLOYEE_CREATED'].includes(d.status) && d.candidateEmail) {
      employeeEmails.add(d.candidateEmail.toLowerCase().trim());
    }
  });
  const employeesCreatedCount = employeeEmails.size || employees.length;

  // 5. Executions Today
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const startOfTodayMs = startOfToday.getTime();
  const executionsTodayCount = hiringRequests.filter(d => {
    const created = normalizeDate(d.createdAt);
    return created && created.getTime() >= startOfTodayMs;
  }).length;

  return {
    totalCandidates: totalCandidatesCount,
    pendingApprovals: pendingApprovalsCount,
    offersSent: offersSentCount,
    employeesCreated: employeesCreatedCount,
    executionsToday: executionsTodayCount,
  };
};

function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case 'USER': return { ...state, currentUser: action.payload };
    case 'CANDIDATES': {
      const next = { ...state, candidates: action.payload, hydrated: true, loading: { ...state.loading, app: false } };
      return { ...next, kpis: deriveKpis(next) };
    }
    case 'EMPLOYEES': {
      const next = { ...state, employees: action.payload };
      return { ...next, kpis: deriveKpis(next) };
    }
    case 'WORKFLOWS': {
      const next = { ...state, workflows: action.payload, executionsToday: Object.values(action.payload).filter(w => w.status === 'Completed').length };
      return { ...next, kpis: deriveKpis(next) };
    }
    case 'HIRING_REQUESTS': {
      const next = { ...state, hiringRequests: action.payload };
      return { ...next, kpis: deriveKpis(next) };
    }
    case 'ACTIVITIES': return { ...state, activities: action.payload };
    case 'NOTIFICATIONS': return { ...state, notifications: action.payload };
    case 'NOTIFY': return { ...state, notifications: [action.payload, ...state.notifications].slice(0, 50) };
    case 'SELECT': return { ...state, selectedCandidateId: action.payload };
    case 'ONLINE': return { ...state, backendOnline: action.payload, lastSync: new Date().toISOString() };
    case 'LOADING': return { ...state, loading: { ...state.loading, [action.payload.key]: action.payload.value } };
    case 'READ': return { ...state, notifications: state.notifications.map(n => ({ ...n, read: action.payload ? n.read || n.id === action.payload : true })) };
  }
}

interface Value extends AppState {
  hiringRequests: HiringRequest[];
  selectedCandidate: Candidate | null;
  setCurrentUser: (user: AppState['currentUser']) => void;
  addCandidate: (candidate: Candidate) => void;
  updateCandidate: (id: string, changes: Partial<Candidate>) => void;
  updateCandidateStatus: (id: string, status: Candidate['status']) => void;
  approveCandidate: (id: string) => WorkflowState | null;
  rejectCandidate: (id: string) => void;
  selectCandidate: (candidate: Candidate | string | null) => void;
  startWorkflow: (id: string) => WorkflowState | null;
  updateWorkflow: (id: string, workflow: WorkflowState) => void;
  completeWorkflow: (id: string | Candidate) => void;
  addActivity: (message: string, sub?: string, type?: AppActivity['type']) => void;
  notify: (message: string, type?: AppActivity['type'], sub?: string) => void;
  markNotificationRead: (id?: string) => void;
  refreshDashboard: () => Promise<void>;
  setBackendOnline: (online: boolean) => void;
  setLoading: (key: LoadingKey, value: boolean) => void;
}

const AppContext = createContext<Value | undefined>(undefined);

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, dispatch] = useReducer(reducer, initialState);
  const { user } = useAuth();
  const stateRef = useRef(state);
  stateRef.current = state;

  const notify = useCallback((message: string, type: AppActivity['type'] = 'info', sub?: string) => {
    dispatch({ type: 'NOTIFY', payload: { id: crypto.randomUUID(), message, sub, type, createdAt: new Date().toISOString(), read: false } });
  }, []);

  useEffect(() => { dispatch({ type: 'USER', payload: user }); }, [user]);

  useEffect(() => {
    if (!user) return;
    const hiringRoles = ['SUPER_ADMIN', 'CEO', 'HR_ADMIN', 'HIRING_MANAGER', 'LEGAL', 'FINANCE'];
    if (!hiringRoles.includes(user.role)) {
      dispatch({ type: 'CANDIDATES', payload: [] });
      return;
    }
    const fail = (error: Error) => {
      dispatch({ type: 'LOADING', payload: { key: 'app', value: false } });
      notify('Firestore connection failed', 'error', error.message);
    };
    const unsubscribers = [
      onSnapshot(collection(db, 'candidates'), snap => dispatch({ type: 'CANDIDATES',
        payload: snap.docs.map(item => candidateFrom(item.id, item.data())) }), fail),
      onSnapshot(collection(db, 'employees'), snap => dispatch({ type: 'EMPLOYEES',
        payload: snap.docs.map(item => candidateFrom(item.id, item.data())) }), fail),
      onSnapshot(collection(db, 'workflowRequests'), snap => {
        const workflows: Record<string, WorkflowState> = {};
        snap.docs.forEach(item => { workflows[item.id] = workflowFrom(item.data()); });
        dispatch({ type: 'WORKFLOWS', payload: workflows });
      }, fail),
      onSnapshot(collection(db, 'hiringRequests'), snap => {
        dispatch({ type: 'HIRING_REQUESTS', payload: snap.docs.map(item => ({ id: item.id, ...item.data() })) });
      }, fail),
      onSnapshot(collection(db, 'auditLogs'), snap => dispatch({ type: 'ACTIVITIES', payload: snap.docs
        .map(item => {
          const data = item.data();
          const d = normalizeDate(data.timestamp);
          const timestamp = d ? d.toISOString() : '';
          return { id: item.id, message: String(data.action || ''), sub: String(data.details || ''),
            timestamp, time: d ? formatNormalizedDate(d) : 'Time unavailable',
            type: String(data.action || '').includes('FAILED') ? 'error' : 'success' } as AppActivity;
        }).sort((a, b) => b.timestamp.localeCompare(a.timestamp)).slice(0, 50) }), fail),
      onSnapshot(collection(db, 'notifications'), snap => dispatch({ type: 'NOTIFICATIONS', payload: snap.docs
        .filter(item => !item.data().targetRole || item.data().targetRole === user.role)
        .map(item => {
          const data = item.data();
          const d = normalizeDate(data.timestamp);
          const createdAt = d ? d.toISOString() : '';
          return { id: item.id, message: String(data.title || ''), sub: String(data.message || ''),
            createdAt, type: 'info', read: Boolean(data.read), requestId: data.requestId } as AppNotification;
        }).sort((a, b) => b.createdAt.localeCompare(a.createdAt)) }), fail),
    ];
    return () => unsubscribers.forEach(unsubscribe => unsubscribe());
  }, [user?.uid, user?.role, notify]);

  useEffect(() => {
    let previous: boolean | null = null;
    const ping = async () => {
      let online = false;
      try { await apiClient.get('/health', { timeout: 4000 }); online = true; } catch { online = false; }
      dispatch({ type: 'ONLINE', payload: online });
      if (previous !== online) {
        notify(online ? 'Backend Connected' : 'Backend Offline', online ? 'success' : 'warning',
          online ? 'Live services synchronized.' : 'Live hiring actions are paused until the backend reconnects.');
        previous = online;
      }
    };
    ping();
    const timer = window.setInterval(ping, 10000);
    return () => window.clearInterval(timer);
  }, [notify]);

  const readOnlyNotice = useCallback(() => notify('Governed action required', 'warning',
    'Advance this candidate from the Decision Passport with the authorized role.'), [notify]);

  const value = useMemo<Value>(() => ({
    ...state,
    selectedCandidate: state.candidates.find(c => c.id === state.selectedCandidateId) || null,
    setCurrentUser: currentUser => dispatch({ type: 'USER', payload: currentUser }),
    addCandidate: readOnlyNotice,
    updateCandidate: readOnlyNotice,
    updateCandidateStatus: readOnlyNotice,
    approveCandidate: id => { dispatch({ type: 'SELECT', payload: id }); return state.workflows[id] || null; },
    rejectCandidate: readOnlyNotice,
    selectCandidate: candidate => dispatch({ type: 'SELECT', payload: typeof candidate === 'string' ? candidate : candidate?.id || null }),
    startWorkflow: id => state.workflows[id] || null,
    updateWorkflow: readOnlyNotice,
    completeWorkflow: readOnlyNotice,
    addActivity: readOnlyNotice,
    notify,
    markNotificationRead: id => {
      dispatch({ type: 'READ', payload: id });
      if (id) {
        updateDoc(doc(db, 'notifications', id), { read: true }).catch(e => console.error("Failed to mark read in db", e));
      } else {
        const unreadList = stateRef.current.notifications.filter(n => !n.read);
        if (unreadList.length > 0) {
          const batch = writeBatch(db);
          unreadList.forEach(n => {
            batch.update(doc(db, 'notifications', n.id), { read: true });
          });
          batch.commit().catch(e => console.error("Failed to batch mark read in db", e));
        }
      }
    },
    refreshDashboard: async () => undefined,
    setBackendOnline: online => dispatch({ type: 'ONLINE', payload: online }),
    setLoading: (key, value) => dispatch({ type: 'LOADING', payload: { key, value } }),
  }), [state, notify, readOnlyNotice]);

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
};

export const useAppContext = () => {
  const context = useContext(AppContext);
  if (!context) throw new Error('useAppContext must be used within AppProvider');
  return context;
};
