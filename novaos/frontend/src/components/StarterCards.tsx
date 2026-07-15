import React from 'react';
import { motion } from 'framer-motion';
import { Brain, FileSearch, Sparkles, UserPlus } from 'lucide-react';

interface StarterCardsProps {
  onSelectPrompt: (prompt: string) => void;
}

export const StarterCards: React.FC<StarterCardsProps> = ({ onSelectPrompt }) => {
  const cards = [
    {
      title: 'Vet Sophia Zhang',
      desc: 'Retrieves application details, scores skills compatibility, and outlines strengths.',
      prompt: 'Vet Candidate Sophia Zhang for AI Research Scientist role.',
      icon: FileSearch,
      color: 'text-blue-500'
    },
    {
      title: 'Active Workflows',
      desc: 'Lists background automated hiring steps registered in our database.',
      prompt: 'List active automation workflows.',
      icon: Brain,
      color: 'text-violet-500'
    },
    {
      title: 'Hiring Statistics',
      desc: 'Summarizes candidates conversion data and average onboarding speeds.',
      prompt: 'Summarize candidate pipeline stats.',
      icon: Sparkles,
      color: 'text-cyan-500'
    },
    {
      title: 'Create Employee',
      desc: 'Explains rules to provision credentials and setup Firebase authentication accounts.',
      prompt: 'Explain the onboarding automated process step-by-step.',
      icon: UserPlus,
      color: 'text-emerald-500'
    }
  ];

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-6">
      {cards.map((card, idx) => (
        <motion.button
          key={card.title}
          initial={{ opacity: 0, y: 15 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: idx * 0.05, ease: 'easeOut' }}
          whileHover={{ y: -2 }}
          onClick={() => onSelectPrompt(card.prompt)}
          className="bg-slate-900 border border-slate-800 p-4 rounded-xl text-left hover:border-slate-700 transition-all flex flex-col justify-between h-32 group"
        >
          <div className="flex items-center justify-between w-full">
            <span className="text-xs font-bold text-slate-200 group-hover:text-cyan-500 transition-colors">
              {card.title}
            </span>
            <card.icon className={`h-4.5 w-4.5 ${card.color}`} />
          </div>
          <p className="text-[11px] text-slate-400 leading-normal mt-2">
            {card.desc}
          </p>
        </motion.button>
      ))}
    </div>
  );
};

export default StarterCards;
