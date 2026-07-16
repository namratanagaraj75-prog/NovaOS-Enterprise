import React, { useEffect, useState, useMemo } from 'react';
import { collection, onSnapshot } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { FileCheck, ShieldCheck, Loader2 } from 'lucide-react';
import { db } from '../lib/firebase';
import { useAuth } from '../context/AuthContext';
import { normalizeDate } from '../lib/dateUtils';

const formatApprovalTime = (v: any) => {
  const d = normalizeDate(v);
  if (!d) return '—';
  if (Number.isNaN(d.getTime())) return '—';
  const day = d.getDate();
  const monthNames = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  ];
  const month = monthNames[d.getMonth()];
  const year = d.getFullYear();
  let hours = d.getHours();
  const minutes = String(d.getMinutes()).padStart(2, '0');
  const ampm = hours >= 12 ? 'AM' : 'PM';
  hours = hours % 12;
  hours = hours ? hours : 12;
  return `${day} ${month} ${year}, ${hours}:${minutes} ${ampm}`;
};

const formatStage = (status: string) => {
  if (!status) return 'Unknown';
  return status.replace(/_/g, ' ');
};

export const ApprovalInbox: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [requests, setRequests] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const role = (user?.role || '').toUpperCase();
  const uid = user?.uid;

  useEffect(() => {
    const unsubscribe = onSnapshot(
      collection(db, 'hiringRequests'),
      (snapshot) => {
        setRequests(snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() })));
        setLoading(false);
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      }
    );
    return unsubscribe;
  }, []);

  const pending = useMemo(() => {
    if (role === 'HR_ADMIN' || role === 'SUPER_ADMIN') {
      // HR should see all requests and their current stage
      return requests;
    } else if (role === 'HIRING_MANAGER') {
      return requests.filter(
        (r) => r.status === 'PENDING_MANAGER_APPROVAL' && r.hiringManagerId === uid
      );
    } else if (role === 'FINANCE') {
      return requests.filter((r) => r.status === 'PENDING_FINANCE_APPROVAL');
    } else if (role === 'LEGAL') {
      return requests.filter((r) => r.status === 'PENDING_LEGAL_APPROVAL');
    } else if (role === 'CEO') {
      return requests.filter((r) => r.status === 'PENDING_CEO_APPROVAL');
    }
    return [];
  }, [requests, role, uid]);

  return (
    <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6 text-slate-200">
      <div className="flex justify-between items-center border-b border-slate-800 pb-4 mb-4">
        <div>
          <h2 className="font-bold text-lg text-white flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-cyan-400" /> Pending Approvals
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            {role === 'HR_ADMIN' || role === 'SUPER_ADMIN'
              ? 'Organization-wide hiring request tracking.'
              : 'Governed hiring approvals assigned to your role.'}
          </p>
        </div>
        <span className="text-xs font-mono bg-slate-950 border border-slate-800 px-3 py-1 rounded-full text-cyan-400">
          {pending.length} Requests
        </span>
      </div>

      {loading ? (
        <div className="py-12 flex justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-cyan-400" />
        </div>
      ) : error ? (
        <p className="text-rose-400 text-xs py-4">Failed to load pending requests: {error}</p>
      ) : pending.length ? (
        <div className="grid gap-4">
          {pending.map((item) => (
            <div
              key={item.id}
              className="bg-slate-950 border border-slate-800 rounded-xl p-5 flex flex-col md:flex-row justify-between items-start md:items-center gap-4 transition-all hover:border-cyan-500/20"
            >
              <div className="space-y-1 flex-1">
                <div className="flex items-center gap-3">
                  <strong className="text-white text-base">{item.candidateName}</strong>
                  <span className="text-[9px] font-mono bg-slate-900 text-amber-400 px-2 py-0.5 rounded border border-amber-500/10">
                    {formatStage(item.status)}
                  </span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-x-6 gap-y-1 text-xs text-slate-400 pt-1">
                  <p>
                    <span className="text-slate-500">Position:</span> {item.jobTitle}
                  </p>
                  <p>
                    <span className="text-slate-500">Department:</span> {item.department || '—'}
                  </p>
                  <p>
                    <span className="text-slate-500">Package:</span> ₹{item.annualPackageLPA} LPA
                  </p>
                  <p>
                    <span className="text-slate-500">Joining Date:</span> {item.joiningDate}
                  </p>
                  <p>
                    <span className="text-slate-500">Requested By:</span> {item.createdByName}
                  </p>
                  <p>
                    <span className="text-slate-500">Created:</span> {formatApprovalTime(item.createdAt)}
                  </p>
                </div>
              </div>
              <button
                onClick={() => navigate('/hiring-requests/' + item.id)}
                className="bg-cyan-500 text-slate-950 px-4 py-2 rounded-xl text-xs font-bold hover:bg-cyan-400 transition-all shadow-glow-cyan w-full md:w-auto text-center"
              >
                Review
              </button>
            </div>
          ))}
        </div>
      ) : (
        <div className="py-12 text-center text-xs text-slate-500">
          <FileCheck className="h-8 w-8 mx-auto mb-2 text-slate-600" />
          No approvals awaiting your role.
        </div>
      )}
    </section>
  );
};

export default ApprovalInbox;
