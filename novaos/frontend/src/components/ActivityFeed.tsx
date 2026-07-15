import React from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, AlertTriangle, HelpCircle, Terminal } from 'lucide-react';
import { ActivityItem } from '../services/dashboardService';

interface ActivityFeedProps {
  activities: ActivityItem[];
}

export const ActivityFeed: React.FC<ActivityFeedProps> = ({ activities }) => {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, delay: 0.1, ease: 'easeOut' }}
      className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm relative overflow-hidden flex flex-col h-full"
    >
      <div className="flex items-center justify-between mb-5">
        <div>
          <h3 className="text-lg font-bold text-slate-200">Recent AI Activity</h3>
          <p className="text-xs text-slate-400">Chronological logs of actions performed by Gemini agents.</p>
        </div>
        <Terminal className="h-4.5 w-4.5 text-slate-400" />
      </div>

      <div className="flex-1 overflow-y-auto space-y-4 pr-1">
        {activities.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-slate-500 font-sans h-full">
            <Terminal className="h-10 w-10 mb-3 text-slate-700 animate-pulse" />
            <p className="text-xs font-semibold">No recent AI activities</p>
            <p className="text-[10px] text-slate-500 mt-1">Live agent logs will appear here</p>
          </div>
        ) : (
          activities.map((activity, idx) => {
            return (
              <motion.div
                key={activity.id}
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.3, delay: idx * 0.05 }}
                className="flex gap-3 bg-slate-950 p-4 rounded-xl border border-slate-800 hover:border-slate-700 transition-colors"
              >
                {/* Event Status Indicator */}
                <div className="shrink-0 mt-0.5">
                  {activity.status === 'completed' && (
                    <CheckCircle2 className="h-4.5 w-4.5 text-emerald-500" />
                  )}
                  {activity.status === 'warning' && (
                    <AlertTriangle className="h-4.5 w-4.5 text-rose-500" />
                  )}
                  {activity.status === 'info' && (
                    <HelpCircle className="h-4.5 w-4.5 text-blue-500" />
                  )}
                </div>

                {/* Event Content */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-4 mb-1">
                    <h4 className="text-xs font-bold text-slate-200 truncate">
                      {activity.candidateName}
                    </h4>
                    <span className="text-[10px] text-slate-400 font-mono shrink-0">
                      {activity.timestamp}
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-400 leading-relaxed">
                    {activity.action}
                  </p>
                  <span className="text-[9px] font-semibold text-slate-400 font-mono block mt-1.5 uppercase">
                    {activity.position}
                  </span>
                </div>

                {/* Matching Score badge */}
                {activity.score !== undefined && (
                  <div className="shrink-0 flex items-center justify-center">
                    <span className={`text-[10px] font-bold font-mono px-2 py-0.5 rounded
                      ${activity.score >= 80 
                        ? 'bg-cyan-500/10 text-cyan-500 border border-cyan-500/20' 
                        : 'bg-rose-500/10 text-rose-500 border border-rose-500/20'
                      }
                    `}>
                      {activity.score}%
                    </span>
                  </div>
                )}
              </motion.div>
            );
          })
        )}
      </div>
    </motion.div>
  );
};
export default ActivityFeed;
