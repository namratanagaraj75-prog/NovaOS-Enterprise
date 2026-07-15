import React from 'react';
import { CheckCircle2, ShieldAlert } from 'lucide-react';

interface StrengthWeaknessProps {
  strengths: string[];
  weaknesses: string[];
}

export const StrengthWeakness: React.FC<StrengthWeaknessProps> = ({ 
  strengths, 
  weaknesses 
}) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm">
      {/* 1. Strengths Panel */}
      <div className="space-y-3">
        <h4 className="text-[10px] font-bold text-emerald-500 uppercase tracking-wider font-mono flex items-center gap-1.5">
          <CheckCircle2 className="h-4.5 w-4.5" />
          <span>Core Capabilities</span>
        </h4>
        <ul className="text-[11px] text-slate-300 space-y-2 bg-slate-950/40 p-4 rounded-xl border border-slate-850/60 leading-relaxed">
          {strengths.map((str, idx) => (
            <li key={idx} className="flex items-start gap-2.5">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 mt-1.5 shrink-0" />
              <span>{str}</span>
            </li>
          ))}
        </ul>
      </div>

      {/* 2. Considerations Panel */}
      <div className="space-y-3">
        <h4 className="text-[10px] font-bold text-violet-500 uppercase tracking-wider font-mono flex items-center gap-1.5">
          <ShieldAlert className="h-4.5 w-4.5" />
          <span>Vetting Considerations</span>
        </h4>
        <ul className="text-[11px] text-slate-300 space-y-2 bg-slate-950/40 p-4 rounded-xl border border-slate-850/60 leading-relaxed">
          {weaknesses.map((weak, idx) => (
            <li key={idx} className="flex items-start gap-2.5">
              <span className="h-1.5 w-1.5 rounded-full bg-violet-500 mt-1.5 shrink-0" />
              <span>{weak}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
};

export default StrengthWeakness;
