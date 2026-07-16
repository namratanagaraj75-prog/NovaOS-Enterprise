import React, { useEffect, useState, useMemo } from 'react';
import { collection, onSnapshot } from 'firebase/firestore';
import { db } from '../lib/firebase';
import { useAuth } from '../context/AuthContext';
import { Clock, CheckCircle, XCircle, UserCheck, AlertTriangle } from 'lucide-react';
import StatCard from './StatCard';
import { normalizeDate } from '../lib/dateUtils';

const isToday = (ts: any) => {
  const d = normalizeDate(ts);
  if (!d) return false;
  const today = new Date();
  return (
    d.getDate() === today.getDate() &&
    d.getMonth() === today.getMonth() &&
    d.getFullYear() === today.getFullYear()
  );
};

export const DashboardStatusCounts: React.FC = () => {
  const { user } = useAuth();
  const [requests, setRequests] = useState<any[]>([]);
  const [error, setError] = useState('');

  const role = (user?.role || '').toUpperCase();
  const uid = user?.uid;

  useEffect(() => {
    const unsubscribe = onSnapshot(
      collection(db, 'hiringRequests'),
      (snapshot) => {
        setRequests(snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() })));
      },
      (err) => {
        setError(err.message);
      }
    );
    return unsubscribe;
  }, []);

  const stats = useMemo(() => {
    let pendingCount = 0;
    let approvedTodayCount = 0;
    let rejectedCount = 0;
    let completedCount = 0;
    let emailFailuresCount = 0;

    if (role === 'HR_ADMIN' || role === 'SUPER_ADMIN') {
      // Organization-wide counts
      pendingCount = requests.filter((r) =>
        ['PENDING_MANAGER_APPROVAL', 'PENDING_FINANCE_APPROVAL', 'PENDING_LEGAL_APPROVAL', 'PENDING_CEO_APPROVAL'].includes(r.status)
      ).length;
      approvedTodayCount = requests.filter((r) =>
        isToday(r.managerApprovedAt) ||
        isToday(r.financeApprovedAt) ||
        isToday(r.legalApprovedAt) ||
        isToday(r.ceoApprovedAt) ||
        isToday(r.approvedAt)
      ).length;
      rejectedCount = requests.filter((r) => r.status === 'REJECTED').length;
      completedCount = requests.filter((r) =>
        ['WORKFLOW_COMPLETED', 'APPROVED', 'EMPLOYEE_CREATED'].includes(r.status)
      ).length;
      emailFailuresCount = requests.filter((r) => r.emailStatus === 'FAILED').length;
    } else if (role === 'HIRING_MANAGER') {
      const myRequests = requests.filter((r) => r.hiringManagerId === uid);
      pendingCount = myRequests.filter((r) => r.status === 'PENDING_MANAGER_APPROVAL').length;
      approvedTodayCount = myRequests.filter((r) => r.managerApprovalStatus === 'APPROVED' && isToday(r.managerApprovedAt)).length;
      rejectedCount = myRequests.filter((r) => r.status === 'REJECTED' || r.managerApprovalStatus === 'REJECTED').length;
      completedCount = myRequests.filter((r) => ['WORKFLOW_COMPLETED', 'APPROVED'].includes(r.status)).length;
      emailFailuresCount = myRequests.filter((r) => r.emailStatus === 'FAILED').length;
    } else if (role === 'FINANCE') {
      pendingCount = requests.filter((r) => r.status === 'PENDING_FINANCE_APPROVAL').length;
      approvedTodayCount = requests.filter((r) => r.financeApprovalStatus === 'APPROVED' && isToday(r.financeApprovedAt)).length;
      rejectedCount = requests.filter((r) => r.financeApprovalStatus === 'REJECTED').length;
      completedCount = requests.filter((r) => ['WORKFLOW_COMPLETED', 'APPROVED'].includes(r.status) && r.financeApprovalStatus === 'APPROVED').length;
      emailFailuresCount = requests.filter((r) => r.emailStatus === 'FAILED' && r.financeApprovalStatus === 'APPROVED').length;
    } else if (role === 'LEGAL') {
      pendingCount = requests.filter((r) => r.status === 'PENDING_LEGAL_APPROVAL').length;
      approvedTodayCount = requests.filter((r) => r.legalApprovalStatus === 'APPROVED' && isToday(r.legalApprovedAt)).length;
      rejectedCount = requests.filter((r) => r.legalApprovalStatus === 'REJECTED').length;
      completedCount = requests.filter((r) => ['WORKFLOW_COMPLETED', 'APPROVED'].includes(r.status) && r.legalApprovalStatus === 'APPROVED').length;
      emailFailuresCount = requests.filter((r) => r.emailStatus === 'FAILED' && r.legalApprovalStatus === 'APPROVED').length;
    } else if (role === 'CEO') {
      pendingCount = requests.filter((r) => r.status === 'PENDING_CEO_APPROVAL').length;
      approvedTodayCount = requests.filter((r) => r.ceoApprovalStatus === 'APPROVED' && isToday(r.ceoApprovedAt)).length;
      rejectedCount = requests.filter((r) => r.ceoApprovalStatus === 'REJECTED').length;
      completedCount = requests.filter((r) => ['WORKFLOW_COMPLETED', 'APPROVED'].includes(r.status) && r.ceoApprovalStatus === 'APPROVED').length;
      emailFailuresCount = requests.filter((r) => r.emailStatus === 'FAILED' && r.ceoApprovalStatus === 'APPROVED').length;
    }

    return {
      pending: pendingCount,
      approvedToday: approvedTodayCount,
      rejected: rejectedCount,
      completed: completedCount,
      failures: emailFailuresCount,
    };
  }, [requests, role, uid]);

  if (error) {
    return (
      <div className="bg-rose-500/10 border border-rose-500/20 text-rose-300 p-4 rounded-xl text-xs">
        Failed to load status counts: {error}
      </div>
    );
  }

  const cards = [
    { title: 'Pending My Approval', value: stats.pending, icon: Clock, type: 'approvals' },
    { title: 'Approved Today', value: stats.approvedToday, icon: CheckCircle, type: 'employees' },
    { title: 'Rejected', value: stats.rejected, icon: XCircle, type: 'candidates' },
    { title: 'Completed Hires', value: stats.completed, icon: UserCheck, type: 'offers' },
    { title: 'Email Failures', value: stats.failures, icon: AlertTriangle, type: 'candidates' },
  ];

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-5">
      {cards.map((card, index) => {
        const kpi = {
          title: card.title,
          value: String(card.value),
          change: card.title === 'Email Failures' && card.value > 0 ? 'Retry needed' : 'Live status',
          isPositive: card.title !== 'Email Failures' || card.value === 0,
          type: card.type as any,
        };
        return (
          <StatCard
            key={card.title}
            data={kpi}
            index={index}
            icon={card.icon}
          />
        );
      })}
    </div>
  );
};

export default DashboardStatusCounts;
