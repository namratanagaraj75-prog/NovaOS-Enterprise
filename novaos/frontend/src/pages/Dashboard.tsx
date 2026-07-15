import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { Activity, AlertTriangle, FileText, Mail, ShieldAlert, UserCheck, Users, Workflow } from 'lucide-react';
import { collection, onSnapshot } from 'firebase/firestore';
import { db } from '../lib/firebase';
import { useAuth } from '../context/AuthContext';
import StatCard from '../components/StatCard';
import ActivityFeed from '../components/ActivityFeed';
import ApprovalInbox from '../components/ApprovalInbox';

interface Metrics {
  candidates: number; pending: number; offers: number; emails: number;
  employees: number; failed: number; warnings: number; today: number; blocked: number;
  averageApprovalHours: number;
}
const initial: Metrics = { candidates: 0, pending: 0, offers: 0, emails: 0, employees: 0, failed: 0, warnings: 0, today:0, blocked:0, averageApprovalHours:0 };
const millis=(value:any)=>value?.toMillis?value.toMillis():value?.seconds?value.seconds*1000:new Date(value||0).getTime();

export const Dashboard: React.FC = () => {
  const { user } = useAuth();
  const [metrics, setMetrics] = useState(initial);
  const [audit, setAudit] = useState<any[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    const fail = (err: Error) => setError(err.message);
    const unsubs = [
      onSnapshot(collection(db, 'candidates'), snap => setMetrics(m => ({ ...m, candidates: snap.size })), fail),
      onSnapshot(collection(db, 'employees'), snap => setMetrics(m => ({ ...m, employees: snap.size })), fail),
      onSnapshot(collection(db, 'documents'), snap => setMetrics(m => ({
        ...m,
        offers: snap.docs.filter(d => d.data().type === 'OFFER_LETTER' && d.data().status === 'STORED').length,
        emails: snap.docs.filter(d => d.data().deliveryStatus === 'SENT').length,
      })), fail),
      onSnapshot(collection(db, 'hiringRequests'), snap => {const docs=snap.docs.map(d=>d.data());const start=new Date();start.setHours(0,0,0,0);const approvalHours=docs.map(d=>{const events=d.activityHistory||[];const first=events.find((e:any)=>e.action==='REQUEST_CREATED');const last=[...events].reverse().find((e:any)=>e.action==='APPROVALS_COMPLETED');return first&&last?(millis(last.timestamp)-millis(first.timestamp))/3600000:null;}).filter((v:any)=>v!=null&&v>=0) as number[];setMetrics(m => ({
        ...m,
        today:docs.filter(d=>millis(d.createdAt)>=start.getTime()).length,
        pending: docs.filter(d => ['PENDING_MANAGER_APPROVAL', 'PENDING_FINANCE_APPROVAL', 'PENDING_LEGAL_APPROVAL', 'PENDING_CEO_APPROVAL'].includes(d.status)).length,
        failed: docs.filter(d => d.emailStatus === 'FAILED' || d.offerLetterStatus === 'FAILED').length,
        blocked:docs.filter(d=>d.decision==='BLOCKED').length,
        warnings: docs.reduce((total, d) => total + (d.policyChecks || []).filter((c: any) => c.status === 'WARNING').length, 0),
        averageApprovalHours:approvalHours.length?Number((approvalHours.reduce((a,b)=>a+b,0)/approvalHours.length).toFixed(1)):0,
      }))}, fail),
      onSnapshot(collection(db, 'auditLogs'), snap => setAudit(snap.docs.map(d => ({ id: d.id, ...d.data() }))
        .sort((a: any, b: any) => String(b.timestamp).localeCompare(String(a.timestamp))).slice(0, 20)), fail),
    ];
    return () => unsubs.forEach(unsubscribe => unsubscribe());
  }, []);

  const feed = useMemo(() => audit.map(item => ({
    id: item.id, candidateName: item.action || 'Audit event',
    position: item.actor?.email || item.actor || 'NovaOS',
    action: item.details || '', timestamp: item.timestamp ? new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '',
    status: item.action?.includes('FAILED') ? 'warning' as const : 'completed' as const,
  })), [audit]);

  const cards = [
    { title: "Today's Requests", value: metrics.today, icon: Activity, change: 'Created since midnight' },
    { title: 'Total Candidates', value: metrics.candidates, icon: Users, change: 'Firestore candidates' },
    { title: 'Pending Approvals', value: metrics.pending, icon: Workflow, change: 'Manager · Legal · Finance' },
    { title: 'Offers Generated', value: metrics.offers, icon: FileText, change: 'Email-ready PDFs' },
    { title: 'Emails Sent', value: metrics.emails, icon: Mail, change: 'SMTP delivered' },
    { title: 'Employees Created', value: metrics.employees, icon: UserCheck, change: 'After delivery only' },
    { title: 'Failed Workflows', value: metrics.failed, icon: AlertTriangle, change: 'Retryable stages' },
    { title: 'Avg Approval Time', value: metrics.averageApprovalHours, icon: Workflow, change: 'Hours to approvals complete' },
  ];

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 text-slate-200">
    <div className="border-b border-slate-800 pb-6"><p className="text-[10px] text-cyan-400 uppercase tracking-[0.22em] font-mono">Decision intelligence</p>
      <h1 className="text-3xl font-extrabold text-white mt-2">Hiring Governance Dashboard</h1>
      <p className="text-sm text-slate-400 mt-1">Live Firestore evidence for {user?.displayName || user?.email}.</p></div>
    {error && <div className="bg-rose-500/10 border border-rose-500/20 text-rose-300 p-4 rounded-xl text-xs">
      Firestore listener failed: {error}</div>}
    <ApprovalInbox />
    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">
      {cards.map((card, index) => <StatCard key={card.title} title={card.title} value={card.value}
        change={card.change} icon={card.icon} index={index} />)}
    </div>
    <div className="w-full">
      <ActivityFeed activities={feed} />
    </div>
  </motion.div>;
};
export default Dashboard;
