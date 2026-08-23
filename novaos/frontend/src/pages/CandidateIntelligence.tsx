import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Brain, RefreshCw, ShieldCheck } from 'lucide-react';
import intelligenceService, { CandidateAnalysis } from '../services/intelligenceService';
import CandidateList from '../components/CandidateList';
import CandidateProfile from '../components/CandidateProfile';
import ScoreCard from '../components/ScoreCard';
import StrengthWeakness from '../components/StrengthWeakness';
import RecommendationCard from '../components/RecommendationCard';
import ResumeSummary from '../components/ResumeSummary';
import { useAppContext } from '../context/AppContext';

const decisionRows = (request: any) => [
  ['HR', request.status === 'DRAFT' ? 'CURRENT' : 'SUBMITTED', request.createdByName || 'Submitted through NovaOS'],
  ['Hiring Manager', request.managerApprovalStatus || 'PENDING', request.managerApprovalComment || 'No reviewer reason recorded'],
  ['Finance', request.financeApprovalStatus || 'PENDING', request.financeApprovalComment || (request.riskLevel ? `Risk: ${request.riskLevel}` : 'No reviewer reason recorded')],
  ['Legal', request.legalApprovalStatus || 'PENDING', request.legalApprovalComment || (request.riskLevel ? `Overall risk: ${request.riskLevel}` : 'No reviewer reason recorded')],
  ['Offer', request.offerLetterStatus || (request.pdfGeneratedAt ? 'GENERATED' : 'PENDING'), request.pdfFileName || 'Not generated'],
  ['Email', request.emailStatus || 'PENDING', request.emailStatus === 'FAILED' ? (request.emailFailureReason || 'Delivery failed') : request.emailMessageId || 'Not sent'],
];

export const CandidateIntelligence: React.FC = () => {
  const navigate = useNavigate();
  const { candidates, selectedCandidate, selectCandidate, loading, setLoading } = useAppContext();
  const [analysis, setAnalysis] = useState<CandidateAnalysis | null>(null);
  const [error, setError] = useState('');
  const directory = useMemo(() => candidates.map(candidate => ({ id: candidate.id, name: candidate.name,
    role: `${candidate.role} · ${candidate.status}${candidate.riskLevel ? ` · ${candidate.riskLevel} risk` : ''}`, score: candidate.matchScore })), [candidates]);

  useEffect(() => { if (!selectedCandidate && candidates.length) selectCandidate(candidates[0].id); }, [candidates, selectedCandidate, selectCandidate]);
  useEffect(() => {
    let live = true;
    if (!selectedCandidate) { setAnalysis(null); return; }
    setError(''); setLoading('intelligence', true);
    intelligenceService.getCandidateAnalysis(selectedCandidate.id, selectedCandidate)
      .then(data => live && setAnalysis(data)).catch(err => live && setError(err.message || 'Unable to load Candidate Intelligence.'))
      .finally(() => live && setLoading('intelligence', false));
    return () => { live = false; };
  }, [selectedCandidate, setLoading]);

  const request = selectedCandidate?.hiringRequest;
  const recommendation = analysis?.recommendation || (analysis?.overallScore ? analysis.overallScore >= 80 ? 'Strong match — recommended for human review.' : 'Requires additional human review.' : 'No AI recommendation has been recorded. Human review remains required.');

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 bg-slate-950 text-slate-200">
    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
      <div><h1 className="text-3xl font-extrabold">Candidate <span className="text-cyan-500">Intelligence</span></h1><p className="text-slate-400 text-sm mt-1">Advisory intelligence and the real decision journey for each hiring request.</p></div>
      <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-4 py-2 rounded-xl text-xs"><Brain className="h-4 w-4 text-cyan-500" /> Advisory only · human decision required</div>
    </div>
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
      <div className="lg:col-span-1 h-[640px]"><CandidateList candidates={directory} selectedId={selectedCandidate?.id} onSelect={selectCandidate} /></div>
      <div className="lg:col-span-2 space-y-6">
        {loading.intelligence ? <div className="min-h-[400px] grid place-items-center bg-slate-900 border border-slate-800 rounded-2xl"><div className="flex items-center gap-3 text-xs text-slate-400 font-mono"><RefreshCw className="h-5 w-5 text-cyan-500 animate-spin" />Loading hiring evidence…</div></div>
        : error ? <div className="p-8 border border-rose-500/20 bg-rose-500/5 rounded-2xl text-rose-300 text-sm">Unable to load Candidate Intelligence: {error}<button onClick={() => selectedCandidate && selectCandidate(selectedCandidate.id)} className="block mt-3 text-cyan-400">Retry</button></div>
        : analysis && selectedCandidate ? <motion.div key={analysis.id + analysis.status} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
          <CandidateProfile name={analysis.name} role={analysis.role} email={analysis.email} source={analysis.source} status={selectedCandidate.currentStatus || analysis.status} />
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 bg-slate-900 border border-slate-800 p-5 rounded-2xl text-xs">
            {[['Department',selectedCandidate.department],['Experience',selectedCandidate.experience],['Expected salary',selectedCandidate.recommendedSalary],['Joining date',selectedCandidate.joiningDate],['Location',selectedCandidate.location],['Employment',selectedCandidate.employmentType],['Risk level',selectedCandidate.riskLevel],['Skills',selectedCandidate.skills?.join(', ')]].map(([label,value]) => <div key={label}><span className="text-[9px] uppercase font-mono text-slate-500">{label}</span><p className="text-slate-200 mt-1">{value || 'Not recorded'}</p></div>)}
          </div>
          <ScoreCard overallScore={analysis.overallScore} skills={analysis.skills} />
          <StrengthWeakness strengths={analysis.strengths} weaknesses={analysis.weaknesses} />
          <RecommendationCard recommendation={recommendation} recommendedSalary={analysis.recommendedSalary || 'Not recorded'} recommendedInterviewer={analysis.recommendedInterviewer || 'Not recorded'} onApprove={() => navigate('/hiring-requests/' + selectedCandidate.id)} isApproving={false} />
          <ResumeSummary summary={analysis.resumeSummary || 'No resume or profile analysis has been recorded.'} />
          {request && <section className="bg-slate-900 border border-cyan-500/15 rounded-2xl p-6"><div className="flex items-center gap-2 mb-5"><ShieldCheck className="h-5 w-5 text-cyan-400" /><div><h3 className="font-bold text-white">Decision Passport</h3><p className="text-[10px] text-slate-500">Recorded decisions only — no inferred approvals.</p></div></div>
            <div className="space-y-3">{decisionRows(request).map(([stage,status,reason], index) => <div key={stage} className="flex gap-4"><div className="flex flex-col items-center"><span className={`h-3 w-3 rounded-full ${status === 'APPROVED' || status === 'SUBMITTED' || status === 'GENERATED' || status === 'SENT' ? 'bg-emerald-400' : status === 'REJECTED' || status === 'FAILED' ? 'bg-rose-400' : 'bg-amber-400'}`} />{index < 5 && <span className="w-px h-10 bg-slate-700" />}</div><div className="pb-2"><strong className="text-xs text-white">{stage}</strong><span className="ml-2 text-[9px] font-mono text-cyan-400">{status}</span><p className="text-[10px] text-slate-400 mt-1">{reason}</p></div></div>)}</div>
          </section>}
        </motion.div> : <div className="min-h-[400px] grid place-items-center border border-dashed border-slate-800 rounded-2xl text-slate-500">{candidates.length ? 'Select a hiring request to view intelligence.' : 'No hiring requests are available.'}</div>}
      </div>
    </div>
  </motion.div>;
};
export default CandidateIntelligence;
