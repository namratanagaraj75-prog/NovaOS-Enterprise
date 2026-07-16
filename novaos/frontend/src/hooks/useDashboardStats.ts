import { useMemo } from 'react';
import { useAppContext } from '../context/AppContext';
import { normalizeDate } from '../lib/dateUtils';
import { useAuth } from '../context/AuthContext';

export interface DashboardMetrics {
  candidates: number;
  pending: number;
  offers: number;
  emails: number;
  employees: number;
  failed: number;
  warnings: number;
  today: number;
  blocked: number;
  averageApprovalHours: string;
}

export function useDashboardStats(): DashboardMetrics {
  const { hiringRequests, candidates, employees } = useAppContext();
  const { user } = useAuth();

  return useMemo(() => {
    // 1. Today's Requests
    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);
    const startOfTodayMs = startOfToday.getTime();

    const todayRequests = hiringRequests.filter(d => {
      const created = normalizeDate(d.createdAt);
      return created && created.getTime() >= startOfTodayMs;
    }).length;

    // 2. Total Candidates
    // Unique candidates by email from candidates collection and hiringRequests
    const candidateEmails = new Set<string>();
    candidates.forEach(c => {
      if (c.email) {
        candidateEmails.add(c.email.toLowerCase().trim());
      }
    });
    hiringRequests.forEach(d => {
      if (d.candidateEmail) {
        candidateEmails.add(d.candidateEmail.toLowerCase().trim());
      }
    });
    const totalCandidatesCount = candidateEmails.size || candidates.length;

    // 3. Pending Approvals (role-aware)
    const userRole = (user?.role || '').toUpperCase();
    const pendingCount = hiringRequests.filter(d => {
      if (userRole === 'LEGAL') {
        return d.status === 'PENDING_LEGAL_APPROVAL';
      }
      if (userRole === 'FINANCE') {
        return d.status === 'PENDING_FINANCE_APPROVAL';
      }
      if (userRole === 'CEO') {
        return d.status === 'PENDING_CEO_APPROVAL';
      }
      if (userRole === 'HIRING_MANAGER') {
        return d.status === 'PENDING_MANAGER_APPROVAL' && d.hiringManagerId === user?.uid;
      }
      // HR_ADMIN or SUPER_ADMIN see all pending
      return ['PENDING_MANAGER_APPROVAL', 'PENDING_FINANCE_APPROVAL', 'PENDING_LEGAL_APPROVAL', 'PENDING_CEO_APPROVAL'].includes(d.status);
    }).length;

    // 4. Offers Generated
    // Count when a request has genuinely generated a PDF
    const offersCount = hiringRequests.filter(d => 
      d.pdfUrl || 
      d.pdfGeneratedAt || 
      d.offerLetterStatus === 'GENERATED' || 
      ['OFFER_GENERATED', 'EMAIL_SENDING', 'EMAIL_SENT', 'WORKFLOW_COMPLETED'].includes(d.status)
    ).length;

    // 5. Emails Sent
    const emailsCount = hiringRequests.filter(d => 
      d.emailStatus === 'SENT' || 
      d.status === 'EMAIL_SENT' || 
      d.emailSent === true || 
      d.emailSentAt || 
      d.emailDeliveryStatus === 'SENT'
    ).length;

    // 6. Employees Created
    // Unique employee emails/IDs from employees collection and completed hiring requests
    const employeeEmails = new Set<string>();
    employees.forEach(e => {
      if (e.email) {
        employeeEmails.add(e.email.toLowerCase().trim());
      }
    });
    hiringRequests.forEach(d => {
      if (['WORKFLOW_COMPLETED', 'APPROVED', 'EMPLOYEE_CREATED'].includes(d.status) && d.candidateEmail) {
        employeeEmails.add(d.candidateEmail.toLowerCase().trim());
      }
    });
    const employeesCount = employeeEmails.size || employees.length;

    // 7. Failed Workflows
    const failedCount = hiringRequests.filter(d => 
      d.emailStatus === 'FAILED' || 
      d.offerLetterStatus === 'FAILED' || 
      d.status === 'EMAIL_FAILED'
    ).length;

    // 8. Warnings
    const warningsCount = hiringRequests.reduce((total, d) => 
      total + (d.policyChecks || []).filter((c: any) => c.status === 'WARNING').length, 0
    );

    // 9. Blocked
    const blockedCount = hiringRequests.filter(d => d.decision === 'BLOCKED').length;

    // 10. Average Approval Time
    const completedDocs = hiringRequests.filter(d => 
      ['WORKFLOW_COMPLETED', 'APPROVED', 'EMAIL_SENT', 'EMPLOYEE_CREATED'].includes(d.status)
    );

    const getFinalApprovalTime = (d: any) => {
      const times = [
        d.managerApprovedAt,
        d.financeApprovedAt,
        d.legalApprovedAt,
        d.ceoApprovedAt,
        d.approvedAt
      ].map(t => normalizeDate(t)).filter((t): t is Date => t !== null);
      
      if (times.length === 0) return null;
      return new Date(Math.max(...times.map(t => t.getTime())));
    };

    const durations: number[] = [];
    completedDocs.forEach(d => {
      const start = normalizeDate(d.createdAt);
      const end = getFinalApprovalTime(d);
      if (start && end) {
        const diff = end.getTime() - start.getTime();
        if (diff >= 0) {
          durations.push(diff);
        }
      }
    });

    const avgMs = durations.length 
      ? durations.reduce((a, b) => a + b, 0) / durations.length 
      : 0;

    let averageApprovalHours = '—';
    if (avgMs > 0) {
      const minutes = avgMs / 60000;
      if (minutes < 60) {
        averageApprovalHours = `${Math.round(minutes)} min`;
      } else {
        const hours = minutes / 60;
        if (hours < 24) {
          averageApprovalHours = `${hours.toFixed(1)} hrs`;
        } else {
          const days = hours / 24;
          averageApprovalHours = `${days.toFixed(1)} days`;
        }
      }
    }

    return {
      candidates: totalCandidatesCount,
      pending: pendingCount,
      offers: offersCount,
      emails: emailsCount,
      employees: employeesCount,
      failed: failedCount,
      warnings: warningsCount,
      today: todayRequests,
      blocked: blockedCount,
      averageApprovalHours
    };
  }, [hiringRequests, candidates, employees, user]);
}
