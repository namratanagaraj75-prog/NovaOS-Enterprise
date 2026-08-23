import React, { useEffect, useMemo } from 'react';
import { motion } from 'framer-motion';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { FileSearch, ShieldCheck } from 'lucide-react';
import { useAppContext } from '../context/AppContext';
import { WorkflowState } from '../services/workflowService';
import { normalizeDate } from '../lib/dateUtils';
import WorkflowCanvas from '../components/WorkflowCanvas';
import AgentStatus from '../components/AgentStatus';
import ExecutionLog from '../components/ExecutionLog';
import AutomationStats from '../components/AutomationStats';

export const WorkflowAutomation: React.FC = () => {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const app = useAppContext();
  const candidateId = params.get('candidateId') || app.selectedCandidate?.id || app.candidates[0]?.id || '';
  const candidate = app.candidates.find(item => item.id === candidateId) || null;
  const workflow = candidateId ? app.workflows[candidateId] : undefined;
  const empty: WorkflowState = { steps: [], currentStep: '', logs: [], status: 'Idle', candidateName: '', progress: 0 };
  const state = workflow || empty;

  useEffect(() => { if (candidateId && app.selectedCandidate?.id !== candidateId) app.selectCandidate(candidateId); }, [candidateId]);

  const metrics = useMemo(() => {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const executionsToday = app.hiringRequests.filter(item => (normalizeDate(item.createdAt)?.getTime() || 0) >= today.getTime()).length;
    const durations = app.hiringRequests.map(item => {
      const start = normalizeDate(item.createdAt); const end = normalizeDate(item.emailSentAt || item.finalApprovedAt);
      return start && end ? (end.getTime() - start.getTime()) / 3600000 : null;
    }).filter((value): value is number => value !== null && value >= 0);
    const completed = Object.values(app.workflows).filter(item => item.status === 'Completed').length;
    const failed = app.hiringRequests.filter(item => item.status === 'REJECTED' || item.emailStatus === 'FAILED').length;
    return { executionsToday, average: durations.length ? `${(durations.reduce((a,b)=>a+b,0)/durations.length).toFixed(1)}h` : 'Not enough data',
      rate: app.hiringRequests.length ? `${Math.round((completed / app.hiringRequests.length) * 100)}%` : 'Not enough data', failed };
  }, [app.hiringRequests, app.workflows]);

  const select = (id: string) => { app.selectCandidate(id); setParams({ candidateId: id }); };

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 text-slate-200">
    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
      <div><h1 className="text-3xl font-extrabold text-white">Workflow <span className="text-cyan-500">Automation</span></h1>
        <p className="text-gray-400 text-sm mt-1">{candidate ? `Governed workflow for ${candidate.name} · ${candidate.role}` : 'No hiring workflows are available.'}</p></div>
      {candidate && <button onClick={() => navigate('/hiring-requests/' + candidate.id)} className="flex items-center gap-2 bg-cyan-500 text-slate-950 px-4 py-2.5 rounded-xl text-xs font-semibold"><FileSearch className="h-4 w-4" /> Open Hiring Request</button>}
    </div>

    <section className="bg-slate-900/60 border border-white/5 p-5 rounded-2xl">
      <h2 className="text-xs font-bold uppercase font-mono text-slate-300 mb-3">Active & Recent Workflows</h2>
      {app.candidates.length ? <div className="flex gap-3 overflow-x-auto pb-1">{app.candidates.map(item => <button key={item.id} onClick={() => select(item.id)}
        className={`min-w-56 text-left p-3 rounded-xl border ${item.id === candidateId ? 'border-cyan-500/40 bg-cyan-500/5' : 'border-slate-800 bg-slate-950'}`}>
        <strong className="text-xs text-white block truncate">{item.name}</strong><span className="text-[10px] text-slate-400">{item.role}</span><span className="text-[9px] text-cyan-400 font-mono block mt-2">{item.currentStatus?.replace(/_/g,' ')}</span>
      </button>)}</div> : <p className="text-xs text-slate-500">No governed hiring workflows exist yet.</p>}
    </section>

    <AutomationStats executionsToday={metrics.executionsToday} averageTime={metrics.average} automationRate={metrics.rate} failedRuns={metrics.failed} />
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
      <div className="lg:col-span-2 bg-slate-900/60 border border-white/5 p-6 rounded-3xl shadow-2xl">
        <div className="flex justify-between mb-6"><div><h3 className="text-sm font-bold uppercase font-mono">Governed execution canvas</h3><p className="text-[11px] text-gray-500">All nodes reflect backend approval, document, and email state.</p></div>
          {state.status === 'Running' && <span className="flex items-center gap-2 text-[10px] text-cyan-400 font-mono"><ShieldCheck className="h-4 w-4" /> LIVE FIRESTORE</span>}</div>
        {state.steps.length ? <WorkflowCanvas steps={state.steps} currentStep={state.currentStep} /> : <div className="h-72 flex items-center justify-center text-slate-500 border border-dashed border-slate-800 rounded-2xl">No governed workflow selected</div>}
      </div>
      <div className="space-y-6 lg:sticky lg:top-24"><AgentStatus status={state.status} currentJob={candidate ? `Hire ${candidate.name}` : 'Idle'} progress={state.progress} /><ExecutionLog logs={state.logs} /></div>
    </div>
  </motion.div>;
};
export default WorkflowAutomation;
