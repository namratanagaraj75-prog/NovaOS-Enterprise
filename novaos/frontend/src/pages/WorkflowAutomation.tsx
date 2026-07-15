import React from 'react';
import { motion } from 'framer-motion';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { FileSearch, ShieldCheck } from 'lucide-react';
import { useAppContext } from '../context/AppContext';
import { WorkflowState } from '../services/workflowService';
import WorkflowCanvas from '../components/WorkflowCanvas';
import AgentStatus from '../components/AgentStatus';
import ExecutionLog from '../components/ExecutionLog';
import AutomationStats from '../components/AutomationStats';

export const WorkflowAutomation: React.FC = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const app = useAppContext();
  const candidateId = params.get('candidateId') || app.selectedCandidate?.id || '';
  const candidate = app.candidates.find(item => item.id === candidateId) || null;
  const workflow = candidateId ? app.workflows[candidateId] : undefined;
  const empty: WorkflowState = { steps: [], currentStep: '', logs: [], status: 'Idle', candidateName: '', progress: 0 };
  const state = workflow || empty;
  const all = Object.values(app.workflows);
  const completed = all.filter(item => item.status === 'Completed').length;
  const failed = all.filter(item => item.status === 'Paused').length;
  const rate = all.length ? Math.round((completed / all.length) * 100) + '%' : '—';

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 text-slate-200">
    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
      <div>
        <h1 className="text-3xl font-extrabold text-white">Workflow <span className="text-cyan-500">Automation</span></h1>
        <p className="text-gray-400 text-sm mt-1">{candidate
          ? 'Live governed workflow for ' + candidate.name
          : 'Select a candidate to inspect its Firestore-backed workflow.'}</p>
      </div>
      {candidate && <button onClick={() => navigate('/passports/' + candidate.id)}
        className="flex items-center gap-2 bg-cyan-500 text-slate-950 px-4 py-2.5 rounded-xl text-xs font-semibold">
        <FileSearch className="h-4 w-4" /> Open Decision Passport
      </button>}
    </div>

    <AutomationStats executionsToday={completed} averageTime="—" automationRate={rate} failedRuns={failed} />
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
      <div className="lg:col-span-2 bg-slate-900/60 border border-white/5 p-6 rounded-3xl shadow-2xl">
        <div className="flex justify-between mb-6">
          <div><h3 className="text-sm font-bold uppercase font-mono">Governed execution canvas</h3>
            <p className="text-[11px] text-gray-500">Stages advance only after verified backend actions.</p></div>
          {state.status === 'Running' && <span className="flex items-center gap-2 text-[10px] text-cyan-400 font-mono">
            <ShieldCheck className="h-4 w-4" /> LIVE FIRESTORE
          </span>}
        </div>
        {state.steps.length ? <WorkflowCanvas steps={state.steps} currentStep={state.currentStep} /> :
          <div className="h-72 flex items-center justify-center text-slate-500 border border-dashed border-slate-800 rounded-2xl">No governed workflow selected</div>}
      </div>
      <div className="space-y-6 lg:sticky lg:top-24">
        <AgentStatus status={state.status} currentJob={candidate ? 'Hire ' + candidate.name : 'Idle'} progress={state.progress} />
        <ExecutionLog logs={state.logs} />
      </div>
    </div>
  </motion.div>;
};
export default WorkflowAutomation;
