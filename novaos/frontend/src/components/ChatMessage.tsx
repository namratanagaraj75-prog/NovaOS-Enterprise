import React from 'react';
import { motion } from 'framer-motion';
import { Terminal, CheckCircle2 } from 'lucide-react';

export interface Message {
  id: string;
  sender: 'user' | 'ai';
  text: string;
  timestamp: string;
  steps?: string[];
}

interface ChatMessageProps {
  message: Message;
}

export const ChatMessage: React.FC<ChatMessageProps> = ({ message }) => {
  const isUser = message.sender === 'user';

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: 'easeOut' }}
      className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}
    >
      <div className={`max-w-xl rounded-2xl p-4 flex flex-col gap-2
        ${isUser 
          ? 'bg-slate-900 border border-slate-800 text-slate-200' 
          : 'bg-slate-900 border border-slate-800 text-slate-200'
        }
      `}>
        {/* Card Header info */}
        <div className="flex items-center justify-between gap-12 border-b border-slate-800/60 pb-1.5 mb-1">
          <span className={`text-[10px] font-mono font-bold tracking-wider
            ${isUser ? 'text-blue-500' : 'text-cyan-500'}
          `}>
            {isUser ? 'USER_SHELL' : 'GEMINI_COPILOT'}
          </span>
          <span className="text-[9px] text-slate-400 font-mono">{message.timestamp}</span>
        </div>

        {/* Message body */}
        <p className="text-xs leading-relaxed text-slate-200 whitespace-pre-line">
          {message.text}
        </p>

        {/* Cognitive execution steps */}
        {message.steps && message.steps.length > 0 && (
          <div className="mt-3 p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-2">
            <div className="flex items-center gap-1.5 text-[9px] font-bold text-slate-400 font-mono tracking-wider">
              <Terminal className="h-3 w-3 text-cyan-500" />
              <span>COGNITIVE TRACE LOGS</span>
            </div>
            <ul className="text-[10px] font-mono text-slate-400 space-y-1.5 pl-1.5">
              {message.steps.map((step, idx) => (
                <li key={idx} className="flex items-start gap-2">
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500 mt-0.5 shrink-0" />
                  <span>{step}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </motion.div>
  );
};

export default ChatMessage;
