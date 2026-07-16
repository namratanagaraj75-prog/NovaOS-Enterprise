import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useParams } from 'react-router-dom';
import { collection, doc, onSnapshot, query, where } from 'firebase/firestore';
import { AlertTriangle, CheckCircle2, FileText, Loader2, Mail, RefreshCw, ShieldCheck, UserCheck, XCircle } from 'lucide-react';
import { db } from '../lib/firebase';
import { useAuth } from '../context/AuthContext';
import { approveHiring, getPassport, previewWhatIf, retryDocument, sendOffer } from '../services/passportService';
import { normalizeDate, formatNormalizedDate } from '../lib/dateUtils';

const message = (error: any) => error?.response?.data?.detail || error?.response?.data?.message || error?.message || 'Request failed';
const badge = (status: string) => status === 'PASS' || status === 'APPROVED' || status === 'SENT'
  ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20'
  : status === 'FAIL' || status === 'FAILED' ? 'text-rose-400 bg-rose-500/10 border-rose-500/20'
  : 'text-amber-400 bg-amber-500/10 border-amber-500/20';

export const DecisionPassport: React.FC = () => {
  const { requestId = '' } = useParams();
  const { user } = useAuth();
  const [data, setData] = useState<any>(null);
  const [error, setError] = useState('');
  const [working, setWorking] = useState('');
  const [comment, setComment] = useState('');
  const [whatIfSalary, setWhatIfSalary] = useState('');
  const [whatIfDate, setWhatIfDate] = useState('');
  const [whatIf, setWhatIf] = useState<any>(null);

  useEffect(() => {
    let alive = true;
    getPassport(requestId).then(result => alive && setData(result)).catch(err => alive && setError(message(err)));
    const merge = (key: string, value: unknown) => setData((current: any) => ({ ...(current || {}), [key]: value }));
    const failures = (err: Error) => setError('Firestore listener failed: ' + err.message);
    const unsubs = [
      onSnapshot(doc(db, 'workflowRequests', requestId), snap => merge('workflow', snap.data() || {}), failures),
      onSnapshot(doc(db, 'candidates', requestId), snap => merge('candidate', snap.data() || {}), failures),
      onSnapshot(doc(db, 'employees', requestId), snap => merge('employee', snap.data() || {}), failures),
      onSnapshot(query(collection(db, 'approvals'), where('requestId', '==', requestId)),
        snap => merge('approvals', snap.docs.map(item => item.data())), failures),
      onSnapshot(query(collection(db, 'documents'), where('requestId', '==', requestId)),
        snap => merge('documents', snap.docs.map(item => item.data())), failures),
      onSnapshot(query(collection(db, 'auditLogs'), where('requestId', '==', requestId)),
        snap => merge('auditEvents', snap.docs.map(item => item.data())), failures),
    ];
    return () => { alive = false; unsubs.forEach(unsubscribe => unsubscribe()); };
  }, [requestId]);

  const workflow = data?.workflow || {};
  const candidate = data?.candidate || {};
  const checks: any[] = workflow.policyChecks || [];
  const approvals: any[] = data?.approvals || [];
  const documents: any[] = data?.documents || [];
  const audit: any[] = useMemo(() => [...(data?.auditEvents || [])].sort((a, b) => {
    const tA = normalizeDate(a.timestamp);
    const tB = normalizeDate(b.timestamp);
    return (tA ? tA.getTime() : 0) - (tB ? tB.getTime() : 0);
  }), [data?.auditEvents]);
  const role = (user?.role || '').toUpperCase();
  const expected = workflow.state === 'MANAGER_PENDING' ? 'HIRING_MANAGER'
    : workflow.state === 'LEGAL_PENDING' ? 'LEGAL' : workflow.state === 'FINANCE_PENDING' ? 'FINANCE' : '';
  const canApprove = role === expected;

  const act = async (name: string, operation: () => Promise<any>) => {
    setWorking(name); setError('');
    try { setData(await operation()); } catch (err) { setError(message(err)); } finally { setWorking(''); }
  };

  if (!data && !error) return <div className="h-72 bg-slate-900 rounded-2xl animate-pulse" />;

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-7 text-slate-200">
    <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-4 border-b border-slate-800 pb-6">
      <div><p className="text-[10px] uppercase tracking-[0.22em] text-cyan-400 font-mono">Hiring Decision Passport</p>
        <h1 className="text-3xl font-extrabold mt-2">{candidate.name || 'Hiring workflow'}</h1>
        <p className="text-sm text-slate-400 mt-1">{candidate.role || candidate.position} · {candidate.location}</p></div>
      <div className="text-right"><span className={`inline-flex border rounded-lg px-3 py-1.5 text-xs font-mono ${badge(workflow.state || '')}`}>{workflow.state || 'LOADING'}</span>
        <p className="text-[10px] text-slate-500 font-mono mt-2 break-all">{requestId}</p></div>
    </div>

    {error && <div className="bg-rose-500/10 border border-rose-500/20 rounded-xl p-4 text-sm text-rose-300 flex gap-3">
      <AlertTriangle className="h-5 w-5 shrink-0" />{error}</div>}

    <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
      <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <h2 className="font-bold text-white">Extracted hiring details</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mt-5">
          {[
            ['Email', candidate.email], ['Position', candidate.role || candidate.position],
            ['Annual CTC', candidate.annualCtc ? '₹' + Number(candidate.annualCtc).toLocaleString('en-IN') : ''],
            ['Joining', candidate.joiningDate], ['Department', candidate.department], ['Location', candidate.location],
            ['Manager', candidate.manager], ['Probation', candidate.probationMonths + ' months'],
            ['Skills', (candidate.requiredSkills || []).join(', ')],
          ].map(([label, value]) => <div key={label}><span className="text-[9px] uppercase tracking-wider text-slate-500">{label}</span>
            <p className="text-sm text-slate-200 mt-1">{value || '—'}</p></div>)}
        </div>
      </div>
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <h2 className="font-bold text-white">Request authority</h2>
        <div className="mt-5 space-y-3 text-xs"><p className="text-slate-400">Requestor</p>
          <p>{workflow.requestor?.email || '—'}</p><p className="text-cyan-400 font-mono">{workflow.requestor?.role}</p>
          <p className="text-slate-400 pt-2">Gemini confidence</p>
          <p className="text-2xl font-bold">{Math.round((workflow.geminiConfidence || 0) * 100)}%</p>
        </div>
      </div>
    </div>

    <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
      <h2 className="font-bold text-white flex items-center gap-2"><ShieldCheck className="h-5 w-5 text-cyan-400" /> Hiring policy report</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-5">{checks.map(check =>
        <div key={check.name} className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex gap-3">
          {check.status === 'PASS' ? <CheckCircle2 className="h-5 w-5 text-emerald-400 shrink-0" /> : <XCircle className="h-5 w-5 text-rose-400 shrink-0" />}
          <div><div className="flex gap-2 items-center"><h3 className="text-sm font-bold">{check.name}</h3>
            <span className={`text-[9px] border rounded px-1.5 ${badge(check.status)}`}>{check.status}</span></div>
            <p className="text-xs text-slate-400 mt-1">{check.reason}</p></div>
        </div>)}</div>
    </section>

    <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">
      <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <h2 className="font-bold text-white flex items-center gap-2"><UserCheck className="h-5 w-5 text-violet-400" /> Verified approvals</h2>
        <div className="space-y-3 mt-5">{approvals.length ? approvals.map(item => <div key={item.approverRole}
          className="bg-slate-950 border border-slate-800 rounded-xl p-4">
          <div className="flex justify-between"><strong className="text-sm">{item.approverRole}</strong><span className="text-[10px] text-emerald-400">{item.status}</span></div>
          <p className="text-xs text-slate-400 mt-1">{item.approverEmail} · {formatNormalizedDate(item.timestamp)}</p>
          {item.comment && <p className="text-xs mt-2">“{item.comment}”</p>}
        </div>) : <p className="text-xs text-slate-500">No approvals recorded.</p>}</div>
        {canApprove && <div className="mt-5 border-t border-slate-800 pt-5"><textarea value={comment} onChange={e => setComment(e.target.value)}
          placeholder="Approval comment" className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-xs" />
          <button onClick={() => act('approve', () => approveHiring(requestId, comment))} disabled={working !== ''}
            className="mt-3 bg-violet-500 text-white px-4 py-2.5 rounded-xl text-xs font-bold disabled:opacity-40">
            {working === 'approve' ? 'Recording approval…' : 'Approve as ' + role}</button></div>}
      </section>

      <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <h2 className="font-bold text-white flex items-center gap-2"><FileText className="h-5 w-5 text-cyan-400" /> Document and delivery</h2>
        <div className="space-y-3 mt-5">{documents.map(docItem => <div key={docItem.documentId} className="bg-slate-950 border border-slate-800 rounded-xl p-4">
          <div className="flex justify-between"><strong className="text-sm">{docItem.type}</strong><span className="text-[10px] text-cyan-400">{docItem.status}</span></div>
          <p className="text-xs text-slate-400 mt-1">{docItem.offerId} · delivery {docItem.deliveryStatus}</p>
        </div>)}</div>
        {role === 'HR_ADMIN' && workflow.state === 'FAILED' && workflow.failedStage === 'OFFER_GENERATING' &&
          <button onClick={() => act('document', () => retryDocument(requestId))} className="mt-4 flex gap-2 bg-slate-800 px-4 py-2.5 rounded-xl text-xs">
            <RefreshCw className="h-4 w-4" /> Retry document preparation</button>}
        {role === 'HR_ADMIN' && workflow.emailStatus === 'FAILED' &&
          <button onClick={() => window.confirm('Retry sending this offer email?') &&
            act('resend', () => sendOffer(requestId, true))} disabled={working !== ''}
            className="mt-4 flex gap-2 border border-amber-500/30 text-amber-400 px-4 py-2.5 rounded-xl text-xs">
            <Mail className="h-4 w-4" /> Retry email</button>}
      </section>
    </div>

    {role === 'HR_ADMIN' && <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
      <h2 className="font-bold text-white">What-if policy preview</h2>
      <p className="text-xs text-slate-400 mt-1">Preview salary or date changes without modifying the real workflow.</p>
      <div className="flex flex-col sm:flex-row gap-3 mt-4">
        <input type="number" value={whatIfSalary} onChange={e => setWhatIfSalary(e.target.value)} placeholder="Annual CTC"
          className="bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-xs" />
        <input type="date" value={whatIfDate} onChange={e => setWhatIfDate(e.target.value)}
          className="bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-xs" />
        <button onClick={() => act('whatif', async () => { const result = await previewWhatIf(requestId,
          whatIfSalary ? Number(whatIfSalary) : null, whatIfDate); setWhatIf(result); return data; })}
          className="bg-slate-800 px-4 py-2.5 rounded-xl text-xs">Preview impact</button>
      </div>
      {whatIf && <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-5">{whatIf.previewChecks.map((check: any) =>
        <div key={check.name} className="bg-slate-950 rounded-xl p-3 flex justify-between gap-3"><span className="text-xs">{check.name}</span>
          <span className={`text-[9px] border rounded px-1.5 ${badge(check.status)}`}>{check.status}</span></div>)}</div>}
    </section>}

    <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
      <h2 className="font-bold text-white">Audit timeline</h2>
      <div className="mt-5 border-l border-slate-700 pl-5 space-y-5">{audit.map((event, index) =>
        <div key={event.action + index} className="relative"><span className="absolute -left-[25px] top-1 h-2 w-2 bg-cyan-400 rounded-full" />
          <div className="flex justify-between gap-3"><strong className="text-xs">{event.action}</strong>
            <span className="text-[10px] text-slate-500">{formatNormalizedDate(event.timestamp)}</span></div>
          <p className="text-xs text-slate-400 mt-1">{event.details}</p>
          <p className="text-[10px] text-cyan-400 mt-1">{event.actor?.email || event.actor}</p>
        </div>)}</div>
    </section>
  </motion.div>;
};
export default DecisionPassport;
