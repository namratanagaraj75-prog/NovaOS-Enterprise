import React, { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { AlertTriangle, Brain, CheckCircle2, Loader2, ShieldCheck, Sparkles } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  confirmHiring, emptyHiringCandidate, HiringCandidate, HiringParseResponse, parseInstruction,
} from '../services/passportService';

const fields: Array<{ key: keyof HiringCandidate; label: string; type?: string }> = [
  { key: 'name', label: 'Candidate name' }, { key: 'email', label: 'Email', type: 'email' },
  { key: 'position', label: 'Position' }, { key: 'annualCtc', label: 'Annual CTC (INR)', type: 'number' },
  { key: 'joiningDate', label: 'Joining date', type: 'date' }, { key: 'department', label: 'Department' },
  { key: 'location', label: 'Location' }, { key: 'manager', label: 'Reporting manager' },
  { key: 'probationMonths', label: 'Probation months', type: 'number' },
];

const errorText = (error: any) => error?.response?.data?.detail || error?.response?.data?.message
  || error?.message || 'The request failed.';

export const AICommandCenter: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [instruction, setInstruction] = useState('');
  const [requestId, setRequestId] = useState('');
  const [candidate, setCandidate] = useState<HiringCandidate | null>(null);
  const [parseResult, setParseResult] = useState<HiringParseResponse | null>(null);
  const [error, setError] = useState('');
  const [parsing, setParsing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const role = (user?.role || '').toUpperCase();
  const missing = useMemo(() => {
    if (!candidate) return [];
    const values: Array<[string, unknown]> = [
      ['name', candidate.name], ['email', candidate.email], ['position', candidate.position],
      ['annualCtc', candidate.annualCtc], ['joiningDate', candidate.joiningDate], ['department', candidate.department],
      ['location', candidate.location], ['manager', candidate.manager], ['probationMonths', candidate.probationMonths],
      ['requiredSkills', candidate.requiredSkills],
    ];
    return values.filter(([, value]) => value == null || value === '' || Array.isArray(value) && !value.length).map(([key]) => key);
  }, [candidate]);

  const parse = async () => {
    if (!instruction.trim()) return;
    setParsing(true); setError(''); setCandidate(null); setParseResult(null);
    const id = crypto.randomUUID(); setRequestId(id);
    try {
      const result = await parseInstruction(id, instruction.trim());
      setParseResult(result); setCandidate({ ...emptyHiringCandidate(), ...result.candidate });
    } catch (err) {
      setError(errorText(err));
      setCandidate(emptyHiringCandidate());
    } finally {
      setParsing(false);
    }
  };

  const update = (key: keyof HiringCandidate, raw: string) => {
    if (!candidate) return;
    if (key === 'annualCtc' || key === 'probationMonths') {
      setCandidate({ ...candidate, [key]: raw === '' ? null : Number(raw) });
    } else setCandidate({ ...candidate, [key]: raw });
  };

  const confirm = async () => {
    if (!candidate || missing.length) return;
    setConfirming(true); setError('');
    try {
      const confirmedRequestId = requestId || crypto.randomUUID();
      await confirmHiring({ requestId: confirmedRequestId, originalInstruction: instruction,
        candidate, confidence: parseResult?.confidence || 0 });
      navigate('/passports/' + confirmedRequestId);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setConfirming(false);
    }
  };

  if (role !== 'HR_ADMIN') return <div className="bg-slate-900 border border-amber-500/20 rounded-2xl p-8 text-slate-200">
    <ShieldCheck className="h-7 w-7 text-amber-400" />
    <h1 className="text-xl font-bold mt-4">HR authorization required</h1>
    <p className="text-sm text-slate-400 mt-2">Only HR_ADMIN can parse and initiate a governed hiring workflow.</p>
  </div>;

  return <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 text-slate-200">
    <div className="border-b border-slate-800 pb-6">
      <p className="text-[10px] uppercase tracking-[0.22em] text-cyan-400 font-mono">Governed hiring instruction</p>
      <h1 className="text-3xl font-extrabold mt-2">AI Hiring <span className="text-cyan-500">Decision Passport</span></h1>
      <p className="text-sm text-slate-400 mt-2">Gemini extracts facts. Deterministic policy checks and verified human approvals make the decision.</p>
    </div>

    <div className="grid grid-cols-1 xl:grid-cols-3 gap-7 items-start">
      <section className="xl:col-span-2 bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <div className="flex items-center gap-2 font-bold text-white"><Brain className="h-5 w-5 text-cyan-400" /> Hiring instruction</div>
        <textarea value={instruction} onChange={event => setInstruction(event.target.value)} rows={9}
          placeholder="Hire Sharma as a Software Engineer in Hyderabad, joining 1 August 2026..."
          className="mt-5 w-full bg-slate-950 border border-slate-800 rounded-xl p-4 text-sm leading-6 outline-none focus:border-cyan-500/50" />
        <button onClick={parse} disabled={parsing || !instruction.trim()}
          className="mt-4 flex items-center gap-2 bg-cyan-500 text-slate-950 px-5 py-3 rounded-xl text-xs font-bold disabled:opacity-40">
          {parsing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
          {parsing ? 'Parsing with Gemini…' : 'Parse hiring instruction'}
        </button>
      </section>

      <aside className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <h2 className="text-sm font-bold text-white">Execution guarantees</h2>
        <ul className="mt-4 space-y-3 text-xs text-slate-400">
          {['No writes before confirmation', 'Server-side policy validation', 'Role-bound approvals', 'Idempotent requestId', 'Employee only after SMTP success'].map(item =>
            <li key={item} className="flex gap-2"><CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />{item}</li>)}
        </ul>
        {requestId && <div className="mt-5 border-t border-slate-800 pt-4"><span className="text-[9px] uppercase text-slate-500">Request ID</span>
          <p className="text-[10px] font-mono text-cyan-400 break-all mt-1">{requestId}</p></div>}
      </aside>
    </div>

    {error && <div className="bg-rose-500/10 border border-rose-500/20 rounded-xl p-4 text-sm text-rose-300 flex gap-3">
      <AlertTriangle className="h-5 w-5 shrink-0" /><div><strong>Real service error</strong><p className="mt-1">{error}</p>
        {candidate && <p className="text-xs text-rose-200/70 mt-2">Correct the fields manually below. Nothing has been created.</p>}</div>
    </div>}

    {candidate && <motion.section initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
      className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div><h2 className="text-lg font-bold text-white">Confirmation preview</h2>
          <p className="text-xs text-slate-400 mt-1">Review and correct every field before creating the governed workflow.</p></div>
        {parseResult && <span className="text-xs font-mono text-cyan-400">Gemini confidence {(parseResult.confidence * 100).toFixed(0)}%</span>}
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mt-6">
        {fields.map(field => <label key={field.key} className="text-xs text-slate-400">{field.label}
          <input type={field.type || 'text'} value={(candidate[field.key] as string | number | null) ?? ''}
            onChange={event => update(field.key, event.target.value)}
            className="mt-2 w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-slate-100 outline-none focus:border-cyan-500/50" />
        </label>)}
        <label className="text-xs text-slate-400 md:col-span-2 lg:col-span-3">Required skills
          <input value={candidate.requiredSkills.join(', ')}
            onChange={event => setCandidate({ ...candidate, requiredSkills: event.target.value.split(',').map(v => v.trim()).filter(Boolean) })}
            className="mt-2 w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-slate-100 outline-none focus:border-cyan-500/50" />
        </label>
      </div>
      {missing.length > 0 && <p className="mt-4 text-xs text-amber-400">Required fields missing: {missing.join(', ')}</p>}
      <div className="mt-6 flex justify-end">
        <button onClick={confirm} disabled={confirming || missing.length > 0}
          className="flex items-center gap-2 bg-emerald-500 text-slate-950 px-5 py-3 rounded-xl text-xs font-bold disabled:opacity-40">
          {confirming ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShieldCheck className="h-4 w-4" />}
          {confirming ? 'Running policy checks…' : 'Confirm and create workflow'}
        </button>
      </div>
    </motion.section>}
  </motion.div>;
};
export default AICommandCenter;
