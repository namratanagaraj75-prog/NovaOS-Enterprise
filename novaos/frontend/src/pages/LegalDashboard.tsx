import React, { useState } from 'react';
import chatService from '../services/chatService';
import { Brain, FileText, Sparkles, Send, Copy, Check, RefreshCw } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { useAuth } from '../context/AuthContext';
import firestoreService from '../services/firestoreService';
import ApprovalInbox from '../components/ApprovalInbox';
import DashboardStatusCounts from '../components/DashboardStatusCounts';

export const LegalDashboard: React.FC = () => {
  const { user } = useAuth();
  const { showToast } = useToast();

  const [docType, setDocType] = useState('Non-Disclosure Agreement (NDA)');
  const [partyName, setPartyName] = useState('');
  const [scope, setScope] = useState('');
  const [effectiveDate, setEffectiveDate] = useState(new Date().toISOString().split('T')[0]);

  const [generatedDoc, setGeneratedDoc] = useState<string | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [copied, setCopied] = useState(false);

  const docTypes = [
    'Non-Disclosure Agreement (NDA)',
    'Standard Employment Contract',
    'Intellectual Property (IP) Assignment Agreement',
    'Independent Consultancy Agreement',
    'Corporate Ethics & Code of Conduct'
  ];

  const handleGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!partyName || !scope) {
      showToast("Party Name and Key Terms/Scope are required.", "error");
      return;
    }

    setIsGenerating(true);
    setGeneratedDoc(null);
    setCopied(false);

    try {
      const prompt = `Act as an expert corporate general counsel. Draft a detailed, legally robust ${docType} in professional markdown. 
Party/Candidate Name: "${partyName}"
Effective Date: "${effectiveDate}"
Scope & Provisions: "${scope}"

Ensure standard confidentiality, jurisdiction, and governing law clauses are included. Provide only the contract template contents.`;

      const result = await chatService.sendPrompt(prompt);
      setGeneratedDoc(result.responseText);

      await firestoreService.writeAuditLog(
        `Generated contract: ${docType}`,
        user?.email || 'LEGAL_TEAM',
        `Drafted contract for ${partyName} covering scope: ${scope.slice(0, 50)}...`
      );

      showToast("Contract drafted successfully via Gemini AI.", "success");
    } catch (err: any) {
      console.error(err);
      showToast("Failed to draft contract using AI Engine: " + err.message, "error");
    } finally {
      setIsGenerating(false);
    }
  };

  const handleCopy = () => {
    if (!generatedDoc) return;
    navigator.clipboard.writeText(generatedDoc);
    setCopied(true);
    showToast("Copied contract content to clipboard.", "success");
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-8 bg-[#030014] text-slate-200 font-sans">
      <DashboardStatusCounts />
      <ApprovalInbox />
      {/* Header welcome console */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
        <div>
          <h1 className="text-3xl font-extrabold text-white tracking-tight">
            Legal AI <span className="text-cyan-500">Drafting Portal</span>
          </h1>
          <p className="text-gray-400 text-sm mt-1">
            Draft robust corporate templates, NDAs, and clearance contracts using Gemini Pro intelligence.
          </p>
        </div>
        <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-4 py-2 rounded-xl text-slate-200 font-medium text-xs">
          <Brain className="h-4 w-4 text-cyan-500 shrink-0" />
          <span className="font-mono text-cyan-400">Legal Copilot Active</span>
        </div>
      </div>

      {/* Main split grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
        {/* Left pane: Parameters Form (1/3 width) */}
        <div className="space-y-6">
          <form onSubmit={handleGenerate} className="bg-slate-900 border border-slate-800 p-6.5 rounded-3xl relative overflow-hidden shadow-2xl space-y-4">
            <div className="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-cyan-500 to-violet-600" />
            
            <div>
              <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider font-mono">Contract Parameters</h3>
              <p className="text-[11px] text-gray-500">Define provisions and party scopes.</p>
            </div>

            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1.5">Document Type</label>
              <select 
                value={docType}
                onChange={(e) => setDocType(e.target.value)}
                className="w-full px-3 py-2.5 bg-slate-950 border border-white/10 rounded-xl text-xs text-white focus:outline-none focus:border-cyan-500"
              >
                {docTypes.map(d => <option key={d} value={d}>{d}</option>)}
              </select>
            </div>

            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1.5">Party / Candidate Name</label>
              <input 
                type="text"
                placeholder="e.g. Jane Doe"
                value={partyName}
                onChange={(e) => setPartyName(e.target.value)}
                className="w-full px-3 py-2.5 bg-slate-950 border border-white/10 rounded-xl text-xs text-white focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1.5">Effective Date</label>
              <input 
                type="date"
                value={effectiveDate}
                onChange={(e) => setEffectiveDate(e.target.value)}
                className="w-full px-3 py-2.5 bg-slate-950 border border-white/10 rounded-xl text-xs text-white focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1.5">Key Terms & Scope</label>
              <textarea 
                rows={4}
                placeholder="e.g. Candidate receives access to intellectual systems. Standard 2-year confidentiality period, governing law of California."
                value={scope}
                onChange={(e) => setScope(e.target.value)}
                className="w-full px-3 py-2.5 bg-slate-950 border border-white/10 rounded-xl text-xs text-white focus:outline-none focus:border-cyan-500"
              />
            </div>

            <button 
              type="submit"
              disabled={isGenerating}
              className="w-full flex items-center justify-center gap-2 bg-gradient-to-r from-cyan-500 to-violet-600 hover:from-cyan-400 hover:to-violet-500 text-white py-2.5 rounded-xl text-xs font-semibold shadow-glow-violet transition-all duration-200"
            >
              {isGenerating ? (
                <>
                  <RefreshCw className="h-4 w-4 animate-spin" />
                  <span>Drafting Document...</span>
                </>
              ) : (
                <>
                  <Sparkles className="h-4 w-4 fill-white" />
                  <span>Draft Contract with AI</span>
                </>
              )}
            </button>
          </form>
        </div>

        {/* Right pane: Document Canvas (2/3 width) */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-slate-900 border border-white/5 backdrop-blur-xl p-6.5 rounded-3xl relative overflow-hidden shadow-2xl min-h-[500px] flex flex-col">
            <div className="absolute top-0 left-0 w-full h-[1px] bg-gradient-to-r from-transparent via-cyan-500/20 to-transparent" />
            
            <div className="flex justify-between items-center mb-6 border-b border-white/5 pb-4">
              <div>
                <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider font-mono">Contract Draft Canvas</h3>
                <p className="text-[11px] text-gray-500">Live AI Output Terminal.</p>
              </div>
              {generatedDoc && (
                <button 
                  onClick={handleCopy}
                  className="flex items-center gap-1.5 p-2 bg-slate-950 border border-white/10 hover:border-white/20 text-gray-400 hover:text-white rounded-xl text-[10px] font-mono transition-all"
                >
                  {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                  <span>{copied ? "Copied!" : "Copy Text"}</span>
                </button>
              )}
            </div>

            {/* AI Document Editor Terminal */}
            <div className="flex-1 flex flex-col justify-center">
              {isGenerating ? (
                <div className="flex flex-col items-center justify-center gap-3 py-20">
                  <span className="h-8 w-8 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin"></span>
                  <span className="text-xs text-slate-400 font-mono">Orchestrating legal clauses via Gemini Pro...</span>
                </div>
              ) : generatedDoc ? (
                <div className="text-xs text-slate-300 font-mono space-y-4 whitespace-pre-wrap leading-loose select-text p-6 md:p-8 border border-white/5 rounded-2xl overflow-y-auto max-h-[600px] break-words mx-1 my-1 bg-slate-950/50">
                  {generatedDoc}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center text-center p-10 py-20 text-gray-500 space-y-2">
                  <FileText className="h-12 w-12 text-slate-600" />
                  <span className="text-xs font-mono uppercase tracking-wider font-bold">Document Canvas Idle</span>
                  <p className="text-[11px] text-gray-600 max-w-xs leading-normal">Fill in the contract parameters on the left and click draft to compile standard corporate clearance docs.</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LegalDashboard;
