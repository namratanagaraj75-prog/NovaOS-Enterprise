import React from 'react';
import { DollarSign } from 'lucide-react';
import ApprovalInbox from '../components/ApprovalInbox';
import DashboardStatusCounts from '../components/DashboardStatusCounts';

export const FinanceDashboard: React.FC = () => (
  <div className="space-y-8 text-slate-200">
    <div className="border-b border-slate-800 pb-6">
      <h1 className="text-3xl font-extrabold text-white flex items-center gap-3">
        <DollarSign className="h-7 w-7 text-emerald-400" /> Finance Approval Workspace
      </h1>
      <p className="text-sm text-slate-400 mt-2">Review policy evidence and compensation before authorizing offer generation.</p>
    </div>
    <DashboardStatusCounts />
    <ApprovalInbox />
  </div>
);
export default FinanceDashboard;
