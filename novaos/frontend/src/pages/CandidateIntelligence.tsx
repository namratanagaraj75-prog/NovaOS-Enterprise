import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Brain, RefreshCw } from 'lucide-react';
import intelligenceService, { CandidateAnalysis } from '../services/intelligenceService';
import CandidateList from '../components/CandidateList';
import CandidateProfile from '../components/CandidateProfile';
import ScoreCard from '../components/ScoreCard';
import StrengthWeakness from '../components/StrengthWeakness';
import RecommendationCard from '../components/RecommendationCard';
import ResumeSummary from '../components/ResumeSummary';
import { useAppContext } from '../context/AppContext';

export const CandidateIntelligence: React.FC = () => {
  const navigate = useNavigate();
  const { candidates, selectedCandidate, selectCandidate, loading, setLoading } = useAppContext();
  const [analysis, setAnalysis] = useState<CandidateAnalysis | null>(null);
  const directory = useMemo(() => candidates.map(candidate => ({
    id: candidate.id, name: candidate.name, role: candidate.role, score: candidate.matchScore,
  })), [candidates]);

  useEffect(() => {
    if (!selectedCandidate && candidates.length) selectCandidate(candidates[0].id);
  }, [candidates, selectedCandidate, selectCandidate]);

  useEffect(() => {
    let live = true;
    if (!selectedCandidate) { setAnalysis(null); return; }
    setLoading('intelligence', true);
    intelligenceService.getCandidateAnalysis(selectedCandidate.id, selectedCandidate)
      .then(data => live && setAnalysis(data))
      .finally(() => live && setLoading('intelligence', false));
    return () => { live = false; };
  }, [selectedCandidate, setLoading]);

  const openPassport = () => selectedCandidate && navigate('/passports/' + selectedCandidate.id);

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 bg-slate-950 text-slate-200">
    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
      <div><h1 className="text-3xl font-extrabold">Candidate <span className="text-cyan-500">Intelligence</span></h1>
        <p className="text-slate-400 text-sm mt-1">Live intelligence from the Firestore candidate and Decision Passport.</p></div>
      <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-4 py-2 rounded-xl text-xs">
        <Brain className="h-4 w-4 text-cyan-500" /> Stored evidence only
      </div>
    </div>
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
      <div className="lg:col-span-1 h-[600px]"><CandidateList candidates={directory}
        selectedId={selectedCandidate?.id} onSelect={selectCandidate} /></div>
      <div className="lg:col-span-2 space-y-6">
        {loading.intelligence ? <div className="min-h-[400px] grid place-items-center bg-slate-900 border border-slate-800 rounded-2xl">
          <div className="flex items-center gap-3 text-xs text-slate-400 font-mono"><RefreshCw className="h-5 w-5 text-cyan-500 animate-spin" />Loading stored evidence…</div>
        </div> : analysis ? <motion.div key={analysis.id + analysis.status} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
          <CandidateProfile name={analysis.name} role={analysis.role} email={analysis.email} source={analysis.source} status={analysis.status} />
          <ScoreCard overallScore={analysis.overallScore} skills={analysis.skills} />
          <StrengthWeakness strengths={analysis.strengths} weaknesses={analysis.weaknesses} />
          <RecommendationCard recommendation={analysis.recommendation || 'No hiring recommendation has been recorded.'}
            recommendedSalary={analysis.recommendedSalary || 'Not recorded'}
            recommendedInterviewer={analysis.recommendedInterviewer || 'Not recorded'}
            onApprove={openPassport} isApproving={false} />
          <ResumeSummary summary={analysis.resumeSummary || 'No resume analysis has been recorded.'} />
        </motion.div> : <div className="min-h-[400px] grid place-items-center border border-dashed border-slate-800 rounded-2xl text-slate-500">
          Select a Firestore candidate to view evidence.
        </div>}
      </div>
    </div>
  </motion.div>;
};
export default CandidateIntelligence;
