import React from 'react';
import { motion } from 'framer-motion';
import { Zap, Clock, ShieldCheck, AlertCircle } from 'lucide-react';

interface AutomationStatsProps {
  executionsToday: number;
  averageTime: string;
  automationRate: string;
  failedRuns: number;
}

export const AutomationStats: React.FC<AutomationStatsProps> = ({
  executionsToday,
  averageTime,
  automationRate,
  failedRuns
}) => {
  const statsList = [
    {
      title: 'Executions Today',
      value: String(executionsToday),
      icon: Zap,
      color: 'text-cyan-400',
      bgColor: 'bg-cyan-500/10 border-cyan-500/20'
    },
    {
      title: 'Average Runtime',
      value: averageTime,
      icon: Clock,
      color: 'text-violet-400',
      bgColor: 'bg-violet-500/10 border-violet-500/20'
    },
    {
      title: 'Automation Rate',
      value: automationRate,
      icon: ShieldCheck,
      color: 'text-emerald-400',
      bgColor: 'bg-emerald-500/10 border-emerald-500/20'
    },
    {
      title: 'Failed Runs',
      value: String(failedRuns),
      icon: AlertCircle,
      color: 'text-rose-400',
      bgColor: 'bg-rose-500/10 border-rose-500/20'
    }
  ];

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      {statsList.map((stat, idx) => (
        <motion.div
          key={stat.title}
          initial={{ opacity: 0, y: 15 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: idx * 0.05 }}
          className={`p-4 rounded-xl border bg-slate-900/60 backdrop-blur-xl flex flex-col justify-between h-24 shadow-sm relative overflow-hidden`}
        >
          <div className="flex justify-between items-start">
            <span className="text-[10px] text-gray-500 font-mono uppercase tracking-wider">
              {stat.title}
            </span>
            <div className={`p-1.5 rounded-lg border shrink-0 ${stat.bgColor}`}>
              <stat.icon className={`h-4 w-4 ${stat.color}`} />
            </div>
          </div>
          <div className="mt-2">
            <span className="text-xl font-extrabold text-white tracking-tight font-sans">
              {stat.value}
            </span>
          </div>
        </motion.div>
      ))}
    </div>
  );
};

export default AutomationStats;
