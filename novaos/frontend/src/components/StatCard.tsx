import React from 'react';
import { motion } from 'framer-motion';
import { Users, Clock, Send, UserCheck, TrendingUp, TrendingDown, LucideIcon } from 'lucide-react';
import { KpiData } from '../services/dashboardService';

const iconMap = {
  candidates: Users,
  approvals: Clock,
  offers: Send,
  employees: UserCheck,
};

const colorMap = {
  candidates: 'text-blue-500 bg-blue-500/10',
  approvals: 'text-violet-500 bg-violet-500/10',
  offers: 'text-cyan-500 bg-cyan-500/10',
  employees: 'text-emerald-500 bg-emerald-500/10',
};

type StatCardProps = {
  data?: KpiData;
  index?: number;
  title?: string;
  value?: string | number;
  change?: string;
  icon?: LucideIcon;
  gradient?: string;
};

export const StatCard: React.FC<StatCardProps> = ({ data, index = 0, title, value, change, icon, gradient }) => {
  const directData: KpiData = data || {
    title: title || '',
    value: String(value ?? ''),
    change: change || '',
    isPositive: !String(change || '').trim().startsWith('-'),
    type: 'candidates'
  };

  const IconComponent = icon || iconMap[directData.type];
  const colorClass = gradient ? 'text-cyan-500 bg-cyan-500/10' : colorMap[directData.type];

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: index * 0.05, ease: 'easeOut' }}
      whileHover={{ y: -2 }}
      className={`bg-slate-900 border border-slate-800 p-6 rounded-2xl flex flex-col justify-between h-40 shadow-sm relative overflow-hidden ${gradient || ''}`}
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
          {directData.title}
        </span>
        <div className={`p-2.5 rounded-xl ${colorClass}`}>
          <IconComponent className="h-5 w-5" />
        </div>
      </div>

      <div className="mt-4">
        <h3 className="text-3xl font-bold text-slate-200 tracking-tight">
          {directData.value}
        </h3>
        <p className="text-xs text-slate-400 flex items-center gap-1 mt-1.5">
          {directData.isPositive ? (
            <TrendingUp className="h-3.5 w-3.5 text-emerald-500" />
          ) : (
            <TrendingDown className="h-3.5 w-3.5 text-rose-500" />
          )}
          <span className={directData.isPositive ? 'text-emerald-500 font-medium' : 'text-rose-500 font-medium'}>
            {directData.change}
          </span>
        </p>
      </div>
    </motion.div>
  );
};
export default StatCard;