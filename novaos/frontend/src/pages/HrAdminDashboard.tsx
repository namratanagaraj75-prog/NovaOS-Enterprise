import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { AlertTriangle, Award, CheckCircle2, FileText, GitBranch, Mail, Plus, RefreshCw, UserCheck, Users, XCircle } from 'lucide-react';
import { useAppContext } from '../context/AppContext';
import { HiringRequest } from '../services/hiringRequestService';
import { PipelineStage, TrendData } from '../services/dashboardService';
import { normalizeDate, formatNormalizedDate } from '../lib/dateUtils';
import StatCard from '../components/StatCard';
import TrendChart from '../components/TrendChart';
import HiringPipeline from '../components/HiringPipeline';
import ActivityFeed from '../components/ActivityFeed';

const pendingStatuses = ['PENDING_MANAGER_APPROVAL', 'PENDING_FINANCE_APPROVAL', 'PENDING_LEGAL_APPROVAL', 'PENDING_CEO_APPROVAL'];
const approvedStatuses = ['APPROVED', 'APPROVALS_COMPLETED', 'GENERATING_OFFER', 'OFFER_GENERATED', 'EMAIL_SENDING', 'EMAIL_SENT', 'WORKFLOW_COMPLETED', 'EMPLOYEE_CREATED'];
const hiredStatuses = ['WORKFLOW_COMPLETED', 'EMPLOYEE_CREATED'];
const hiringEventPattern = /(REQUEST|CANDIDATE|HIRING|MANAGER|FINANCE|LEGAL|APPROVAL|REJECT|OFFER|DOCUMENT|PDF|EMAIL|EMPLOYEE|POLICY|ROUTED)/;

const friendlyAction = (action: string) => {
  const names: Record<string, string> = {
    REQUEST_CREATED: 'Submitted for hiring', MANAGER_APPROVED: 'Approved by Hiring Manager',
    FINANCE_APPROVED: 'Approved by Finance', LEGAL_APPROVED: 'Legal review completed',
    OFFER_GENERATED: 'Offer letter generated', OFFER_EMAIL_SENT: 'Offer email sent', EMAIL_SENT: 'Offer email sent',
    EMPLOYEE_CREATED: 'Candidate hired successfully', CANDIDATE_REJECTED: 'Candidate rejected',
    HIRING_MANAGER_REJECTED: 'Rejected by Hiring Manager', FINANCE_REJECTED: 'Rejected by Finance',
    LEGAL_REJECTED: 'Rejected by Legal', HR_REJECTED: 'Rejected by HR', APPROVALS_COMPLETED: 'All approvals completed',
  };
  return names[action] || action.replace(/_/g, ' ').toLowerCase().replace(/(^|\s)\S/g, letter => letter.toUpperCase());
};

const departmentFor = (action: string, event: any, request: HiringRequest) => {
  const role = String(event.actorRole || '').replace(/_/g, ' ');
  if (role) return role;
  if (action.includes('MANAGER')) return 'Hiring Manager';
  if (action.includes('FINANCE')) return 'Finance';
  if (action.includes('LEGAL') || action.includes('POLICY')) return 'Legal';
  if (action.includes('EMAIL') || action.includes('OFFER') || action.includes('DOCUMENT')) return 'HR Operations';
  return request.department || 'HR';
};

