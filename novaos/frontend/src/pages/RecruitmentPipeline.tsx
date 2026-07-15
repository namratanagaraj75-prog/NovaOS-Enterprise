import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { Brain, RefreshCw } from 'lucide-react';
import { useAppContext } from '../context/AppContext';
import { Candidate } from '../services/recruitmentService';
import PipelineColumn from '../components/PipelineColumn';
import CandidateDrawer from '../components/CandidateDrawer';

const columns: Array<{ title: string; status: Candidate['status']; accent: string }> = [
  { title: 'Applied', status: 'Applied', accent: 'text-blue-500' },
  { title: 'AI Screening', status: 'AI Screening', accent: 'text-cyan-500' },
  { title: 'Manager Approval', status: 'Manager Approval', accent: 'text-violet-500' },
  { title: 'Offer Sent', status: 'Offer Sent', accent: 'text-purple-500' },
  { title: 'Employee Created', status: 'Employee Created', accent: 'text-emerald-500' },
  { title: 'Rejected', status: 'Rejected', accent: 'text-rose-500' },
];

export const RecruitmentPipeline: React.FC = () => {
  const navigate = useNavigate();
  const { candidates, selectedCandidate, selectCandidate, approveCandidate, rejectCandidate, refreshDashboard, loading } = useAppContext();
  const [processingId, setProcessingId] = useState<string | null>(null);

  const handleApprove = async (id: string) => {
    setProcessingId(id);
    await approveCandidate(id);
    selectCandidate(id);
    navigate('/workflow?run=true&candidateId=' + encodeURIComponent(id));
    setProcessingId(null);
  };

  const handleReject = async (id: string) => {
    setProcessingId(id);
    await rejectCandidate(id);
    selectCandidate(null);
    setProcessingId(null);
  };

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 bg-slate-950 text-slate-200">
    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
      <div>
        <h1 className="text-3xl font-extrabold">Recruitment <span className="text-violet-500">Pipeline</span></h1>
        <p className="text-slate-400 text-sm mt-1">One live Kanban shared by Dashboard, Intelligence, and Workflow Automation.</p>
      </div>
      <div className="flex items-center gap-3">
        <button onClick={refreshDashboard} className="p-2.5 bg-slate-900 border border-slate-800 rounded-xl text-xs font-mono flex items-center gap-2">
          <RefreshCw className="h-4 w-4" /> Recalculate
        </button>
        <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-4 py-2 rounded-xl text-xs">
          <Brain className="h-4 w-4 text-violet-500" /> Pipeline Agent Active
        </div>
      </div>
    </div>

    {loading.app ? <div className="flex gap-6 overflow-hidden">{columns.slice(0, 5).map(column =>
      <div key={column.status} className="w-72 shrink-0 h-[560px] bg-slate-900 rounded-2xl animate-pulse" />)}
    </div> : <div className="flex overflow-x-auto gap-6 pb-6 min-h-[600px] items-stretch">
      {columns.map(column => <PipelineColumn key={column.status} title={column.title}
        count={candidates.filter(candidate => candidate.status === column.status).length}
        candidates={candidates.filter(candidate => candidate.status === column.status)}
        onViewDetails={candidate => selectCandidate(candidate)} accentColor={column.accent} />)}
    </div>}

    <CandidateDrawer candidate={selectedCandidate} onClose={() => selectCandidate(null)}
      onApprove={handleApprove} onReject={handleReject} isProcessing={Boolean(processingId)} />
  </motion.div>;
};
export default RecruitmentPipeline;

