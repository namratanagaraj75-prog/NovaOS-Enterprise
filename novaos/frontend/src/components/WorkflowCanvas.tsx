import React from 'react';
import WorkflowNode from './WorkflowNode';
import WorkflowEdge from './WorkflowEdge';
import { WorkflowStep } from '../services/workflowService';

interface WorkflowCanvasProps {
  steps: WorkflowStep[];
  currentStep: string;
}

export const WorkflowCanvas: React.FC<WorkflowCanvasProps> = ({ steps, currentStep }) => {
  return (
    <div className="flex flex-col items-center w-full py-2">
      {steps.map((step, idx) => {
        const isLast = idx === steps.length - 1;
        
        // Edge status calculation:
        // - 'completed': this node and the next node are both completed
        // - 'active': this node is completed, and the next node is running
        // - 'pending': next node is pending
        let edgeStatus: 'completed' | 'active' | 'pending' = 'pending';
        if (step.status === 'completed') {
          const nextStep = steps[idx + 1];
          if (nextStep) {
            if (nextStep.status === 'completed') {
              edgeStatus = 'completed';
            } else if (nextStep.status === 'running') {
              edgeStatus = 'active';
            }
          }
        }

        return (
          <React.Fragment key={step.id}>
            <WorkflowNode
              id={step.id}
              title={step.title}
              status={step.status}
              time={step.time}
              index={idx}
            />
            {!isLast && <WorkflowEdge status={edgeStatus} />}
          </React.Fragment>
        );
      })}
    </div>
  );
};

export default WorkflowCanvas;