export const HrAdminDashboard: React.FC = () => {
  const navigate = useNavigate();
  const { hiringRequests, loading, backendOnline, lastSync, refreshDashboard } = useAppContext();

  const metrics = useMemo(() => ({
    total: hiringRequests.length,
    pending: hiringRequests.filter(item => pendingStatuses.includes(item.status)).length,
    approved: hiringRequests.filter(item => approvedStatuses.includes(item.status)).length,
    rejected: hiringRequests.filter(item => item.status === 'REJECTED').length,
    generated: hiringRequests.filter(item => Boolean(item.pdfGeneratedAt || item.offerLetterStatus === 'GENERATED')).length,
    sent: hiringRequests.filter(item => item.emailStatus === 'SENT').length,
    hired: hiringRequests.filter(item => hiredStatuses.includes(item.status)).length,
  }), [hiringRequests]);

  const trend = useMemo<TrendData[]>(() => {
    const months = Array.from({ length: 6 }, (_, offset) => {
      const date = new Date(); date.setDate(1); date.setMonth(date.getMonth() - (5 - offset));
      return { key: `${date.getFullYear()}-${date.getMonth()}`, month: date.toLocaleDateString('en-US', { month: 'short' }), candidates: 0, approved: 0, rejected: 0, hires: 0 };
    });
    for (const request of hiringRequests) {
      const created = normalizeDate(request.createdAt); if (!created) continue;
      const bucket = months.find(item => item.key === `${created.getFullYear()}-${created.getMonth()}`); if (!bucket) continue;
      bucket.candidates++; if (approvedStatuses.includes(request.status)) bucket.approved++;
      if (request.status === 'REJECTED') bucket.rejected++; if (hiredStatuses.includes(request.status)) bucket.hires++;
    }
    return months.map(({ key, ...item }) => item);
  }, [hiringRequests]);

  const funnel = useMemo<PipelineStage[]>(() => {
    const total = Math.max(1, hiringRequests.length);
    const reached = (test: (item: HiringRequest) => boolean) => hiringRequests.filter(test).length;
    const stages: Array<[string, number]> = [
      ['Applied', hiringRequests.length],
      ['Manager Review', reached(item => Boolean(item.managerApprovalStatus))],
      ['Finance Review', reached(item => Boolean(item.financeApprovalStatus))],
      ['Legal Review', reached(item => Boolean(item.legalApprovalStatus))],
      ['Offer Sent', reached(item => item.emailStatus === 'SENT')],
      ['Hired', reached(item => hiredStatuses.includes(item.status))],
    ];
    return stages.map(([name, count]) => ({ name, stage: name, count, percentage: Math.round((count / total) * 100) }));
  }, [hiringRequests]);

  const activity = useMemo(() => hiringRequests.flatMap(request => (request.activityHistory || []).map((event: any, index) => ({ request, event, index })))
    .filter(({ event }) => hiringEventPattern.test(String(event.action || event.eventType || '').toUpperCase()))
    .sort((a, b) => (normalizeDate(b.event.timestamp)?.getTime() || 0) - (normalizeDate(a.event.timestamp)?.getTime() || 0))
    .slice(0, 3).map(({ request, event, index }) => {
      const action = String(event.action || event.eventType || 'HIRING_EVENT').toUpperCase();
      const rejected = action.includes('REJECT') || action.includes('FAILED');
      return { id: `${request.id}-${action}-${index}`, candidateName: request.candidateName, action: friendlyAction(action),
        position: departmentFor(action, event, request), timestamp: formatNormalizedDate(event.timestamp),
        status: rejected ? 'warning' as const : 'completed' as const };
    }), [hiringRequests]);

  const recent = useMemo(() => [...hiringRequests].sort((a, b) => (normalizeDate(b.createdAt)?.getTime() || 0) - (normalizeDate(a.createdAt)?.getTime() || 0)).slice(0, 5), [hiringRequests]);
  const offers = useMemo(() => ({ generated: metrics.generated, sent: metrics.sent,
    failed: hiringRequests.filter(item => item.emailStatus === 'FAILED').length,
    pending: hiringRequests.filter(item => approvedStatuses.includes(item.status) && !['SENT', 'FAILED'].includes(item.emailStatus)).length }), [hiringRequests, metrics]);
  const rejections = useMemo(() => {
    const source = (item: HiringRequest) => String(item.rejectedByDepartment || '').toUpperCase();
    return { total: metrics.rejected, hr: hiringRequests.filter(item => item.status === 'REJECTED' && ['HR','HR_ADMIN'].includes(source(item))).length,
      manager: hiringRequests.filter(item => item.status === 'REJECTED' && (source(item).includes('MANAGER') || item.managerApprovalStatus === 'REJECTED')).length,
      finance: hiringRequests.filter(item => item.status === 'REJECTED' && (source(item) === 'FINANCE' || item.financeApprovalStatus === 'REJECTED')).length,
      legal: hiringRequests.filter(item => item.status === 'REJECTED' && (source(item) === 'LEGAL' || item.legalApprovalStatus === 'REJECTED')).length };
  }, [hiringRequests, metrics.rejected]);

  if (loading.app) return <div className="space-y-6 animate-pulse"><div className="h-12 bg-slate-900 rounded-xl w-1/3"/><div className="grid grid-cols-4 gap-5">{[1,2,3,4].map(item => <div key={item} className="h-28 bg-slate-900 rounded-2xl" />)}</div><div className="h-96 bg-slate-900 rounded-2xl" /></div>;

  const cards = [
    ['Total Candidates', metrics.total, Users, 'All hiring requests'], ['Pending Review', metrics.pending, GitBranch, 'Department review required'],
    ['Approved', metrics.approved, CheckCircle2, 'Approval route completed'], ['Rejected', metrics.rejected, XCircle, 'Rejected at any stage'],
    ['Offers Generated', metrics.generated, FileText, 'PDF documents created'], ['Offers Sent', metrics.sent, Mail, 'Email delivered'],
    ['Hired', metrics.hired, UserCheck, 'Employee creation completed'],
  ] as const;

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 bg-slate-950 text-slate-200">
    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6"><div><p className="text-[10px] text-cyan-400 uppercase tracking-[0.22em] font-mono">Hiring overview</p><h1 className="text-3xl font-extrabold text-white mt-2">HR Portal</h1><p className="text-gray-400 text-sm mt-1">Candidate, approval, offer, rejection, and hiring information only.</p></div><div className="flex items-center gap-3"><button onClick={() => navigate('/chat')} className="flex items-center gap-2 bg-cyan-500 text-slate-950 px-4 py-2.5 rounded-xl text-xs font-bold"><Plus className="h-4 w-4" />New Candidate</button><button onClick={refreshDashboard} className="p-2.5 bg-slate-900 border border-slate-800 rounded-xl"><RefreshCw className="h-4 w-4" /></button><span className={`text-[9px] font-mono ${backendOnline ? 'text-emerald-400' : 'text-rose-400'}`}>{backendOnline ? 'HIRING DATA CONNECTED' : 'BACKEND OFFLINE'}{lastSync ? ` · ${formatNormalizedDate(lastSync)}` : ''}</span></div></div>

    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">{cards.map(([title, value, icon, change], index) => <StatCard key={title} title={title} value={value} change={change} icon={icon} index={index} />)}</div>
    <div className="grid grid-cols-1 xl:grid-cols-3 gap-8"><div className="xl:col-span-2"><TrendChart data={trend} /></div><ActivityFeed activities={activity} title="Recent Hiring Activity" description="Candidate approvals, rejections, legal reviews, documents, and email events only." /></div>
    <HiringPipeline stages={funnel} />

    <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <section className="xl:col-span-2 bg-slate-900 border border-slate-800 p-6 rounded-2xl"><div className="flex justify-between mb-5"><div><h2 className="font-bold text-white">Recent Candidates</h2><p className="text-xs text-slate-400 mt-1">Newest records from the shared hiring-request collection.</p></div><button onClick={() => navigate('/hiring-requests')} className="text-xs text-cyan-400">View all →</button></div>{recent.length ? <div className="space-y-2">{recent.map(item => <button key={item.id} onClick={() => navigate('/hiring-requests/' + item.id)} className="w-full text-left bg-slate-950 border border-slate-800 hover:border-cyan-500/20 rounded-xl p-4 grid grid-cols-2 md:grid-cols-5 gap-3 items-center"><strong className="text-sm text-white">{item.candidateName}</strong><span className="text-xs text-slate-400">{item.jobTitle}</span><span className="text-[10px] font-mono text-cyan-400">{item.status.replace(/_/g, ' ')}</span><span className="text-xs text-slate-400">{item.riskLevel ? `${item.riskLevel} risk` : 'Risk not assessed'}</span><span className="text-[9px] text-slate-500 md:text-right">{formatNormalizedDate(item.createdAt)}</span></button>)}</div> : <p className="py-10 text-center text-xs text-slate-500">No hiring candidates are available.</p>}</section>
      <div className="space-y-6">
        <section className="bg-slate-900 border border-slate-800 p-6 rounded-2xl"><h2 className="font-bold text-white flex items-center gap-2"><Award className="h-4 w-4 text-amber-400" />Offer Status</h2><div className="grid grid-cols-2 gap-3 mt-4">{Object.entries(offers).map(([name,value]) => <div key={name} className="bg-slate-950 border border-slate-800 rounded-xl p-3"><p className="text-[9px] uppercase font-mono text-slate-500">{name}</p><strong className="text-xl text-white">{value}</strong></div>)}</div></section>
        <section className="bg-slate-900 border border-slate-800 p-6 rounded-2xl"><h2 className="font-bold text-white flex items-center gap-2"><AlertTriangle className="h-4 w-4 text-rose-400" />Rejections</h2><div className="space-y-2 mt-4">{Object.entries(rejections).map(([name,value]) => <div key={name} className="flex justify-between text-xs bg-slate-950 border border-slate-800 rounded-lg px-3 py-2"><span className="text-slate-400">{name === 'total' ? 'Total Rejected' : `Rejected by ${name === 'manager' ? 'Manager' : name.toUpperCase()}`}</span><strong className="text-white">{value}</strong></div>)}</div></section>
      </div>
    </div>
  </motion.div>;
};

export default HrAdminDashboard;
