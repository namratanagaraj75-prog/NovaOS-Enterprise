import React from 'react';
import { motion } from 'framer-motion';
import { Award, Target, MessageSquare, Compass, Heart } from 'lucide-react';
import { SkillScore } from '../services/intelligenceService';

const iconMap: Record<string, any> = {
  'Technical Skills': Target,
  'Communication': MessageSquare,
  'Leadership': Compass,
  'Culture Fit': Heart,
};

const colorMap: Record<string, string> = {
  'Technical Skills': 'bg-blue-500 text-blue-500',
  'Communication': 'bg-cyan-500 text-cyan-500',
  'Leadership': 'bg-violet-500 text-violet-500',
  'Culture Fit': 'bg-emerald-500 text-emerald-500',
};

interface ScoreCardProps {
  overallScore: number;
  skills: SkillScore[];
}

export const ScoreCard: React.FC<ScoreCardProps> = ({ overallScore, skills }) => {
  return (
    <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm grid grid-cols-1 md:grid-cols-3 gap-6">
      {/* 1. Overall Score widget */}
      <div className="flex flex-col items-center justify-center text-center p-4 bg-slate-950 rounded-xl border border-slate-850/65 md:col-span-1 min-h-[180px]">
        <span className="text-[10px] text-slate-400 font-mono uppercase tracking-wider">GEMINI VETTING SCORE</span>
        <div className="relative flex items-center justify-center my-4 h-24 w-24">
          {/* Circular glow indicator */}
          <div className="absolute inset-0 bg-cyan-500/5 rounded-full blur-xl animate-pulse" />
          <div className="absolute inset-0 rounded-full border-2 border-slate-850 flex flex-col items-center justify-center z-10">
            <h3 className="text-3xl font-extrabold text-slate-200 tracking-tight">{overallScore}%</h3>
            <span className="text-[8px] text-slate-400 font-mono mt-0.5">COMPATIBLE</span>
          </div>
        </div>
      </div>

      {/* 2. Skills Progress Bars */}
      <div className="md:col-span-2 flex flex-col justify-center space-y-4">
        <h4 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider font-mono">Competence Breakdown</h4>
        
        <div className="space-y-3.5">
          {skills.map((skill) => {
            const Icon = iconMap[skill.name] || Target;
            const colors = colorMap[skill.name] || 'bg-cyan-500 text-cyan-500';
            const barBgColor = colors.split(' ')[0];
            const textAccentColor = colors.split(' ')[1];

            return (
              <div key={skill.name} className="space-y-1.5">
                <div className="flex items-center justify-between text-xs font-semibold">
                  <div className="flex items-center gap-2 text-slate-300">
                    <Icon className={`h-4 w-4 ${textAccentColor}`} />
                    <span>{skill.name}</span>
                  </div>
                  <span className="font-mono text-slate-200">{skill.score}%</span>
                </div>
                
                {/* Horizontal Progress Container */}
                <div className="w-full bg-slate-950 h-2 rounded-full overflow-hidden border border-slate-850/30">
                  <motion.div
                    initial={{ width: 0 }}
                    animate={{ width: `${skill.score}%` }}
                    transition={{ duration: 0.8, ease: 'easeOut' }}
                    className={`h-full rounded-full ${barBgColor}`}
                  />
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default ScoreCard;
