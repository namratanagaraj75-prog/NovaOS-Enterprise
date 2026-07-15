import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Terminal, CheckCircle2 } from 'lucide-react';
import { WorkflowLog } from '../services/workflowService';

interface ExecutionLogProps {
  logs: WorkflowLog[];
}

export const ExecutionLog: React.FC<ExecutionLogProps> = ({ logs }) => {
  return (
    <div className="bg-slate-900/60 border border-white/5 backdrop-blur-xl rounded-2xl p-5 shadow-lg flex flex-col h-[340px]">
      <div className="flex items-center gap-2 pb-4 border-b border-white/5 mb-4">
        <Terminal className="h-4.5 w-4.5 text-cyan-400" />
        <h3 className="text-xs font-bold text-slate-200 uppercase tracking-wider font-mono">
          Agent Execution Timeline
        </h3>
      </div>

      <div className="flex-1 overflow-y-auto pr-1 space-y-4 font-mono scrollbar-thin">
        <AnimatePresence initial={false}>
          {logs.length === 0 ? (
            <div className="text-[11px] text-gray-500 flex items-center justify-center h-full">
              No executions logged yet. Start workflow.
            </div>
          ) : (
            logs.map((log, index) => (
              <motion.div
                key={index + '-' + log.message}
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.3 }}
                className="flex gap-3 text-[11px] text-slate-350 items-start"
              >
                <span className="text-cyan-400/70 font-mono shrink-0 select-none bg-cyan-950/20 px-1.5 py-0.5 rounded border border-cyan-500/10">
                  {log.time}
                </span>
                <div className="flex items-start gap-1.5 min-w-0">
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500 mt-0.5 shrink-0" />
                  <span className="leading-relaxed break-words">{log.message}</span>
                </div>
              </motion.div>
            ))
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default ExecutionLog;
