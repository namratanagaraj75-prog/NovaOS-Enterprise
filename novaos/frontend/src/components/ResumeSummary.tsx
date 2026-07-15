import React from 'react';
import { FileText } from 'lucide-react';

interface ResumeSummaryProps {
  summary: string;
}

export const ResumeSummary: React.FC<ResumeSummaryProps> = ({ summary }) => {
  return (
    <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm space-y-3">
      {/* Title */}
      <h4 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono flex items-center gap-1.5 border-b border-slate-850/60 pb-3">
        <FileText className="h-4.5 w-4.5 text-blue-500" />
        <span>Parsed Resume Summary</span>
      </h4>

      {/* Code Text Block */}
      <div className="bg-slate-950 p-4 rounded-xl border border-slate-850/60 overflow-x-auto">
        <p className="text-[11px] font-mono text-slate-300 leading-relaxed whitespace-pre-wrap max-h-40 overflow-y-auto">
          {summary}
        </p>
      </div>
    </div>
  );
};

export default ResumeSummary;
