import React from 'react';
import { Award, Mail, Globe, MapPin } from 'lucide-react';

interface CandidateProfileProps {
  name: string;
  role: string;
  email: string;
  source: string;
  status: string;
}

export const CandidateProfile: React.FC<CandidateProfileProps> = ({
  name,
  role,
  email,
  source,
  status
}) => {
  return (
    <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm relative overflow-hidden flex flex-col sm:flex-row sm:items-center justify-between gap-6">
      {/* Decorative top border */}
      <div className="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-blue-500 via-cyan-500 to-violet-500"></div>

      <div className="flex items-center gap-4">
        {/* Avatar */}
        <div className="h-14 w-14 rounded-full bg-slate-950 border border-slate-800 flex items-center justify-center font-bold text-lg text-slate-200 shrink-0">
          {name.split(' ').map(n => n[0]).join('')}
        </div>
        
        {/* Identity Details */}
        <div className="space-y-1.5 min-w-0">
          <h2 className="text-xl font-bold text-slate-200 truncate">{name}</h2>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-slate-400">
            <span className="flex items-center gap-1.5 min-w-0">
              <Mail className="h-3.5 w-3.5 text-slate-500 shrink-0" />
              <span className="truncate">{email}</span>
            </span>
            <span className="flex items-center gap-1.5">
              <Globe className="h-3.5 w-3.5 text-slate-500 shrink-0" />
              <span>{source}</span>
            </span>
          </div>
        </div>
      </div>

      {/* Right Column details: Status tag */}
      <div className="flex flex-row sm:flex-col sm:items-end justify-between items-center shrink-0 gap-3">
        <span className="text-[10px] text-slate-400 font-mono uppercase tracking-wider">INTELLIGENCE STAGE</span>
        <span className="text-xs font-bold text-cyan-500 bg-cyan-500/5 border border-cyan-500/10 px-3 py-1 rounded-lg">
          {status}
        </span>
      </div>
    </div>
  );
};

export default CandidateProfile;
