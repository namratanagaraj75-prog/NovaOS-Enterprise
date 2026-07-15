import React, { useState } from 'react';
import { Send, Sparkles } from 'lucide-react';

interface ChatInputProps {
  onSend: (message: string) => void;
  disabled?: boolean;
  placeholder?: string;
}

export const ChatInput: React.FC<ChatInputProps> = ({ onSend, disabled, placeholder }) => {
  const [val, setVal] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!val.trim() || disabled) return;
    onSend(val);
    setVal('');
  };

  return (
    <form onSubmit={handleSubmit} className="relative flex items-center w-full">
      <input
        type="text"
        disabled={disabled}
        value={val}
        onChange={(e) => setVal(e.target.value)}
        placeholder={placeholder || "Type a command or ask Gemini to vet applications..."}
        className="w-full pl-4 pr-16 py-3.5 bg-slate-950 border border-slate-800 rounded-xl text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500/50 transition-all text-xs disabled:opacity-70 disabled:cursor-not-allowed"
      />
      <div className="absolute right-2 flex items-center gap-1.5">
        <button
          type="submit"
          disabled={!val.trim() || disabled}
          className="p-2 bg-slate-900 border border-slate-800 text-slate-400 hover:text-cyan-500 hover:border-slate-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-all"
          title="Submit Instruction"
        >
          <Send className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
};

export default ChatInput;
