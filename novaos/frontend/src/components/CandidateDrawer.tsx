import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, CheckCircle, ShieldAlert, XCircle, Award, DollarSign, UserCheck, Play } from 'lucide-react';
import { Candidate } from '../services/recruitmentService';

interface CandidateDrawerProps {
  candidate: Candidate | null;
  onClose: () => void;
  onApprove: (id: string) => void;
  onReject: (id: string) => void;
  isProcessing?: boolean;
}

export const CandidateDrawer: React.FC<CandidateDrawerProps> = ({
  candidate,
  onClose,
  onApprove,
  onReject,
  isProcessing = false
}) => {
  return (
    <AnimatePresence>
      {candidate && (
        <>
          {/* 1. Backdrop overlay */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 0.5 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-slate-950 z-40"
          />

          {/* 2. Slide Over Panel */}
          <motion.div
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            className="fixed top-0 right-0 bottom-0 w-full max-w-md bg-slate-900 border-l border-slate-800 z-50 p-6 flex flex-col justify-between shadow-2xl h-screen overflow-y-auto"
          >
            {/* Header Content */}
            <div className="space-y-6">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div>
                  <span className="text-[10px] text-cyan-500 font-bold font-mono tracking-widest uppercase">Candidate Identity File</span>
                  <h3 className="text-xl font-bold text-slate-200 mt-1">{candidate.name}</h3>
                  <span className="text-xs text-slate-400 font-mono block mt-0.5">{candidate.email}</span>
                </div>
                <button
                  onClick={onClose}
                  className="p-1.5 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-slate-200 transition-colors"
                  title="Close panel"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {/* Match Score & Status */}
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-slate-950 border border-slate-800 p-4 rounded-xl flex flex-col justify-between h-20">
                  <span className="text-[9px] text-slate-400 font-mono uppercase">AI Match Rate</span>
                  <div className="flex items-center gap-1.5">
                    <Award className="h-4.5 w-4.5 text-cyan-500" />
                    <span className="text-xl font-extrabold text-slate-200">{candidate.matchScore}%</span>
                  </div>
                </div>
                <div className="bg-slate-950 border border-slate-800 p-4 rounded-xl flex flex-col justify-between h-20">
                  <span className="text-[9px] text-slate-400 font-mono uppercase">Pipeline Phase</span>
                  <span className="text-xs font-bold text-slate-200 tracking-wide">
                    {candidate.status}
                  </span>
                </div>
              </div>

              {/* Resume summary */}
              <div className="space-y-2">
                <h4 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono">Executive Summary</h4>
                <p className="text-xs text-slate-300 leading-relaxed bg-slate-950 p-4 rounded-xl border border-slate-800">
                  {candidate.resumeSummary}
                </p>
              </div>

              {/* Strengths & Weaknesses */}
              <div className="grid grid-cols-1 gap-4">
                <div>
                  <h4 className="text-[10px] font-bold text-emerald-500 uppercase tracking-wider font-mono mb-2">Key Strengths</h4>
                  <ul className="text-[11px] text-slate-300 space-y-2 bg-slate-950/40 p-3 rounded-xl border border-slate-800">
                    {candidate.strengths.map((str, idx) => (
                      <li key={idx} className="flex items-start gap-2">
                        <CheckCircle className="h-3.5 w-3.5 text-emerald-500 mt-0.5 shrink-0" />
                        <span>{str}</span>
                      </li>
                    ))}
                  </ul>
                </div>
                <div>
                  <h4 className="text-[10px] font-bold text-violet-500 uppercase tracking-wider font-mono mb-2">Considerations</h4>
                  <ul className="text-[11px] text-slate-300 space-y-2 bg-slate-950/40 p-3 rounded-xl border border-slate-800">
                    {candidate.weaknesses.map((weak, idx) => (
                      <li key={idx} className="flex items-start gap-2">
                        <ShieldAlert className="h-3.5 w-3.5 text-violet-500 mt-0.5 shrink-0" />
                        <span>{weak}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              {/* Recommended Salary & Interviewer */}
              <div className="space-y-3 p-4 bg-slate-950 rounded-xl border border-slate-800 text-xs">
                <div className="flex justify-between items-center">
                  <span className="text-slate-400 flex items-center gap-1.5">
                    <DollarSign className="h-4 w-4 text-cyan-500" />
                    <span>Salary Recommendation</span>
                  </span>
                  <span className="font-bold text-slate-200">{candidate.recommendedSalary}</span>
                </div>
                <div className="h-[1px] bg-slate-800 w-full"></div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-400 flex items-center gap-1.5">
                    <UserCheck className="h-4 w-4 text-violet-500" />
                    <span>Assigned Interviewer</span>
                  </span>
                  <span className="font-bold text-slate-200">{candidate.recommendedInterviewer}</span>
                </div>
              </div>
            </div>

            {/* Bottom Actions Row */}
            {candidate.status !== 'Employee Created' && candidate.status !== 'Rejected' && (
              <div className="pt-6 border-t border-slate-800 flex gap-4 mt-6">
                <button
                  onClick={() => onReject(candidate.id)}
                  disabled={isProcessing}
                  className="flex-1 py-3 border border-slate-800 bg-slate-950 text-rose-500 hover:bg-slate-900/50 font-semibold rounded-xl text-xs flex items-center justify-center gap-2 transition-all disabled:opacity-50"
                >
                  <XCircle className="h-4 w-4" />
                  <span>Reject Profile</span>
                </button>
                <button
                  onClick={() => onApprove(candidate.id)}
                  disabled={isProcessing}
                  className="flex-1 py-3 bg-cyan-500 text-slate-950 hover:bg-cyan-400 font-semibold rounded-xl text-xs flex items-center justify-center gap-2 transition-all disabled:opacity-50 shadow-glow-cyan"
                >
                  <Play className="h-4 w-4 text-slate-950 fill-slate-950" />
                  <span>Approve Match</span>
                </button>
              </div>
            )}
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default CandidateDrawer;
