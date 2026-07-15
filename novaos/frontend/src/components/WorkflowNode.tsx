import React from 'react';
import { motion } from 'framer-motion';
import { 
  FileText, 
  Cpu, 
  UserCheck, 
  FileCheck, 
  Mail, 
  UserPlus, 
  CheckCircle2,
  Clock,
  AlertTriangle,
  Play
} from 'lucide-react';

interface WorkflowNodeProps {
  id: string;
  title: string;
  status: 'completed' | 'running' | 'pending' | 'failed';
  time?: string;
  index: number;
}

const iconMap: Record<string, any> = {
  resume: FileText,
  screening: Cpu,
  approval: UserCheck,
  offer: FileCheck,
  email: Mail,
  employee: UserPlus,
  completed: CheckCircle2
};

export const WorkflowNode: React.FC<WorkflowNodeProps> = ({
  id,
  title,
  status,
  time,
  index
}) => {
  const Icon = iconMap[id] || FileText;

  // Set glows and text colors
  let cardClass = "border border-white/5 bg-slate-900/60 backdrop-blur-xl shadow-lg";
  let iconContainerClass = "bg-white/5 text-gray-500 border border-white/5";
  let statusBadge = null;
  let glowColor = "";

  if (status === 'completed') {
    cardClass = "border border-emerald-500/20 bg-emerald-950/10 backdrop-blur-xl shadow-glow-emerald";
    iconContainerClass = "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20";
    glowColor = "rgba(16, 185, 129, 0.15)";
    statusBadge = (
      <span className="flex items-center gap-1 text-[9px] font-mono text-emerald-400 font-bold bg-emerald-500/5 px-2 py-0.5 rounded border border-emerald-500/10">
        <CheckCircle2 className="h-3 w-3" />
        <span>COMPLETED</span>
      </span>
    );
  } else if (status === 'running') {
    cardClass = "border border-cyan-500/30 bg-cyan-950/10 backdrop-blur-xl shadow-glow-cyan animate-pulse";
    iconContainerClass = "bg-cyan-500/10 text-cyan-400 border border-cyan-500/20";
    glowColor = "rgba(6, 182, 212, 0.2)";
    statusBadge = (
      <span className="flex items-center gap-1 text-[9px] font-mono text-cyan-400 font-bold bg-cyan-500/5 px-2 py-0.5 rounded border border-cyan-500/10 animate-pulse">
        <Play className="h-2.5 w-2.5 fill-cyan-400" />
        <span>PROCESSING</span>
      </span>
    );
  } else if (status === 'failed') {
    cardClass = "border border-rose-500/20 bg-rose-950/10 backdrop-blur-xl shadow-glow-rose";
    iconContainerClass = "bg-rose-500/10 text-rose-400 border border-rose-500/20";
    glowColor = "rgba(244, 63, 94, 0.15)";
    statusBadge = (
      <span className="flex items-center gap-1 text-[9px] font-mono text-rose-400 font-bold bg-rose-500/5 px-2 py-0.5 rounded border border-rose-500/10">
        <AlertTriangle className="h-3 w-3" />
        <span>SUSPENDED</span>
      </span>
    );
  } else {
    statusBadge = (
      <span className="flex items-center gap-1 text-[9px] font-mono text-gray-500 font-medium bg-white/5 px-2 py-0.5 rounded border border-white/5">
        <Clock className="h-3 w-3" />
        <span>QUEUED</span>
      </span>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: index * 0.05 }}
      style={{ boxShadow: glowColor ? `0 0 25px 0 ${glowColor}` : 'none' }}
      className={`p-4.5 rounded-2xl flex items-center justify-between gap-4 transition-all duration-300 w-full relative overflow-hidden ${cardClass}`}
    >
      {/* Glow highlight overlay */}
      {status === 'running' && (
        <div className="absolute inset-0 bg-gradient-to-r from-transparent via-cyan-500/5 to-transparent -translate-x-full animate-[shimmer_2s_infinite] pointer-events-none" />
      )}

      <div className="flex items-center gap-3.5 min-w-0">
        <div className={`h-11 w-11 rounded-xl flex items-center justify-center shrink-0 transition-colors ${iconContainerClass}`}>
          <Icon className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <h4 className={`text-sm font-bold tracking-tight font-sans transition-colors
            ${status === 'completed' && 'text-emerald-400'}
            ${status === 'running' && 'text-cyan-400'}
            ${status === 'failed' && 'text-rose-400'}
            ${status === 'pending' && 'text-slate-400'}
          `}>
            {title}
          </h4>
          <span className="text-[10px] text-gray-500 font-mono mt-0.5 block">
            {time ? `Completed at ${time}` : 'Waiting on pipeline steps'}
          </span>
        </div>
      </div>

      <div className="shrink-0">
        {statusBadge}
      </div>
    </motion.div>
  );
};

export default WorkflowNode;
