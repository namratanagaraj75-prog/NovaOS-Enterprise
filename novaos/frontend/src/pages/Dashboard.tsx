import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { Activity, AlertTriangle, FileText, Mail, UserCheck, Users, Workflow } from 'lucide-react';
import { collection, onSnapshot } from 'firebase/firestore';
import { db } from '../lib/firebase';
import { useAuth } from '../context/AuthContext';
import StatCard from '../components/StatCard';
import ActivityFeed from '../components/ActivityFeed';
import ApprovalInbox from '../components/ApprovalInbox';
import { normalizeDate, formatNormalizedDate } from '../lib/dateUtils';
import { useDashboardStats } from '../hooks/useDashboardStats';

export const Dashboard: React.FC = () => {
  const { user } = useAuth();
  const metrics = useDashboardStats();
  const [audit, setAudit] = useState<any[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    const fail = (err: Error) => setError(err.message);
    const unsubs = [
      onSnapshot(collection(db, 'auditLogs'), snap => setAudit(snap.docs.map(d => ({ id: d.id, ...d.data() }))
        .sort((a: any, b: any) => {
          const tA = normalizeDate(a.timestamp);
          const tB = normalizeDate(b.timestamp);
          return (tB ? tB.getTime() : 0) - (tA ? tA.getTime() : 0);
        }).slice(0, 20)), fail),
    ];
    return () => unsubs.forEach(unsubscribe => unsubscribe());
  }, []);

  const feed = useMemo(() => audit.map(item => ({
    id: item.id, candidateName: item.action || 'Audit event',
    position: item.actor?.email || item.actor || 'NovaOS',
    action: item.details || '', timestamp: formatNormalizedDate(item.timestamp),
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
