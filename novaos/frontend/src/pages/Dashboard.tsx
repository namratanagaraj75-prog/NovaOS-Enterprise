import React, { useMemo } from 'react';
import { motion } from 'framer-motion';
import { AlertTriangle, CheckCircle2, FileText, Mail, Scale, UserCheck, Users, Workflow } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useAppContext } from '../context/AppContext';
import StatCard from '../components/StatCard';

const pendingStatuses = ['PENDING_MANAGER_APPROVAL','PENDING_FINANCE_APPROVAL','PENDING_LEGAL_APPROVAL','PENDING_CEO_APPROVAL'];
const label = (value: string) => value.replace(/_/g, ' ').toLowerCase().replace(/(^|\s)\S/g, letter => letter.toUpperCase());

export const Dashboard: React.FC = () => {
  const { user } = useAuth();
  const { hiringRequests, activities, loading } = useAppContext();
  const metrics = useMemo(() => ({
    total: hiringRequests.length,
    pending: hiringRequests.filter(item => pendingStatuses.includes(item.status)).length,
    approved: hiringRequests.filter(item => ['APPROVED','APPROVALS_COMPLETED','OFFER_GENERATED','EMAIL_SENDING','EMAIL_SENT','WORKFLOW_COMPLETED','EMPLOYEE_CREATED'].includes(item.status)).length,
    rejected: hiringRequests.filter(item => item.status === 'REJECTED').length,
    offers: hiringRequests.filter(item => item.emailStatus === 'SENT' || ['EMAIL_SENT','WORKFLOW_COMPLETED'].includes(item.status)).length,
    failed: hiringRequests.filter(item => item.emailStatus === 'FAILED' || item.candidateEmailNotification?.status === 'FAILED').length,
    legal: hiringRequests.filter(item => item.status === 'PENDING_LEGAL_APPROVAL').length,
    active: hiringRequests.filter(item => !['REJECTED','WORKFLOW_COMPLETED','EMPLOYEE_CREATED'].includes(item.status)).length,
  }), [hiringRequests]);

  const statusChart = useMemo(() => [
    ['Applied', hiringRequests.filter(item => ['DRAFT','AI_SCREENING'].includes(item.status)).length],
    ['Under Review', hiringRequests.filter(item => pendingStatuses.includes(item.status)).length],
    ['Approved', metrics.approved], ['Rejected', metrics.rejected], ['Offer Sent', metrics.offers],
  ] as Array<[string,number]>, [hiringRequests, metrics]);
  const approvalChart = useMemo(() => ['manager','finance','legal'].map(department => ({
    name: department === 'manager' ? 'Hiring Manager' : label(department),
    approved: hiringRequests.filter(item => (item as any)[`${department}ApprovalStatus`] === 'APPROVED').length,
    rejected: hiringRequests.filter(item => (item as any)[`${department}ApprovalStatus`] === 'REJECTED'
      || (item.status === 'REJECTED' && String(item.rejectedByDepartment || '').toLowerCase().includes(department))).length,
  })), [hiringRequests]);
  const maxStatus = Math.max(1, ...statusChart.map(([,count]) => count));

  const cards = [
    ['Total Candidates',metrics.total,Users,'Authoritative hiring requests'], ['Pending Approvals',metrics.pending,Workflow,'Across all departments'],
    ['Approved Candidates',metrics.approved,CheckCircle2,'Approval route completed'], ['Rejected Candidates',metrics.rejected,AlertTriangle,'Rejected at any stage'],
    ['Offers Sent',metrics.offers,Mail,'Successful email delivery'], ['Emails Failed',metrics.failed,AlertTriangle,'Delivery retry required'],
    ['Legal Reviews Pending',metrics.legal,Scale,'Awaiting Legal'], ['Active Workflows',metrics.active,UserCheck,'Not completed or terminated'],
  ] as const;

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 text-slate-200">
    <div className="border-b border-slate-800 pb-6"><p className="text-[10px] text-cyan-400 uppercase tracking-[0.22em] font-mono">Enterprise command overview</p><h1 className="text-3xl font-extrabold text-white mt-2">Hiring Governance Dashboard</h1><p className="text-sm text-slate-400 mt-1">Live hiring-request evidence for {user?.displayName || user?.email}.</p></div>
    {loading.app ? <div className="h-40 rounded-2xl bg-slate-900 animate-pulse" /> : <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">{cards.map(([title,value,icon,change],index) => <StatCard key={title} title={title} value={value} change={change} icon={icon} index={index} />)}</div>}

    <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
      <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6"><h2 className="font-bold text-white mb-5">Hiring Status</h2><div className="space-y-4">{statusChart.map(([name,count]) => <div key={name}><div className="flex justify-between text-xs mb-1.5"><span className="text-slate-400">{name}</span><strong>{count}</strong></div><div className="h-2 bg-slate-950 rounded-full overflow-hidden"><div className="h-full bg-gradient-to-r from-cyan-500 to-violet-500 rounded-full" style={{width:`${(count/maxStatus)*100}%`}} /></div></div>)}</div></section>
      <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6"><h2 className="font-bold text-white mb-5">Department Approval Activity</h2><div className="space-y-4">{approvalChart.map(item => <div key={item.name} className="grid grid-cols-3 items-center gap-3 text-xs"><span className="text-slate-300">{item.name}</span><span className="text-emerald-400 bg-emerald-500/5 border border-emerald-500/10 rounded-lg px-3 py-2">Approved {item.approved}</span><span className="text-rose-400 bg-rose-500/5 border border-rose-500/10 rounded-lg px-3 py-2">Rejected {item.rejected}</span></div>)}</div></section>
    </div>

    <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6"><div className="flex items-center gap-2 border-b border-slate-800 pb-4 mb-4"><FileText className="h-5 w-5 text-cyan-400" /><h2 className="font-bold text-white">Recent Activity</h2></div>
      {activities.length ? <div className="space-y-3">{activities.slice(0,10).map(item => { const request = hiringRequests.find(candidate => candidate.id === item.requestId); return <div key={item.id} className="flex items-start justify-between gap-4 bg-slate-950/60 border border-slate-800 rounded-xl p-4"><div><p className="text-sm text-slate-200"><strong>{request?.candidateName || 'Hiring request'}</strong> · {label(item.message)}</p><p className="text-xs text-slate-500 mt-1">{item.sub || `${item.actorName || 'NovaOS'} recorded this event.`}</p></div><span className="text-[9px] text-slate-500 font-mono shrink-0">{item.time}</span></div>})}</div> : <p className="py-8 text-center text-xs text-slate-500">No hiring activity has been recorded yet.</p>}
    </section>
  </motion.div>;
};
export default Dashboard;
