import React from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { Brain, RefreshCw } from 'lucide-react';
import { useAppContext } from '../context/AppContext';
import { Candidate } from '../services/recruitmentService';
import PipelineColumn from '../components/PipelineColumn';

const columns: Array<{ title: string; status: Candidate['status']; accent: string }> = [
  { title: 'Applied', status: 'Applied', accent: 'text-blue-500' },
  { title: 'AI Screening', status: 'AI Screening', accent: 'text-cyan-500' },
  { title: 'Manager Review', status: 'Manager Review', accent: 'text-violet-500' },
  { title: 'Finance Review', status: 'Finance Review', accent: 'text-amber-500' },
  { title: 'Legal Review', status: 'Legal Review', accent: 'text-orange-500' },
  { title: 'Offer Generated', status: 'Offer Generated', accent: 'text-fuchsia-500' },
  { title: 'Offer Sent', status: 'Offer Sent', accent: 'text-purple-500' },
  { title: 'Employee Created', status: 'Employee Created', accent: 'text-emerald-500' },
  { title: 'Rejected', status: 'Rejected', accent: 'text-rose-500' },
];

export const RecruitmentPipeline: React.FC = () => {
  const navigate = useNavigate();
  const { candidates, refreshDashboard, loading } = useAppContext();

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 bg-slate-950 text-slate-200">
    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
      <div><h1 className="text-3xl font-extrabold">Recruitment <span className="text-violet-500">Pipeline</span></h1>
        <p className="text-slate-400 text-sm mt-1">Every column is derived from the authoritative hiring request record.</p></div>
      <div className="flex items-center gap-3">
        <button onClick={refreshDashboard} className="p-2.5 bg-slate-900 border border-slate-800 rounded-xl text-xs font-mono flex items-center gap-2"><RefreshCw className="h-4 w-4" /> Live refresh</button>
        <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-4 py-2 rounded-xl text-xs"><Brain className="h-4 w-4 text-violet-500" /> {candidates.length} shared records</div>
      </div>
    </div>

    {loading.app ? <div className="flex gap-6 overflow-hidden">{columns.slice(0, 5).map(column =>
      <div key={column.status} className="w-72 shrink-0 h-[560px] bg-slate-900 rounded-2xl animate-pulse" />)}
    </div> : candidates.length === 0 ? <div className="p-12 text-center border border-dashed border-slate-800 rounded-2xl text-slate-500">
      No hiring requests are available. Create one from the HR Portal or Nova Cortex.
    </div> : <div className="flex overflow-x-auto gap-6 pb-6 min-h-[600px] items-stretch">
      {columns.map(column => <PipelineColumn key={column.status} title={column.title}
        count={candidates.filter(candidate => candidate.status === column.status).length}
        candidates={candidates.filter(candidate => candidate.status === column.status)}
        onViewDetails={candidate => navigate('/hiring-requests/' + candidate.id)} accentColor={column.accent} />)}
    </div>}
  </motion.div>;
};
export default RecruitmentPipeline;
