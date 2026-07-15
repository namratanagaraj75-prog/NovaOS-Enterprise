import React from 'react';
import { motion } from 'framer-motion';

export const TypingIndicator: React.FC = () => {
  return (
    <div className="flex justify-start">
      <div className="bg-slate-900 border border-slate-800 text-slate-400 rounded-2xl p-4 flex flex-col gap-2 max-w-xs shadow-sm">
        <span className="text-[10px] font-mono font-bold tracking-wider text-cyan-500">GEMINI_COPILOT</span>
        <div className="flex items-center gap-1.5 py-1 pl-1">
          <motion.span 
            animate={{ y: [0, -5, 0] }}
            transition={{ duration: 0.6, repeat: Infinity, delay: 0 }}
            className="h-1.5 w-1.5 rounded-full bg-slate-400"
          />
          <motion.span 
            animate={{ y: [0, -5, 0] }}
            transition={{ duration: 0.6, repeat: Infinity, delay: 0.2 }}
            className="h-1.5 w-1.5 rounded-full bg-slate-400"
          />
          <motion.span 
            animate={{ y: [0, -5, 0] }}
            transition={{ duration: 0.6, repeat: Infinity, delay: 0.4 }}
            className="h-1.5 w-1.5 rounded-full bg-slate-400"
          />
          <span className="text-[11px] text-slate-400 pl-2">Copilot is thinking...</span>
        </div>
      </div>
    </div>
  );
};

export default TypingIndicator;
