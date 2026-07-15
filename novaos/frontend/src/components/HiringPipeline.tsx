import React from 'react';
import { motion } from 'framer-motion';
import { ChevronRight, CheckCircle2, CircleDot } from 'lucide-react';
import { PipelineStage } from '../services/dashboardService';

interface HiringPipelineProps {
  stages: PipelineStage[];
}

export const HiringPipeline: React.FC<HiringPipelineProps> = ({ stages }) => {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, ease: 'easeOut' }}
      className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm relative overflow-hidden"
    >
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-lg font-bold text-slate-200">Hiring Conversion Funnel</h3>
          <p className="text-xs text-slate-400">Conversion percentages and volume across recruitment phases.</p>
        </div>
        <div className="text-[10px] font-mono text-cyan-500 bg-cyan-500/10 px-2.5 py-1 rounded-lg border border-cyan-500/20">
          AUTO-UPDATED VIA FIRESTORE
        </div>
      </div>

      {/* Pipeline Grid wrapper */}
      <div className="grid grid-cols-1 md:grid-cols-5 gap-4 relative">
        {stages.map((stage, idx) => {
          const isLast = idx === stages.length - 1;
          
          return (
            <div key={stage.name} className="flex flex-col md:flex-row items-center gap-4 relative">
              {/* Step Detail Card */}
              <div className="w-full bg-slate-950 border border-slate-800 p-4 rounded-xl flex items-center justify-between md:flex-col md:items-start md:justify-center md:h-28 gap-2 hover:border-slate-700 transition-colors">
                <div className="flex items-center gap-2">
                  {idx < 3 ? (
                    <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                  ) : (
                    <CircleDot className="h-4 w-4 text-violet-500 shrink-0 animate-pulse" />
                  )}
                  <span className="text-xs font-semibold text-slate-200">{stage.name}</span>
                </div>
                
                <div className="text-right md:text-left">
                  <h4 className="text-lg font-extrabold text-slate-200">{stage.count}</h4>
                  <span className="text-[10px] text-slate-400 font-mono">
                    {idx === 0 ? 'Base Sourced' : `Conv Rate: ${stage.percentage}%`}
                  </span>
                </div>
              </div>

              {/* Arrow Connector between steps */}
              {!isLast && (
                <div className="flex items-center justify-center text-slate-400 rotate-90 md:rotate-0 my-1 md:my-0">
                  <ChevronRight className="h-5 w-5 text-slate-400" />
                </div>
              )}
            </div>
          );
        })}
      </div>
    </motion.div>
  );
};
export default HiringPipeline;
