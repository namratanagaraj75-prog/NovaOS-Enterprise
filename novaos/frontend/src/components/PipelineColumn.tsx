import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Candidate } from '../services/recruitmentService';
import CandidateCard from './CandidateCard';

interface PipelineColumnProps {
  title: string;
  count: number;
  candidates: Candidate[];
  onViewDetails: (candidate: Candidate) => void;
  accentColor: string; // text-blue-500, etc.
}

export const PipelineColumn: React.FC<PipelineColumnProps> = ({ 
  title, 
  count, 
  candidates, 
  onViewDetails,
  accentColor
}) => {
  return (
    <div className="flex flex-col gap-4 w-full min-w-[250px] shrink-0 md:shrink">
      {/* Column header panel */}
      <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex items-center justify-between font-bold text-xs uppercase tracking-wider text-slate-200">
        <div className="flex items-center gap-2">
          <span className={`h-2 w-2 rounded-full ${accentColor.replace('text-', 'bg-')}`}></span>
          <span>{title}</span>
        </div>
        <span className="bg-slate-950 px-2 py-0.5 rounded-full text-[10px] text-slate-400 font-mono">
          {count}
        </span>
      </div>

      {/* Cards container wrapper */}
      <div className="bg-slate-950/60 border border-slate-800/80 p-3 rounded-2xl flex-1 flex flex-col gap-3 min-h-[500px]">
        <AnimatePresence mode="popLayout">
          {candidates.map((candidate) => (
            <CandidateCard
              key={candidate.id}
              candidate={candidate}
              onViewDetails={onViewDetails}
            />
          ))}
        </AnimatePresence>

        {candidates.length === 0 && (
          <div className="flex-1 flex flex-col items-center justify-center text-center p-6 border border-dashed border-slate-800 rounded-xl">
            <span className="text-[10px] font-mono text-slate-500">NO QUEUED CANDIDATES</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default PipelineColumn;
