import React from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, ShieldAlert, Award, Send } from 'lucide-react';
import { ParsedCandidateData } from '../services/chatService';

interface CandidatePreviewProps {
  candidate: ParsedCandidateData;
  onConfirm: () => void;
  isConfirming?: boolean;
  isConfirmed?: boolean;
}

export const CandidatePreview: React.FC<CandidatePreviewProps> = ({ 
  candidate, 
  onConfirm,
  isConfirming = false,
  isConfirmed = false
}) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 15 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut' }}
      className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm relative overflow-hidden"
    >
      {/* Decorative top border */}
      <div className="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-blue-500 via-cyan-500 to-violet-500"></div>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-800">
        <div>
          <h3 className="text-lg font-bold text-slate-200">{candidate.name}</h3>
          <span className="text-[10px] text-slate-400 font-mono block mt-0.5">{candidate.email}</span>
          <span className="text-[10px] text-cyan-500 font-semibold mt-1 inline-flex items-center gap-1.5 bg-cyan-500/5 px-2 py-0.5 rounded border border-cyan-500/10">
            <Award className="h-3.5 w-3.5" />
            {candidate.role}
          </span>
        </div>
        <div className="flex flex-col items-start sm:items-end">
          <span className="text-[9px] text-slate-400 font-mono uppercase">VETTING COEFFICIENT</span>
          <span className="text-2xl font-extrabold text-slate-200 mt-0.5">{candidate.matchScore}%</span>
        </div>
      </div>

      <div className="py-4 border-b border-slate-800 space-y-2">
        <h4 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono">Resume Executive Analysis</h4>
        <p className="text-xs text-slate-300 leading-relaxed bg-slate-950 p-3 rounded-lg border border-slate-800/60">
          {candidate.resumeSummary || candidate.aiSummary || 'Profile details.'}
        </p>
      </div>

      <div className="py-4 border-b border-slate-800 grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <h4 className="text-[10px] font-bold text-emerald-500 uppercase tracking-wider font-mono mb-2">Strengths</h4>
          <ul className="text-[11px] text-slate-300 space-y-1.5">
            {(candidate.strengths || []).map((str: string, idx: number) => (
              <li key={idx} className="flex items-start gap-2">
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500 mt-0.5 shrink-0" />
                <span>{str}</span>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <h4 className="text-[10px] font-bold text-violet-500 uppercase tracking-wider font-mono mb-2">Considerations</h4>
          <ul className="text-[11px] text-slate-300 space-y-1.5">
            {((candidate.weaknesses?.length ? candidate.weaknesses : ['None reported'])).map((weak: string, idx: number) => (
              <li key={idx} className="flex items-start gap-2">
                <ShieldAlert className="h-3.5 w-3.5 text-violet-500 mt-0.5 shrink-0" />
                <span>{weak}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* Button Confirmation Controls */}
      <div className="pt-4 flex justify-end">
        <button
          onClick={onConfirm}
          disabled={isConfirming || isConfirmed}
          className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-semibold font-sans transition-all duration-200
            ${isConfirmed 
              ? 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20 cursor-default'
              : 'bg-cyan-500 text-slate-950 hover:bg-cyan-400 disabled:opacity-75 disabled:cursor-not-allowed shadow-glow-cyan'
            }
          `}
        >
          {isConfirming ? (
            <>
              <span className="h-3 w-3 border-2 border-slate-950 border-t-transparent rounded-full animate-spin"></span>
              <span>Provisioning in Database...</span>
            </>
          ) : isConfirmed ? (
            <>
              <CheckCircle2 className="h-4 w-4" />
              <span>Hired & Provisioned</span>
            </>
          ) : (
            <>
              <Send className="h-3.5 w-3.5" />
              <span>Confirm Hiring</span>
            </>
          )}
        </button>
      </div>
    </motion.div>
  );
};

export default CandidatePreview;


