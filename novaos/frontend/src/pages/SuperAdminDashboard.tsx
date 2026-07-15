import React, { useEffect, useMemo, useState } from 'react';
import { collection, doc, onSnapshot, query, setDoc, updateDoc } from 'firebase/firestore';
import { db } from '../lib/firebase';
import firestoreService, { AuditLog, FirestoreUser } from '../services/firestoreService';
import apiClient from '../services/api';
import {
  Activity,
  Bot,
  CheckCircle2,
  FileText,
  Mail,
  RefreshCw,
  ScrollText,
  Shield,
  ToggleLeft,
  ToggleRight,
  User,
  UserPlus,
  Users,
  Workflow
} from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';

interface LiveMetrics {
  employees: number;
  candidates: number;
  aiRequests: number;
  pendingApprovals: number;
  documentsGenerated: number;
  emailsSent: number;
  auditLogs: number;
}

export const SuperAdminDashboard: React.FC = () => {
  const { user: currentUser } = useAuth();
  const { showToast } = useToast();

  const [users, setUsers] = useState<FirestoreUser[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [metrics, setMetrics] = useState<LiveMetrics>({
    employees: 0,
    candidates: 0,
    aiRequests: 0,
    pendingApprovals: 0,
    documentsGenerated: 0,
    emailsSent: 0,
    auditLogs: 0
  });
  const [activeTab, setActiveTab] = useState<'overview' | 'access'>('overview');
  const [showAddForm, setShowAddForm] = useState(false);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('EMPLOYEE');
  const [department, setDepartment] = useState('');
  const [designation, setDesignation] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isResetting, setIsResetting] = useState(false);

  const handleResetDemoData = async () => {
    if (!window.confirm("Are you sure you want to reset all demo data? This will clear all candidates, employees, requests, notifications, approvals, documents, and audit logs.")) {
      return;
    }
    setIsResetting(true);
    try {
      await apiClient.post('/admin/reset-demo-data');
      showToast('Demo database has been successfully reset.', 'success');
      window.location.reload();
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Reset failed';
      showToast('Reset failed: ' + errorMsg, 'error');
    } finally {
      setIsResetting(false);
    }
  };

  const roles = [
    { value: 'SUPER_ADMIN', label: 'CEO / Super Admin' },
    { value: 'HR_ADMIN', label: 'HR Admin' },
    { value: 'MANAGER', label: 'Manager' },
    { value: 'LEGAL', label: 'Legal Team' },
    { value: 'FINANCE', label: 'Finance' },
    { value: 'EMPLOYEE', label: 'Employee' }
  ];

  useEffect(() => {
    const unsubscribers = [
      onSnapshot(collection(db, 'employees'), snap => setMetrics(prev => ({ ...prev, employees: snap.size }))),
      onSnapshot(collection(db, 'candidates'), snap => setMetrics(prev => ({ ...prev, candidates: snap.size }))),
      onSnapshot(query(collection(db, 'workflowRequests')), snap => {
        const pending = snap.docs.filter(item => String(item.data().status || '').toLowerCase() === 'pending').length;
        setMetrics(prev => ({ ...prev, pendingApprovals: pending }));
      }),
      onSnapshot(collection(db, 'auditLogs'), snap => {
        setMetrics(prev => ({ ...prev, auditLogs: snap.size }));
        const logs = snap.docs
          .map(item => ({ id: item.id, ...item.data() } as AuditLog))
          .sort((a, b) => String(b.timestamp).localeCompare(String(a.timestamp)))
          .slice(0, 12);
        setAuditLogs(logs);
      }),
      onSnapshot(collection(db, 'users'), snap => {
        setUsers(snap.docs.map(item => ({ uid: item.id, ...item.data() } as FirestoreUser)));
      }),
      onSnapshot(doc(db, 'metrics', 'dashboard'), snap => {
        const data = snap.data() || {};
        setMetrics(prev => ({
          ...prev,
          aiRequests: Number(data.aiRequests || 0),
          documentsGenerated: Number(data.documentsGenerated || 0),
          emailsSent: Number(data.emailsSent || 0)
        }));
      })
    ];

    return () => unsubscribers.forEach(unsubscribe => unsubscribe());
  }, []);

  const metricCards = useMemo(() => [
    { label: 'Employees', value: metrics.employees, icon: Users, tone: 'text-cyan-400' },
    { label: 'Candidates', value: metrics.candidates, icon: UserPlus, tone: 'text-violet-400' },
    { label: 'AI Requests', value: metrics.aiRequests, icon: Bot, tone: 'text-fuchsia-400' },
    { label: 'Pending Approvals', value: metrics.pendingApprovals, icon: Workflow, tone: 'text-amber-400' },
    { label: 'Documents Generated', value: metrics.documentsGenerated, icon: FileText, tone: 'text-emerald-400' },
    { label: 'Emails Sent', value: metrics.emailsSent, icon: Mail, tone: 'text-sky-400' },
    { label: 'Audit Logs', value: metrics.auditLogs, icon: ScrollText, tone: 'text-slate-300' }
  ], [metrics]);

  const handleAddUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !email.trim()) {
      showToast('Name and email are required.', 'error');
      return;
    }

    setIsSubmitting(true);
    const cleanedEmail = email.trim().toLowerCase();

    try {
      const existingUser = users.find(u => u.email?.toLowerCase() === cleanedEmail);
      if (existingUser) {
        showToast('User with this email is already registered.', 'error');
        return;
      }

      const newUser = {
        uid: '',
        name: name.trim(),
        email: cleanedEmail,
        role,
        department: department.trim() || undefined,
        designation: designation.trim() || undefined,
        status: 'ACTIVE',
        approved: true,
        active: true,
        createdAt: new Date().toISOString()
      };

      await setDoc(doc(db, 'users', cleanedEmail), newUser);
      await firestoreService.writeAuditLog(
        `Pre-provisioned user: ${cleanedEmail}`,
        currentUser?.email || 'SYSTEM',
        `Assigned role: ${role}, department: ${department || 'N/A'}`
      );

      showToast(`User ${name} has been pre-provisioned.`, 'success');
      setName('');
      setEmail('');
      setDepartment('');
      setDesignation('');
      setRole('EMPLOYEE');
      setShowAddForm(false);
    } catch (err: any) {
      showToast('Failed to create user record: ' + err.message, 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggleStatus = async (userDoc: FirestoreUser) => {
    const isActive = String(userDoc.status).toUpperCase() === 'ACTIVE';
    const newStatus = isActive ? 'Suspended' : 'ACTIVE';
    const docId = userDoc.uid || userDoc.email.toLowerCase();

    try {
      await updateDoc(doc(db, 'users', docId), { status: newStatus, active: !isActive });
      await firestoreService.writeAuditLog(
        `Toggled status of user: ${userDoc.email}`,
        currentUser?.email || 'SYSTEM',
        `Status updated from ${userDoc.status} to ${newStatus}`
      );
      showToast(`User status updated to ${newStatus}.`, 'success');
    } catch (err: any) {
      showToast('Failed to toggle status: ' + err.message, 'error');
    }
  };

  return (
    <div className="space-y-8 bg-[#080613] min-h-screen text-slate-200 font-sans p-1">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-white/5 pb-6">
        <div>
          <h1 className="text-2xl font-black text-white tracking-tight flex items-center gap-3">
            <Shield className="h-6 w-6 text-cyan-400" />
            <span>NovaOS Command Dashboard</span>
          </h1>
          <p className="text-gray-400 text-xs mt-1">Firestore-backed live metrics, approvals, identity access, and audit trail.</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={handleResetDemoData}
            disabled={isResetting}
            className="px-4 py-2 bg-rose-500 hover:bg-rose-600 disabled:opacity-40 text-white rounded-lg text-xs font-bold transition-all shadow-glow-rose flex items-center gap-2"
          >
            {isResetting ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="h-3.5 w-3.5" />}
            <span>Reset Demo Data</span>
          </button>
          <div className="flex items-center gap-2 bg-[#121426] border border-white/5 p-1 rounded-xl">
            <button onClick={() => setActiveTab('overview')} className={`px-4 py-2 rounded-lg text-xs font-bold ${activeTab === 'overview' ? 'bg-cyan-500 text-slate-950' : 'text-gray-400 hover:text-white'}`}>Overview</button>
            <button onClick={() => setActiveTab('access')} className={`px-4 py-2 rounded-lg text-xs font-bold ${activeTab === 'access' ? 'bg-cyan-500 text-slate-950' : 'text-gray-400 hover:text-white'}`}>Access</button>
          </div>
        </div>
      </div>

      {activeTab === 'overview' ? (
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
          <div className="xl:col-span-2 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {metricCards.map(card => {
              const Icon = card.icon;
              return (
                <div key={card.label} className="bg-[#121426] border border-white/5 p-5 rounded-2xl">
                  <div className="flex justify-between items-start mb-5">
                    <span className="text-[11px] text-gray-400 font-bold uppercase tracking-wider">{card.label}</span>
                    <Icon className={`h-4 w-4 ${card.tone}`} />
                  </div>
                  <div className="text-3xl font-black text-white tracking-tight">{card.value}</div>
                  <div className="mt-2 flex items-center gap-2 text-[10px] text-gray-500 font-mono">
                    <Activity className="h-3 w-3" />
                    <span>Live from Firestore</span>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="bg-[#121426] border border-white/5 p-5 rounded-2xl min-h-[420px]">
            <div className="flex items-center gap-2 text-sm font-bold text-white uppercase tracking-wider mb-4">
              <ScrollText className="h-4 w-4 text-cyan-400" />
              Audit Trail
            </div>
            <div className="space-y-3 max-h-[540px] overflow-y-auto pr-1">
              {auditLogs.length === 0 ? (
                <div className="text-xs text-gray-500 py-10 text-center">No audit logs yet.</div>
              ) : auditLogs.map(log => (
                <div key={log.id} className="bg-[#080613]/60 border border-white/5 rounded-xl p-3 text-xs">
                  <div className="flex items-start justify-between gap-3">
                    <span className="font-bold text-white">{log.action}</span>
                    <span className="text-[10px] text-gray-500 shrink-0">{new Date(log.timestamp).toLocaleTimeString()}</span>
                  </div>
                  <p className="text-gray-400 text-[11px] mt-1">{log.details}</p>
                  <span className="text-[10px] text-cyan-400 uppercase font-bold mt-2 block">{log.actor}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
          <div className="lg:col-span-2 space-y-6">
            <div className="flex justify-between items-center">
              <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider font-mono">Provisioning Console</h3>
              <button onClick={() => setShowAddForm(!showAddForm)} className="flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 text-slate-950 px-4 py-2 rounded-xl text-xs font-bold">
                <UserPlus className="h-4 w-4" />
                <span>Pre-Provision User</span>
              </button>
            </div>

            {showAddForm && (
              <form onSubmit={handleAddUser} className="bg-[#121426] border border-white/5 p-6 rounded-2xl space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <input value={name} onChange={e => setName(e.target.value)} placeholder="Full name" className="px-3 py-2 bg-slate-950 border border-white/10 rounded-xl text-xs text-white" />
                  <input value={email} onChange={e => setEmail(e.target.value)} placeholder="Email" type="email" className="px-3 py-2 bg-slate-950 border border-white/10 rounded-xl text-xs text-white" />
                  <select value={role} onChange={e => setRole(e.target.value)} className="px-3 py-2 bg-slate-950 border border-white/10 rounded-xl text-xs text-white">
                    {roles.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
                  </select>
                  <input value={department} onChange={e => setDepartment(e.target.value)} placeholder="Department" className="px-3 py-2 bg-slate-950 border border-white/10 rounded-xl text-xs text-white" />
                  <input value={designation} onChange={e => setDesignation(e.target.value)} placeholder="Designation" className="sm:col-span-2 px-3 py-2 bg-slate-950 border border-white/10 rounded-xl text-xs text-white" />
                </div>
                <div className="flex justify-end gap-3">
                  <button type="button" onClick={() => setShowAddForm(false)} className="px-4 py-2 bg-white/5 border border-white/10 rounded-xl text-xs text-white">Cancel</button>
                  <button type="submit" disabled={isSubmitting} className="px-4 py-2 bg-cyan-500 text-slate-950 rounded-xl text-xs font-bold">{isSubmitting ? 'Saving...' : 'Authorize Identity'}</button>
                </div>
              </form>
            )}

            <div className="bg-[#121426] border border-white/5 p-6 rounded-2xl overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="text-[10px] text-gray-400 uppercase tracking-wider font-mono border-b border-white/5">
                  <tr><th className="pb-3">User</th><th className="pb-3">Role</th><th className="pb-3">Status</th><th className="pb-3 text-right">Action</th></tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {users.map(item => (
                    <tr key={item.email}>
                      <td className="py-4">
                        <div className="flex items-center gap-3">
                          <div className="h-8 w-8 rounded-full bg-slate-950 flex items-center justify-center border border-white/10"><User className="h-4 w-4 text-gray-400" /></div>
                          <div><div className="font-bold text-white">{item.name}</div><div className="text-[10px] text-gray-500">{item.email}</div></div>
                        </div>
                      </td>
                      <td className="py-4 font-mono text-[10px] text-cyan-400 font-bold">{item.role}</td>
                      <td className="py-4 font-mono text-[10px] text-slate-300">{item.status}</td>
                      <td className="py-4 text-right">
                        <button onClick={() => handleToggleStatus(item)} className="p-1.5 rounded-lg border border-white/5 hover:border-white/15 text-gray-400 hover:text-white">
                          {String(item.status).toUpperCase() === 'ACTIVE' ? <ToggleRight className="h-5 w-5 text-emerald-400" /> : <ToggleLeft className="h-5 w-5 text-gray-500" />}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="bg-[#121426] border border-white/5 p-5 rounded-2xl">
            <div className="flex items-center gap-2 text-sm font-bold text-white uppercase tracking-wider mb-4"><CheckCircle2 className="h-4 w-4 text-emerald-400" />Firebase Auth</div>
            <p className="text-xs text-gray-400 leading-6">Email/password and Google users authenticate in Firebase. Spring Boot verifies Firebase ID tokens and reads role access from Firestore.</p>
          </div>
        </div>
      )}
    </div>
  );
};

export default SuperAdminDashboard;
