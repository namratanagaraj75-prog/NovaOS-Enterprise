import React from 'react';
import { motion } from 'framer-motion';
import { Check, Dot, Circle } from 'lucide-react';

interface WorkflowStepperProps {
  currentStep: number; // 0 to 4
  steps?: string[];
  status?: 'idle' | 'running' | 'completed' | 'failed';
}

const defaultSteps = [
  'Resume Received',
  'AI Screening',
  'Manager Approval',
  'Offer Letter',
  'Employee Created'
];

export const WorkflowStepper: React.FC<WorkflowStepperProps> = ({ 
  currentStep, 
  steps = defaultSteps,
  status = 'idle'
}) => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 15 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut' }}
      className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-sm relative overflow-hidden"
    >
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider font-mono">
            Automated Hiring Agent status
          </h3>
          <p className="text-[11px] text-slate-400">Current progress of autonomous workflow dispatcher.</p>
        </div>
        <span className={`text-[10px] font-mono font-semibold px-2 py-0.5 rounded
          ${status === 'completed' && 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20'}
          ${status === 'running' && 'bg-cyan-500/10 text-cyan-500 border border-cyan-500/20 animate-pulse'}
          ${status === 'idle' && 'bg-slate-850 text-slate-400 border border-slate-800'}
        `}>
          {status.toUpperCase()}
        </span>
      </div>

      {/* Steps Visual Progress Bar */}
      <div className="relative flex flex-col md:flex-row justify-between items-start md:items-center gap-6 md:gap-4">
        {steps.map((step, idx) => {
          const isCompleted = idx < currentStep;
          const isActive = idx === currentStep && status === 'running';
          const isDone = idx === currentStep && status === 'completed';
          const isPending = idx > currentStep;
          
          const isLast = idx === steps.length - 1;

          return (
            <div key={step} className="flex-1 flex flex-row md:flex-col items-center gap-4 relative w-full">
              {/* Connector line (desktop only) */}
              {!isLast && (
                <div className="hidden md:block absolute top-4 left-[60%] right-[-40%] h-[1px] bg-slate-800 z-0">
                  <div 
                    className="h-full bg-cyan-500 transition-all duration-500" 
                    style={{ width: isCompleted ? '100%' : '0%' }}
                  />
                </div>
              )}

              {/* Step indicator node */}
              <div className="relative z-10 shrink-0">
                {isCompleted || isDone ? (
                  <div className="h-8 w-8 rounded-full bg-emerald-500 text-slate-950 flex items-center justify-center font-bold">
                    <Check className="h-4.5 w-4.5" />
                  </div>
                ) : isActive ? (
                  <div className="h-8 w-8 rounded-full bg-cyan-500 text-slate-950 flex items-center justify-center font-bold animate-pulse">
                    <Dot className="h-6 w-6" />
                  </div>
                ) : (
                  <div className="h-8 w-8 rounded-full bg-slate-950 border border-slate-800 text-slate-400 flex items-center justify-center text-xs font-mono">
                    {idx + 1}
                  </div>
                )}
              </div>

              {/* Text content details */}
              <div className="text-left md:text-center md:mt-2">
                <h4 className={`text-xs font-bold font-sans
                  ${isCompleted || isDone ? 'text-emerald-500' : isActive ? 'text-cyan-500' : 'text-slate-400'}
                `}>
                  {step}
                </h4>
                <span className="text-[9px] text-slate-500 font-mono">
                  {isCompleted || isDone ? 'COMPLETED' : isActive ? 'ACTIVE AGENT' : 'PENDING'}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </motion.div>
  );
};

export default WorkflowStepper;
