import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2, Clock, GitFork, XCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useAppContext } from '../context/AppContext';
import { useToast } from '../context/ToastContext';
import { decideHiring, rejectHiring } from '../services/hiringRequestService';

export const ManagerDashboard: React.FC = () => {
  const navigate = useNavigate(); const { user } = useAuth(); const { hiringRequests } = useAppContext(); const { showToast } = useToast();
  const [busy, setBusy] = useState(''); const [rejecting, setRejecting] = useState(''); const [reason, setReason] = useState('');
  const assigned = useMemo(() => hiringRequests.filter(item => user?.role !== 'HIRING_MANAGER' || item.hiringManagerId === user.uid), [hiringRequests,user]);
  const pending = assigned.filter(item => item.status === 'PENDING_MANAGER_APPROVAL');
  const approved = assigned.filter(item => item.managerApprovalStatus === 'APPROVED');
  const rejected = assigned.filter(item => item.status === 'REJECTED' || item.managerApprovalStatus === 'REJECTED');

  const approve = async (id: string) => { setBusy(id); try { await decideHiring(id,'APPROVE','Candidate reviewed and approved by Hiring Manager.'); showToast('Manager approval recorded.','success'); } catch (error:any) { showToast(error.response?.data?.detail || error.message,'error'); } finally { setBusy(''); } };
  const reject = async (id: string) => { if (!reason.trim()) { showToast('A rejection reason is required.','warning'); return; } setBusy(id); try { await rejectHiring(id,reason.trim()); showToast('Candidate rejection recorded.','success'); setRejecting(''); setReason(''); } catch (error:any) { showToast(error.response?.data?.detail || error.message,'error'); } finally { setBusy(''); } };

  const card = (item:any, actions=false) => <article key={item.id} className="bg-slate-950 border border-slate-800 rounded-xl p-5">
    <div className="flex justify-between gap-4"><div><h3 className="font-bold text-white">{item.candidateName}</h3><p className="text-xs text-slate-400 mt-1">{item.jobTitle} · {item.department || 'Department not recorded'}</p></div><span className="text-[9px] font-mono text-cyan-400">{item.status.replace(/_/g,' ')}</span></div>
    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-[10px] mt-4 text-slate-400"><span>AI score<br/><b className="text-white">{item.aiMatchScore ?? item.matchScore ?? 'Not scored'}</b></span><span>Salary<br/><b className="text-white">{item.annualPackageLPA ? `${item.annualPackageLPA} LPA` : 'Not recorded'}</b></span><span>Experience<br/><b className="text-white">{item.experience || 'Not recorded'}</b></span><span>HR notes<br/><b className="text-white">{item.hrNotes || item.originalInstruction || 'Not recorded'}</b></span></div>
    <div className="flex flex-wrap gap-2 mt-5"><button onClick={() => navigate('/hiring-requests/'+item.id)} className="border border-slate-700 px-3 py-2 rounded-lg text-xs">Open candidate</button>{actions && user?.role === 'HIRING_MANAGER' && <><button disabled={!!busy} onClick={() => approve(item.id)} className="bg-emerald-500 text-slate-950 px-3 py-2 rounded-lg text-xs font-bold">Approve</button><button disabled={!!busy} onClick={() => setRejecting(item.id)} className="border border-rose-500/30 text-rose-300 px-3 py-2 rounded-lg text-xs">Reject</button></>}</div>
    {rejecting === item.id && <div className="mt-4 border-t border-slate-800 pt-4"><textarea value={reason} onChange={event=>setReason(event.target.value)} placeholder="Required rejection reason" className="w-full bg-slate-900 border border-slate-700 rounded-xl p-3 text-xs"/><div className="flex gap-2 mt-2"><button onClick={()=>reject(item.id)} className="bg-rose-500 px-3 py-2 rounded-lg text-xs font-bold text-white">Confirm rejection</button><button onClick={()=>{setRejecting('');setReason('')}} className="text-xs text-slate-400">Cancel</button></div></div>}
  </article>;

  const sections = [["Pending Manager Approvals",pending,true,Clock],["Approved Candidates",approved,false,CheckCircle2],["Rejected Candidates",rejected,false,XCircle]] as const;
  return <div className="space-y-8 text-slate-200"><div className="border-b border-slate-800 pb-6"><h1 className="text-3xl font-extrabold text-white flex items-center gap-3"><GitFork className="h-7 w-7 text-violet-400" /> Manager Task Center</h1><p className="text-sm text-slate-400 mt-2">Tasks and decisions use the existing governed hiring-request workflow.</p></div>
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">{sections.map(([title,items,,Icon])=><div key={title} className="bg-slate-900 border border-slate-800 p-5 rounded-2xl"><Icon className="h-5 w-5 text-cyan-400"/><p className="text-xs text-slate-400 mt-3">{title}</p><strong className="text-2xl text-white">{items.length}</strong></div>)}</div>
    {sections.map(([title,items,actions])=><section key={title} className="bg-slate-900 border border-slate-800 rounded-2xl p-6"><h2 className="font-bold text-white mb-4">{title}</h2>{items.length?<div className="grid gap-4">{items.slice(0,8).map(item=>card(item,actions))}</div>:<p className="text-xs text-slate-500 py-8 text-center">No candidates in this category.</p>}</section>)}
  </div>;
};
export default ManagerDashboard;
