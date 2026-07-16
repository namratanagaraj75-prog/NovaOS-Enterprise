import React, { useEffect, useState } from "react";
import {
  CheckCircle2,
  Clock,
  Download,
  Loader2,
  Mail,
  RefreshCw,
  Send,
  ShieldCheck,
  XCircle,
} from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import {
  decideHiring,
  fetchHiringPdf,
  getHiring,
  HiringRequest,
  sendHiringEmail,
  submitHiring,
} from "../services/hiringRequestService";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { doc, updateDoc, arrayUnion, onSnapshot } from "firebase/firestore";
import { db } from "../lib/firebase";
import { normalizeDate, formatNormalizedDate } from "../lib/dateUtils";

const when = (v: any) => {
  const d = normalizeDate(v);
  if (!d) return "—";
  const day = d.getDate();
  const months = ["January","February","March","April","May","June","July","August","September","October","November","December"];
  let h = d.getHours(); const m = String(d.getMinutes()).padStart(2,"0"); const ampm = h >= 12 ? "PM" : "AM"; h = h % 12 || 12;
  return `${day} ${months[d.getMonth()]} ${d.getFullYear()}, ${h}:${m} ${ampm}`;
};
const errorOf = (e: any) =>
  e.response?.data?.detail || e.response?.data?.message || e.message;
