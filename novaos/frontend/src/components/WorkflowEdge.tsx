import React from 'react';

interface WorkflowEdgeProps {
  status: 'completed' | 'active' | 'pending';
}

export const WorkflowEdge: React.FC<WorkflowEdgeProps> = ({ status }) => {
  let strokeColor = "rgba(255, 255, 255, 0.05)";
  let flowParticle = null;

  if (status === 'completed') {
    strokeColor = "rgba(16, 185, 129, 0.3)"; // Emerald line
  } else if (status === 'active') {
    strokeColor = "rgba(6, 182, 212, 0.2)"; // Cyan active line
    flowParticle = (
      <>
        {/* Animated flow path */}
        <line
          x1="12"
          y1="0"
          x2="12"
          y2="40"
          stroke="url(#edge-cyan-glow)"
          strokeWidth="3"
          strokeDasharray="8, 8"
          className="animate-[dash_1.5s_linear_infinite]"
        />
        {/* Moving glowing particle */}
        <circle cx="12" cy="0" r="3.5" fill="#22d3ee" className="animate-[slideDown_1s_infinite_ease-in-out]">
          <animate
            attributeName="cy"
            from="0"
            to="40"
            dur="1s"
            repeatCount="indefinite"
          />
        </circle>
      </>
    );
  }

  return (
    <div className="flex justify-center items-center h-10 w-full relative">
      <svg width="24" height="40" viewBox="0 0 24 40" fill="none" xmlns="http://www.w3.org/2000/svg" className="overflow-visible">
        <defs>
          <linearGradient id="edge-cyan-glow" x1="0" y1="0" x2="0" y2="40" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#0891b2" stopOpacity="0" />
            <stop offset="50%" stopColor="#22d3ee" stopOpacity="1" />
            <stop offset="100%" stopColor="#0891b2" stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* Background static line */}
        <line
          x1="12"
          y1="0"
          x2="12"
          y2="40"
          stroke={strokeColor}
          strokeWidth="2.5"
          strokeLinecap="round"
        />

        {flowParticle}
      </svg>
    </div>
  );
};

export default WorkflowEdge;
