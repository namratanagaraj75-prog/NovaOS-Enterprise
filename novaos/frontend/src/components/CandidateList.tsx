import React, { useState } from 'react';
import { Search, Brain, Award } from 'lucide-react';

export interface CandidateDirectoryItem {
  id: string;
  name: string;
  role: string;
  score?: number;
}

interface CandidateListProps {
  candidates: CandidateDirectoryItem[];
  selectedId?: string;
  onSelect?: (id: string) => void;
}

export const CandidateList: React.FC<CandidateListProps> = ({ 
  candidates, 
  selectedId = '', 
  onSelect = () => undefined 
}) => {
  const [query, setQuery] = useState('');

  const filtered = candidates.filter(c => 
    c.name.toLowerCase().includes(query.toLowerCase()) ||
    c.role.toLowerCase().includes(query.toLowerCase())
  );

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-2xl flex flex-col gap-4 h-full shadow-sm">
      {/* Header controls */}
      <div className="flex items-center justify-between px-2">
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider font-mono">
          Profiles Directory
        </h3>
        <span className="text-[9px] text-slate-500 font-mono">GEMINI SCREENED</span>
      </div>

      {/* Search box input */}
      <div className="relative">
        <Search className="h-4 w-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
        <input
          type="text"
          placeholder="Filter candidates..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="w-full pl-9 pr-4 py-2 bg-slate-950 border border-slate-850 rounded-xl text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500/50 transition-all text-xs"
        />
      </div>

      {/* Directory items list wrapper */}
      <div className="flex-1 overflow-y-auto space-y-2 pr-1">
        {filtered.map((candidate) => {
          const isActive = candidate.id === selectedId;

          return (
            <button
              key={candidate.id}
              onClick={() => onSelect(candidate.id)}
              className={`w-full text-left p-3.5 rounded-xl transition-all duration-200 border flex items-center justify-between
                ${isActive 
                  ? 'bg-slate-950 border-cyan-500/30 text-slate-200 shadow-sm shadow-cyan-500/5' 
                  : 'bg-slate-950/40 border-slate-850/80 text-slate-400 hover:bg-slate-950/70 hover:border-slate-800'
                }
              `}
            >
              <div className="min-w-0">
                <h4 className={`text-xs font-bold font-sans truncate
                  ${isActive ? 'text-cyan-500' : 'text-slate-200'}
                `}>
                  {candidate.name}
                </h4>
                <span className="text-[10px] text-slate-400 block mt-0.5 truncate">{candidate.role}</span>
              </div>
              <div className="shrink-0 pl-3">
                <span className={`text-[9px] font-bold font-mono px-2 py-0.5 rounded flex items-center gap-1
                  ${(candidate.score || 0) >= 95 
                    ? 'bg-cyan-500/10 text-cyan-500 border border-cyan-500/20' 
                    : 'bg-violet-500/10 text-violet-500 border border-violet-500/20'
                  }
                `}>
                  {candidate.score || 0}%
                </span>
              </div>
            </button>
          );
        })}

        {filtered.length === 0 && (
          <div className="text-center py-8 text-[11px] text-slate-500 font-mono">
            NO MATCHES FOUND
          </div>
        )}
      </div>
    </div>
  );
};

export default CandidateList;
