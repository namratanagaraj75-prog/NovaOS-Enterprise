import React from 'react';
import { GitFork } from 'lucide-react';
import ApprovalInbox from '../components/ApprovalInbox';
import DashboardStatusCounts from '../components/DashboardStatusCounts';

export const ManagerDashboard: React.FC = () => (
  <div className="space-y-8 text-slate-200">
    <div className="border-b border-slate-800 pb-6">
      <h1 className="text-3xl font-extrabold text-white flex items-center gap-3">
        <GitFork className="h-7 w-7 text-violet-400" /> Manager Approval Workspace
      </h1>
      <p className="text-sm text-slate-400 mt-2">Approve only the governed hiring requests assigned to your verified role.</p>
    </div>
    <DashboardStatusCounts />
    <ApprovalInbox />
  </div>
);
export default ManagerDashboard;