export default function HiringRequestDetails() {
  const { id = "" } = useParams();
  const nav = useNavigate();
  const { user, isLoading } = useAuth();
  const { showToast } = useToast();
  const [item, setItem] = useState<HiringRequest | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [reason, setReason] = useState("");

  useEffect(() => {
    if (!id || !user?.uid) return;
    updateDoc(doc(db, "hiringRequests", id), {
      readBy: arrayUnion(user.uid)
    }).catch(e => console.error("Failed to mark hiring request as read", e));
  }, [id, user?.uid]);

  useEffect(() => {
    if (isLoading || !user || !id) return;

    setError("");
    setItem(null);

    getHiring(id)
      .then((request) => {
        setItem(request);
      })
      .catch((requestError) => {
        setError(errorOf(requestError));
      });

    // Listen to real-time updates from Firestore to synchronize state instantly cross-device
    const unsubscribe = onSnapshot(
      doc(db, "hiringRequests", id),
      (snap) => {
        if (snap.exists()) {
          setItem({ id: snap.id, ...snap.data() } as any);
        }
      },
      (err) => {
        console.error("Firestore listener failed", err);
      }
    );

    return () => {
      unsubscribe();
    };
  }, [id, isLoading, user?.uid]);

  const run = async (fn: () => Promise<HiringRequest>, msg: string) => {
    setBusy(true);
    setError("");
    try {
      setItem(await fn());
      showToast(msg, "success");
    } catch (e) {
      const errMsg = errorOf(e);
      setError(errMsg);
      showToast(errMsg, "error");
    } finally {
      setBusy(false);
    }
  };
  const openPdf = async (download = false) => {
    setBusy(true);
    try {
      const blob = await fetchHiringPdf(id);
      const url = URL.createObjectURL(blob);
      if (download) {
        const a = document.createElement("a");
        a.href = url;
        a.download = item?.pdfFileName || "offer.pdf";
        a.click();
      } else window.open(url, "_blank", "noopener");
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (e) {
      setError(errorOf(e));
    } finally {
      setBusy(false);
    }
  };
  if (!item)
    return (
      <div className="text-slate-300">
        {error || <Loader2 className="animate-spin" />}
      </div>
    );
  const role = user?.role;
  const expectedStatus =
    role === "HIRING_MANAGER"
      ? "PENDING_MANAGER_APPROVAL"
      : role === "FINANCE"
        ? "PENDING_FINANCE_APPROVAL"
        : role === "LEGAL"
          ? "PENDING_LEGAL_APPROVAL"
          : role === "CEO"
            ? "PENDING_CEO_APPROVAL"
            : "";
  const approver = Boolean(expectedStatus && item.status === expectedStatus);
  const approvalMessage =
    "Approval recorded and workflow advanced automatically";
  const hr = role === "HR_ADMIN";
  const passport = item.decisionPassport || {};
  const route = item.approvalRoute || [];
  const approvalComplete = [
    "APPROVALS_COMPLETED",
    "GENERATING_OFFER",
    "OFFER_GENERATED",
    "EMAIL_SENDING",
    "EMAIL_SENT",
    "WORKFLOW_COMPLETED",
  ].includes(item.status);

  // Deterministic 6-step timeline: HR → Manager → Finance → Legal → Offer → Email
  type TStep = { label: string; status: "done" | "active" | "failed" | "pending"; timestamp?: string };
  const tSteps: TStep[] = [
    { label: "HR Submitted", status: "done", timestamp: when(item.createdAt) },
    {
      label: "Hiring Manager",
      status: item.managerApprovalStatus === "APPROVED" ? "done"
        : item.managerApprovalStatus === "REJECTED" ? "failed"
        : item.status === "PENDING_MANAGER_APPROVAL" ? "active" : "pending",
      timestamp: item.managerApprovedAt ? when(item.managerApprovedAt) : undefined,
    },
    {
      label: "Finance",
      status: item.financeApprovalStatus === "APPROVED" ? "done"
        : item.financeApprovalStatus === "REJECTED" ? "failed"
        : item.status === "PENDING_FINANCE_APPROVAL" ? "active" : "pending",
      timestamp: item.financeApprovedAt ? when(item.financeApprovedAt) : undefined,
    },
    {
      label: "Legal",
      status: item.legalApprovalStatus === "APPROVED" ? "done"
        : item.legalApprovalStatus === "REJECTED" ? "failed"
        : item.status === "PENDING_LEGAL_APPROVAL" ? "active" : "pending",
      timestamp: item.legalApprovedAt ? when(item.legalApprovedAt) : undefined,
    },
    {
      label: "Offer Generated",
      status: item.offerLetterStatus === "GENERATED" ? "done"
        : item.offerLetterStatus === "FAILED" ? "failed"
        : item.status === "GENERATING_OFFER" ? "active" : "pending",
      timestamp: item.offerLetterGeneratedAt ? when(item.offerLetterGeneratedAt) : undefined,
    },
    {
      label: "Email Sent",
      status: item.emailStatus === "SENT" ? "done"
        : item.emailStatus === "FAILED" ? "failed"
        : item.emailStatus === "SENDING" ? "active" : "pending",
      timestamp: item.emailSentAt ? when(item.emailSentAt) : undefined,
    },
  ];

  // Approvals data for the governed sections
  const approvalBlocks = [
    { role: "Hiring Manager", name: item.managerApprovedByName, status: item.managerApprovalStatus, comment: item.managerApprovalComment, at: item.managerApprovedAt, email: item.managerApprovedByEmail },
    { role: "Finance", name: item.financeApprovedByName, status: item.financeApprovalStatus, comment: item.financeApprovalComment, at: item.financeApprovedAt, email: item.financeApprovedByEmail },
    { role: "Legal", name: item.legalApprovedByName, status: item.legalApprovalStatus, comment: item.legalApprovalComment, at: item.legalApprovedAt, email: item.legalApprovedByEmail },
  ];

  return (
    <div className="space-y-6 text-slate-200">
      <div className="flex flex-wrap justify-between gap-4 border-b border-slate-800 pb-5">
        <div>
          <p className="text-[10px] font-mono text-cyan-400">{item.id}</p>
          <h1 className="text-3xl font-extrabold text-white mt-2">
            {item.candidateName}
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            {item.jobTitle} · ₹{item.annualPackageLPA} LPA
          </p>
        </div>
        <span className="h-fit rounded-full border border-cyan-500/20 px-4 py-2 text-xs text-cyan-400">
          {item.status.replace(/_/g, " ")}
        </span>
      </div>
      {error && (
        <p className="bg-rose-500/10 border border-rose-500/20 rounded-xl p-4 text-rose-300 text-sm">
          {error}
        </p>
      )}
      <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <h2 className="font-bold text-white mb-5">Workflow Timeline</h2>
        <div className="flex overflow-x-auto pb-2 gap-0">
          {tSteps.map((step, index) => (
            <div key={step.label} className="flex min-w-fit items-center">
              <div className="text-center w-32 px-1">
                <span
                  className={
                    "mx-auto flex h-9 w-9 items-center justify-center rounded-full border-2 text-xs font-bold " +
                    (step.status === "done"
                      ? "bg-emerald-500/15 border-emerald-500 text-emerald-300"
                      : step.status === "failed"
                      ? "bg-rose-500/15 border-rose-500 text-rose-300"
                      : step.status === "active"
                      ? "bg-amber-500/15 border-amber-500 text-amber-300 animate-pulse"
                      : "border-slate-700 text-slate-600")
                  }
                >
                  {step.status === "done" ? (
                    <CheckCircle2 className="h-4 w-4" />
                  ) : step.status === "failed" ? (
                    <XCircle className="h-4 w-4" />
                  ) : step.status === "active" ? (
                    <Clock className="h-4 w-4" />
                  ) : (
                    <span className="text-[10px]">○</span>
                  )}
                </span>
                <p className="text-[10px] text-slate-400 mt-2 leading-tight">{step.label}</p>
                {step.timestamp && (
                  <p className="text-[9px] text-slate-600 mt-1 leading-tight">{step.timestamp}</p>
                )}
              </div>
              {index < tSteps.length - 1 && (
                <span
                  className={
                    "h-px w-6 flex-shrink-0 " +
                    (step.status === "done" ? "bg-emerald-500/40" : "bg-slate-800")
                  }
                />
              )}
            </div>
          ))}
        </div>
      </section>

      {/* Email Delivery Status Section */}
      <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <div className="flex flex-wrap justify-between items-center gap-4 mb-4">
          <div>
            <h2 className="font-bold text-white flex items-center gap-2">
              <Mail className="h-4 w-4 text-cyan-400" />
              Email Delivery Status
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              Direct secure SMTP delivery status for candidate offer letter.
            </p>
          </div>
          <span
            className={`px-3 py-1 rounded-full text-xs font-bold uppercase ${
              item.emailStatus === "SENT"
                ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                : item.emailStatus === "FAILED"
                ? "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                : item.emailStatus === "SENDING"
                ? "bg-amber-500/10 text-amber-400 border border-amber-500/20 animate-pulse"
                : item.emailStatus === "PENDING"
                ? "bg-blue-500/10 text-blue-400 border border-blue-500/20"
                : "bg-slate-800 text-slate-400 border border-slate-700"
            }`}
          >
            {item.emailStatus ? item.emailStatus.replace(/_/g, " ") : "NOT READY"}
          </span>
        </div>
        
        <div className="grid md:grid-cols-2 gap-4 text-sm mt-4 border-t border-slate-800/60 pt-4">
          <div>
            <p className="text-[10px] uppercase text-slate-500 tracking-wider">Candidate Recipient</p>
            <p className="text-white font-medium mt-1">{item.candidateEmail}</p>
          </div>
          <div>
            <p className="text-[10px] uppercase text-slate-500 tracking-wider">Delivery Timestamp</p>
            <p className="text-slate-300 mt-1">{item.emailSentAt ? when(item.emailSentAt) : "—"}</p>
          </div>
          {item.emailRetryCount !== undefined && item.emailRetryCount > 0 && (
            <div>
              <p className="text-[10px] uppercase text-slate-500 tracking-wider">Retry/Resend Attempts</p>
              <p className="text-slate-300 mt-1">{item.emailRetryCount}</p>
            </div>
          )}
          {item.emailMessageId && (
            <div>
              <p className="text-[10px] uppercase text-slate-500 tracking-wider">SMTP Message ID</p>
              <p className="text-xs font-mono text-slate-400 mt-1 truncate">{item.emailMessageId}</p>
            </div>
          )}
          {(item.emailFailureReason || item.emailError || item.emailErrorMessage) && (
            <div className="md:col-span-2 bg-slate-950/60 p-4 rounded-xl border border-rose-500/20 mt-2">
              <p className="text-[10px] uppercase text-rose-400 font-bold tracking-wider mb-1">Error Message</p>
              <p className="text-xs font-mono text-rose-300 whitespace-pre-wrap">
                {item.emailErrorMessage || item.emailFailureReason || item.emailError}
              </p>
            </div>
          )}
        </div>
      </section>

      {/* Governed Approval Decisions */}
      <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <h2 className="font-bold text-white mb-5">Governed Approval Decisions</h2>
        <div className="grid md:grid-cols-3 gap-4">
          {approvalBlocks.map((b) => (
            <div key={b.role} className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-1">
              <p className="text-[10px] font-bold uppercase text-slate-500 tracking-wider">{b.role}</p>
              {b.name ? (
                <>
                  <p className="text-sm text-white font-semibold">{b.name}</p>
                  {b.email && <p className="text-[10px] text-slate-500">{b.email}</p>}
                  <span
                    className={`inline-block mt-1 px-2 py-0.5 rounded text-[9px] font-bold uppercase ${
                      b.status === "APPROVED"
                        ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                        : b.status === "REJECTED"
                        ? "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                        : "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                    }`}
                  >
                    {b.status || "Pending"}
                  </span>
                  {b.at && (
                    <p className="text-[10px] text-slate-500 mt-1">{when(b.at)}</p>
                  )}
                  {b.comment && (
                    <p className="text-[10px] text-slate-400 italic mt-1">"{b.comment}"</p>
                  )}
                </>
              ) : (
                <p className="text-xs text-slate-600 mt-1">Awaiting decision</p>
              )}
            </div>
          ))}
        </div>
      </section>

      <section className="grid md:grid-cols-2 gap-4 bg-slate-900 border border-slate-800 rounded-2xl p-6">
        {[
          ["Email", item.candidateEmail],
          ["Joining date", item.joiningDate],
          ["Department", item.department || "—"],
          ["Reporting manager", item.reportingManagerName],
          ["Hiring manager", item.hiringManagerName],
          ["Created by", item.createdByName],
          ["Location", item.location || "—"],
          ["Employment type", item.employmentType || "—"],
        ].map(([a, b]) => (
          <div key={a}>
            <p className="text-[10px] uppercase text-slate-500">{a}</p>
            <p className="text-sm text-white mt-1">{b}</p>
          </div>
        ))}
      </section>
      <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <div className="flex flex-wrap justify-between gap-3">
          <div>
            <h2 className="font-bold text-white">AI Decision Passport</h2>
            <p className="text-xs text-slate-400 mt-1">
              Explainable deterministic policy evidence and dynamic routing.
            </p>
          </div>
          <div className="text-right flex items-center">
            <span className="text-xs font-mono font-bold text-cyan-400 border border-cyan-500/20 rounded-full px-3 py-1">
              {item.decision || "NOT EVALUATED"}
            </span>
          </div>
        </div>
        <p className="text-xs text-slate-300 mt-4">
          {passport.explanation ||
            "Policy evidence will appear after HR confirmation."}
        </p>
        {passport.recommendation && (
          <p className="text-xs text-cyan-300 mt-3">
            <strong>Recommendation:</strong> {passport.recommendation}
          </p>
        )}
        <div className="flex flex-wrap gap-2 mt-4">
          {route.map((step, index) => (
            <span
              key={step}
              className={`border rounded-lg px-3 py-1.5 text-[10px] ${index < (item.currentApprovalIndex || 0) ? "border-emerald-500/30 text-emerald-400" : index === (item.currentApprovalIndex || 0) && item.currentApproverRole === step ? "border-amber-500/30 text-amber-400" : "border-slate-700 text-slate-500"}`}
            >
              {index + 1}. {step.replace(/_/g, " ")}
            </span>
          ))}
        </div>
        <div className="grid md:grid-cols-2 gap-3 mt-5">
          {(item.policyChecks || []).map((check: any) => (
            <div
              key={check.name}
              className="bg-slate-950 border border-slate-800 rounded-xl p-3"
            >
              <div className="flex justify-between gap-2">
                <strong className="text-xs text-white">{check.name}</strong>
                <span
                  className={`text-[9px] ${check.status === "FAIL" ? "text-rose-400" : check.status === "WARNING" ? "text-amber-400" : "text-emerald-400"}`}
                >
                  {check.status}
                </span>
              </div>
              <p className="text-[10px] text-slate-400 mt-1">{check.reason}</p>
            </div>
          ))}
        </div>
        {(item.fieldChangeHistory || []).length > 0 && (
          <div className="mt-5 border-t border-slate-800 pt-4">
            <h3 className="text-xs font-bold text-white">HR field changes</h3>
            {(item.fieldChangeHistory || []).map(
              (change: any, index: number) => (
                <p
                  key={change.field + index}
                  className="text-[10px] text-slate-400 mt-2"
                >
                  <span className="text-cyan-400">{change.field}</span>:{" "}
                  {String(change.oldValue ?? "—")} →{" "}
                  {String(change.newValue ?? "—")} · {change.changedByName}
                </p>
              ),
            )}
          </div>
        )}
      </section>
      <div className="flex flex-wrap gap-3">
        {hr && item.status === "DRAFT" && (
          <button
            disabled={busy}
            onClick={() =>
              run(() => submitHiring(id), "Submitted for manager approval")
            }
            className="bg-cyan-500 text-slate-950 px-4 py-2.5 rounded-xl text-xs font-bold"
          >
            <Send className="inline h-4 w-4 mr-2" />
            Submit for approval
          </button>
        )}
        {approver && (
          <>
            <button
              disabled={busy}
              onClick={() =>
                run(() => decideHiring(id, "APPROVE"), approvalMessage)
              }
              className="bg-emerald-500 text-slate-950 px-4 py-2.5 rounded-xl text-xs font-bold"
            >
              <ShieldCheck className="inline h-4 w-4 mr-2" />
              Approve
            </button>
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Reason required for reject/change"
              className="bg-slate-950 border border-slate-800 rounded-xl px-3 text-xs"
            />
            <button
              disabled={!reason || busy}
              onClick={() =>
                run(
                  () => decideHiring(id, "REJECT", reason),
                  "Request rejected",
                )
              }
              className="border border-rose-500/30 text-rose-300 px-4 py-2.5 rounded-xl text-xs"
            >
              <XCircle className="inline h-4 w-4 mr-2" />
              Reject
            </button>
            <button
              disabled={!reason || busy}
              onClick={() =>
                run(
                  () => decideHiring(id, "REQUEST_CHANGES", reason),
                  "Changes requested",
                )
              }
              className="border border-amber-500/30 text-amber-300 px-4 py-2.5 rounded-xl text-xs"
            >
              Request changes
            </button>
          </>
        )}
        {item.pdfUrl && (
          <>
            <button
              disabled={busy}
              onClick={() => openPdf()}
              className="border border-slate-700 px-4 py-2.5 rounded-xl text-xs"
            >
              Preview PDF
            </button>
            <button
              disabled={busy}
              onClick={() => openPdf(true)}
              className="border border-slate-700 px-4 py-2.5 rounded-xl text-xs"
            >
              <Download className="inline h-4 w-4 mr-2" />
              Download
            </button>
          </>
        )}
        {hr && (item.emailStatus === "FAILED" || item.emailStatus === "SENT") && (
          <button
            disabled={busy}
            onClick={() => {
              if (window.confirm(`Are you sure you want to resend the offer letter to ${item.candidateEmail}?`)) {
                run(() => sendHiringEmail(id, true), "Offer letter resent successfully");
              }
            }}
            className="bg-violet-600 hover:bg-violet-700 text-white px-4 py-2.5 rounded-xl text-xs font-bold transition-colors"
          >
            <Mail className="inline h-4 w-4 mr-2" />
            Resend Offer Letter
          </button>
        )}
      </div>
      <section className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
        <h2 className="font-bold text-white">Activity History</h2>
        <div className="mt-5 space-y-4">
          {(item.activityHistory || [])
            .slice()
            .sort((a: any, b: any) => {
              const ta = a.timestamp?.seconds ?? 0;
              const tb = b.timestamp?.seconds ?? 0;
              return tb - ta;
            })
            .map((a: any, i: number) => (
              <div key={i} className="border-l-2 border-cyan-500/30 pl-4">
                <div className="flex flex-wrap justify-between gap-2">
                  <strong className="text-xs text-cyan-300">
                    {(a.eventType || a.action || "").replace(/_/g, " ")}
                  </strong>
                  <span className="text-[10px] text-slate-500">
                    {when(a.timestamp)}
                  </span>
                </div>
                <p className="text-xs text-white mt-0.5">
                  {a.actorName || a.performedByName || "System"}
                  {a.actorRole && <span className="text-slate-500 ml-1">· {a.actorRole.replace(/_/g, " ")}</span>}
                  {a.actorEmail && <span className="text-slate-600 ml-1">({a.actorEmail})</span>}
                </p>
                <p className="text-[11px] text-slate-400 mt-0.5">{a.message || a.details}</p>
                {a.requestId && (
                  <p className="text-[9px] text-slate-600 font-mono mt-0.5">REQ: {a.requestId}</p>
                )}
              </div>
            ))}
        </div>
      </section>
    </div>
  );
}
