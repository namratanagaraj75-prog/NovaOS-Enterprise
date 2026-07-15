import React from 'react';
import { Sparkles, DollarSign, UserCheck, Play } from 'lucide-react';

interface RecommendationCardProps {
  recommendation: string;
  recommendedSalary: string;
  recommendedInterviewer: string;
  onApprove?: () => void;
  isApproving?: boolean;
}

export const RecommendationCard: React.FC<RecommendationCardProps> = ({
  recommendation,
  recommendedSalary,
  recommendedInterviewer,
  onApprove,
  isApproving = false
}) => {
  return (
    <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm space-y-4 relative overflow-hidden">
      {/* Glow effect on hover or if approving */}
      {isApproving && (
        <div className="absolute inset-0 bg-gradient-to-r from-transparent via-cyan-500/5 to-transparent -translate-x-full animate-[shimmer_2s_infinite] pointer-events-none" />
      )}

      {/* Title */}
      <h4 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono flex items-center gap-1.5 border-b border-slate-850/60 pb-3">
        <Sparkles className="h-4.5 w-4.5 text-cyan-500" />
        <span>Gemini Hiring Directive</span>
      </h4>

      {/* Narrative Vetting Recommendation */}
      <p className="text-xs text-slate-300 leading-relaxed bg-slate-950 p-4 rounded-xl border border-slate-850/60 font-mono">
        {recommendation}
      </p>

      {/* Salary and Interviewer Parameters Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs font-sans">
        <div className="flex items-center justify-between p-3.5 bg-slate-950 border border-slate-850/60 rounded-xl">
          <span className="text-slate-400 flex items-center gap-1.5">
            <DollarSign className="h-4 w-4 text-cyan-500" />
            <span>Target Compensation</span>
          </span>
          <span className="font-bold text-slate-200">{recommendedSalary}</span>
        </div>
        <div className="flex items-center justify-between p-3.5 bg-slate-950 border border-slate-850/60 rounded-xl">
          <span className="text-slate-400 flex items-center gap-1.5">
            <UserCheck className="h-4 w-4 text-violet-500" />
            <span>Assigned Panel Interviewer</span>
          </span>
          <span className="font-bold text-slate-200">{recommendedInterviewer}</span>
        </div>
      </div>

      {/* Primary Action Button to Approve & Trigger Onboarding */}
      {onApprove && (
        <div className="pt-2">
          <button
            onClick={onApprove}
            disabled={isApproving}
            className="w-full py-3.5 bg-gradient-to-r from-cyan-500 to-violet-600 hover:from-cyan-400 hover:to-violet-500 disabled:opacity-50 text-slate-950 hover:text-white font-bold rounded-xl text-xs flex items-center justify-center gap-2.5 transition-all duration-300 shadow-glow-cyan"
          >
            <Play className="h-4 w-4 fill-current shrink-0" />
            <span>{isApproving ? 'Authorizing Pipeline Workflow...' : 'Approve Match & Trigger Automation'}</span>
          </button>
        </div>
      )}
    </div>
  );
};

export default RecommendationCard;

