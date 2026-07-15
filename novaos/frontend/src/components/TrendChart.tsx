import React from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, BarChart2 } from 'lucide-react';
import { 
  LineChart, 
  Line, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer,
  Legend
} from 'recharts';
import { TrendData } from '../services/dashboardService';

interface TrendChartProps {
  data: TrendData[];
}

export const TrendChart: React.FC<TrendChartProps> = ({ data }) => {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, delay: 0.2, ease: 'easeOut' }}
      className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm relative overflow-hidden"
    >
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-lg font-bold text-slate-200">Recruitment Performance Trends</h3>
          <p className="text-xs text-slate-400">Monthly candidate velocity, interview conversions, and hires count.</p>
        </div>
        <BarChart2 className="h-4.5 w-4.5 text-slate-400" />
      </div>

      <div className="h-72 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255, 255, 255, 0.03)" />
            <XAxis 
              dataKey="month" 
              stroke="rgba(255, 255, 255, 0.3)" 
              fontSize={11} 
              tickLine={false} 
              axisLine={false}
            />
            <YAxis 
              stroke="rgba(255, 255, 255, 0.3)" 
              fontSize={11} 
              tickLine={false} 
              axisLine={false}
            />
            <Tooltip 
              contentStyle={{ 
                backgroundColor: '#090d16', 
                borderColor: 'rgba(255, 255, 255, 0.08)', 
                borderRadius: '12px',
                color: '#fff',
                fontSize: '11px',
                fontFamily: 'monospace'
              }} 
            />
            <Legend 
              wrapperStyle={{ fontSize: '11px', paddingTop: '10px' }}
              iconSize={8}
            />
            <Line 
              type="monotone" 
              dataKey="candidates" 
              name="Candidates"
              stroke="#3b82f6" /* blue-500 */
              strokeWidth={2} 
              dot={{ r: 3 }}
              activeDot={{ r: 5 }}
            />
            <Line 
              type="monotone" 
              dataKey="interviews" 
              name="Interviews"
              stroke="#8b5cf6" /* violet-500 */
              strokeWidth={2} 
              dot={{ r: 3 }}
              activeDot={{ r: 5 }}
            />
            <Line 
              type="monotone" 
              dataKey="hires" 
              name="Hires"
              stroke="#06b6d4" /* cyan-500 */
              strokeWidth={2} 
              dot={{ r: 3 }}
              activeDot={{ r: 5 }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </motion.div>
  );
};
export default TrendChart;
// Standard colors mapped to chart: blue-500 (#3b82f6), violet-500 (#8b5cf6), cyan-500 (#06b6d4)
