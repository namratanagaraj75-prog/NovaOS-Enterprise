import React from 'react';
import { Cpu, RefreshCw, AlertTriangle, CheckCircle2 } from 'lucide-react';

interface AgentStatusProps {
  status: string; // Idle, Running, Paused, Completed
  currentJob: string;
  progress: number;
}

export const AgentStatus: React.FC<AgentStatusProps> = ({
  status,
  currentJob,
  progress
}) => {
  let statusBadge = "bg-white/5 text-gray-400 border border-white/10";
  let statusColorDot = "bg-gray-400";
  let statusText = status;

  if (status === 'Running') {
    statusBadge = "bg-cyan-500/10 text-cyan-400 border border-cyan-500/20";
    statusColorDot = "bg-cyan-400 animate-ping";
    statusText = "🟢 Running";
  } else if (status === 'Completed') {
    statusBadge = "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20";
    statusColorDot = "bg-emerald-400";
    statusText = "✓ Completed";
  } else if (status === 'Paused') {
    statusBadge = "bg-amber-500/10 text-amber-400 border border-amber-500/20";
    statusColorDot = "bg-amber-400 animate-pulse";
    statusText = "🟡 Paused";
  }

  return (
    <div className="bg-slate-900/60 border border-white/5 backdrop-blur-xl rounded-2xl p-5 shadow-lg space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Cpu className="h-4.5 w-4.5 text-cyan-400" />
          <h3 className="text-xs font-bold text-slate-200 uppercase tracking-wider font-mono">
            Orchestrator Agent Status
          </h3>
        </div>
        <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded flex items-center gap-1.5 ${statusBadge}`}>
          <span className={`h-1.5 w-1.5 rounded-full ${statusColorDot}`} />
          <span>{statusText.toUpperCase()}</span>
        </span>
      </div>

      <div className="h-[1px] bg-white/5 w-full" />

      {/* Info Details */}
      <div className="grid grid-cols-2 gap-4 text-xs font-mono">
        <div className="space-y-1">
          <span className="text-[10px] text-gray-500 uppercase">Current Execution Job</span>
          <span className="text-slate-200 font-bold block truncate">{currentJob}</span>
        </div>
        <div className="space-y-1 text-right">
          <span className="text-[10px] text-gray-500 uppercase block">Engine Load</span>
          <span className="text-cyan-400 font-bold block">14.8 TFLOPS</span>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="space-y-2 pt-1">
        <div className="flex justify-between items-center text-[10px] font-mono">
          <span className="text-gray-500 uppercase">Process Progress</span>
          <span className="text-cyan-400 font-bold">{progress}%</span>
        </div>
        <div className="h-2 bg-slate-950 rounded-full overflow-hidden border border-white/5 relative">
          <div 
            className="h-full bg-gradient-to-r from-cyan-500 via-violet-500 to-emerald-500 transition-all duration-500 rounded-full" 
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>
    </div>
  );
};

export default AgentStatus;
