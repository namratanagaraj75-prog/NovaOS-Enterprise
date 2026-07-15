import React from 'react';
import { motion } from 'framer-motion';
import { Award, ArrowRight } from 'lucide-react';
import { Candidate } from '../services/recruitmentService';

interface CandidateCardProps {
  candidate: Candidate;
  onViewDetails: (candidate: Candidate) => void;
}

export const CandidateCard: React.FC<CandidateCardProps> = ({ candidate, onViewDetails }) => {
  const isHighMatch = candidate.matchScore >= 90;

  return (
    <motion.div
      layoutId={`card-${candidate.id}`}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      transition={{ type: 'spring', stiffness: 350, damping: 25 }}
      whileHover={{ y: -2 }}
      className="bg-slate-900 border border-slate-800 p-4 rounded-xl shadow-sm relative group flex flex-col gap-3.5"
    >
      {/* Top Section: Avatar & Info */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <div className="h-8 w-8 rounded-full bg-slate-950 border border-slate-800 flex items-center justify-center font-bold text-xs text-slate-200">
            {candidate.name.split(' ').map(n => n[0]).join('')}
          </div>
          <div>
            <h4 className="text-xs font-bold text-slate-200 group-hover:text-cyan-500 transition-colors">
              {candidate.name}
            </h4>
            <span className="text-[10px] text-slate-400 block mt-0.5">{candidate.role}</span>
          </div>
        </div>
        
        {/* Match score indicator */}
        <span className={`text-[9px] font-bold font-mono px-2 py-0.5 rounded-md flex items-center gap-1
          ${isHighMatch 
            ? 'bg-cyan-500/10 text-cyan-500 border border-cyan-500/20' 
            : 'bg-violet-500/10 text-violet-500 border border-violet-500/20'
          }
        `}>
          <Award className="h-3 w-3 shrink-0" />
          {candidate.matchScore}%
        </span>
      </div>

      {/* Footer Controls: Status Tag & View details button */}
      <div className="flex items-center justify-between pt-2 border-t border-slate-800/60 text-[10px] text-slate-400">
        <span className="text-[9px] text-slate-400 font-mono bg-slate-950 px-2 py-0.5 rounded border border-slate-800">
          {candidate.source}
        </span>
        
        <button
          onClick={() => onViewDetails(candidate)}
          className="flex items-center gap-1 text-cyan-500 hover:text-cyan-400 transition-colors font-medium group/btn"
        >
          <span>View Details</span>
          <ArrowRight className="h-3 w-3 transition-transform duration-200 group-hover/btn:translate-x-0.5" />
        </button>
      </div>
    </motion.div>
  );
};

export default CandidateCard;
