import React from 'react';
import { motion } from 'framer-motion';
import { Sparkles, Info, ShieldAlert, CheckCircle } from 'lucide-react';
import { AiInsight } from '../services/dashboardService';

interface InsightsCardProps {
  insights: AiInsight[];
}

export const InsightsCard: React.FC<InsightsCardProps> = ({ insights }) => {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, delay: 0.15, ease: 'easeOut' }}
      className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm relative overflow-hidden flex flex-col h-full animate-glow-pulse"
    >
      {/* Visual background highlight */}
      <div className="absolute top-0 right-0 w-24 h-24 bg-violet-500/5 rounded-full blur-2xl pointer-events-none"></div>

      <div className="flex items-center justify-between mb-5">
        <h3 className="text-lg font-bold text-slate-200 flex items-center gap-2">
          <Sparkles className="h-5 w-5 text-violet-500" />
          <span>AI Copilot Insights</span>
        </h3>
        <span className="text-[9px] font-mono text-violet-400 bg-violet-500/10 px-2 py-0.5 rounded border border-violet-500/20">
          GEMINI COGNITIVE LAYER
        </span>
      </div>

      <div className="space-y-4 flex-1 overflow-y-auto pr-1">
        {insights.map((insight, idx) => {
          return (
            <motion.div
              key={insight.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3, delay: idx * 0.05 }}
              className={`p-4 rounded-xl border flex gap-3.5
                ${insight.type === 'warning' && 'bg-slate-950/60 border-slate-800/80 text-slate-200'}
                ${insight.type === 'success' && 'bg-slate-950/60 border-slate-800/80 text-slate-200'}
                ${insight.type === 'info' && 'bg-slate-950/60 border-slate-800/80 text-slate-200'}
              `}
            >
              {/* Insight Icon type */}
              <div className="shrink-0 mt-0.5">
                {insight.type === 'warning' && (
                  <ShieldAlert className="h-4.5 w-4.5 text-rose-500" />
                )}
                {insight.type === 'success' && (
                  <CheckCircle className="h-4.5 w-4.5 text-emerald-500" />
                )}
                {insight.type === 'info' && (
                  <Info className="h-4.5 w-4.5 text-blue-500" />
                )}
              </div>

              {/* Text content details */}
              <div className="min-w-0">
                <h4 className="text-xs font-bold text-slate-200 mb-1 leading-snug">
                  {insight.title}
                </h4>
                <p className="text-[11px] text-slate-400 leading-relaxed">
                  {insight.message}
                </p>
              </div>
            </motion.div>
          );
        })}
      </div>
    </motion.div>
  );
};
export default InsightsCard;
