import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import chatService from '../services/chatService';
import firestoreService from '../services/firestoreService';
import { User, FileText, Send, Sparkles, Brain, Check, RefreshCw, Terminal } from 'lucide-react';

export const EmployeeDashboard: React.FC = () => {
  const { user } = useAuth();
  const { showToast } = useToast();

  // Workflow requests state
  const [requestType, setRequestType] = useState('Leave Application');
  const [details, setDetails] = useState('');
  const [isSubmittingRequest, setIsSubmittingRequest] = useState(false);

  // Chat states
  const [chatPrompt, setChatPrompt] = useState('');
  const [chatReply, setChatReply] = useState<string | null>(null);
  const [isThinking, setIsThinking] = useState(false);

  const requestTypes = [
    'Leave Application',
    'Hardware Provisioning Request',
    'Remote Work Authorization',
    'Corporate Expense Clearance'
  ];

  const handleRequestSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!details) {
      showToast("Please provide details/justification for the request.", "error");
      return;
    }

    setIsSubmittingRequest(true);
    try {
      await firestoreService.createWorkflowRequest({
        candidateName: user?.displayName || 'Employee User',
        requestedBy: user?.email || 'employee@novaos.com',
        status: 'Pending',
        stage: requestType,
        timestamp: new Date().toISOString()
      });

      await firestoreService.writeAuditLog(
        `Submitted clearance request: ${requestType}`,
        user?.email || 'EMPLOYEE',
        `Details: ${details.slice(0, 50)}...`
      );

      showToast("Clearance request logged in Firestore database.", "success");
      setDetails('');
    } catch (err: any) {
      console.error(err);
      showToast("Failed to file request: " + err.message, "error");
    } finally {
      setIsSubmittingRequest(false);
    }
  };

  const handleChatSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatPrompt.trim()) return;

    setIsThinking(true);
    setChatReply(null);
    try {
      const response = await chatService.sendPrompt(chatPrompt);
      setChatReply(response.responseText);
      setChatPrompt('');
    } catch (err: any) {
      console.error(err);
      showToast("Failed to contact AI Copilot: " + err.message, "error");
    } finally {
      setIsThinking(false);
    }
  };

  return (
    <div className="space-y-8 bg-[#030014] text-slate-200 font-sans">
      {/* Header welcome console */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
        <div>
          <h1 className="text-3xl font-extrabold text-white tracking-tight">
            Employee <span className="text-cyan-400">Portal</span>
          </h1>
          <p className="text-gray-400 text-sm mt-1">
            Access corporate guidance documents, check policy manuals, and submit clearance forms.
          </p>
        </div>
        <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-4 py-2 rounded-xl text-slate-200 font-medium text-xs">
          <Terminal className="h-4 w-4 text-cyan-500 shrink-0" />
          <span className="font-mono text-cyan-400">Employee Workspace Active</span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
        {/* Left pane: User Info & Request Form (1/3 width) */}
        <div className="space-y-6">
          {/* User Details Card */}
          <div className="bg-slate-900 border border-white/5 p-6 rounded-2xl relative overflow-hidden shadow-2xl">
            <div className="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-violet-600 to-cyan-500" />
            <div className="flex items-center gap-4 mb-4">
              {user?.photoURL ? (
                <img 
                  src={user.photoURL} 
                  alt={user?.displayName || 'User'} 
                  className="h-10 w-10 rounded-full object-cover border border-white/10"
                  referrerPolicy="no-referrer"
                />
              ) : (
                <div className="h-10 w-10 rounded-full bg-gradient-to-br from-violet-600 to-cyan-500 flex items-center justify-center font-bold text-sm text-white shrink-0 font-mono">
                  {user?.displayName?.substring(0, 2).toUpperCase() || 'EM'}
                </div>
              )}
              <div className="min-w-0">
                <span className="block font-bold text-slate-200 truncate">{user?.displayName}</span>
                <span className="block text-[10px] text-gray-500 truncate">{user?.email}</span>
              </div>
            </div>
            <div className="h-[1px] bg-white/5 w-full my-3"></div>
            <div className="space-y-2 text-[11px] font-mono">
              <div className="flex justify-between">
                <span className="text-gray-500">Department:</span>
                <span className="text-slate-300 font-bold">{user?.department || 'Not Assigned'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Designation:</span>
                <span className="text-slate-300 font-bold">{user?.designation || 'Staff Associate'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Security Clearance:</span>
                <span className="text-cyan-400 font-bold">{user?.role}</span>
              </div>
            </div>
          </div>

          {/* Form to submit request */}
          <form onSubmit={handleRequestSubmit} className="bg-slate-900 border border-slate-800 p-6.5 rounded-3xl relative overflow-hidden shadow-2xl space-y-4">
            <div className="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-cyan-500 to-violet-600" />
            <div>
              <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider font-mono">Workplace Clearance Request</h3>
              <p className="text-[11px] text-gray-500">Submit requests for manager approval.</p>
            </div>

            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1.5">Request Type</label>
              <select 
                value={requestType}
                onChange={(e) => setRequestType(e.target.value)}
                className="w-full px-3 py-2.5 bg-slate-950 border border-white/10 rounded-xl text-xs text-white focus:outline-none focus:border-cyan-500"
              >
                {requestTypes.map(type => <option key={type} value={type}>{type}</option>)}
              </select>
            </div>

            <div>
              <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1.5">Justification & Details</label>
              <textarea 
                rows={4}
                placeholder="Details of the request..."
                value={details}
                onChange={(e) => setDetails(e.target.value)}
                className="w-full px-3 py-2.5 bg-slate-950 border border-white/10 rounded-xl text-xs text-white focus:outline-none focus:border-cyan-500"
              />
            </div>

            <button 
              type="submit"
              disabled={isSubmittingRequest}
              className="w-full flex items-center justify-center gap-2 bg-gradient-to-r from-cyan-500 to-violet-600 hover:from-cyan-400 hover:to-violet-500 text-white py-2.5 rounded-xl text-xs font-semibold shadow-glow-violet transition-all duration-200"
            >
              {isSubmittingRequest ? (
                <>
                  <RefreshCw className="h-4 w-4 animate-spin" />
                  <span>Submitting request...</span>
                </>
              ) : (
                <>
                  <FileText className="h-4 w-4" />
                  <span>File Request</span>
                </>
              )}
            </button>
          </form>
        </div>

        {/* Right pane: Personal AI Assistant (2/3 width) */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-slate-900 border border-white/5 backdrop-blur-xl p-6.5 rounded-3xl relative overflow-hidden shadow-2xl min-h-[500px] flex flex-col justify-between">
            <div className="absolute top-0 left-0 w-full h-[1px] bg-gradient-to-r from-transparent via-cyan-500/20 to-transparent" />
            
            <div>
              <div className="flex justify-between items-center mb-6 border-b border-white/5 pb-4">
                <div>
                  <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider font-mono">NovaOS Personal AI Copilot</h3>
                  <p className="text-[11px] text-gray-500">Ask policy questions, check entitlements, or draft messages.</p>
                </div>
                <div className="flex items-center gap-2 bg-slate-950 border border-white/5 px-3 py-1.5 rounded-xl text-[10px] font-mono text-cyan-400">
                  <Brain className="h-3.5 w-3.5" />
                  <span>AI Core Online</span>
                </div>
              </div>

              {/* Chat Reply Area */}
              <div className="min-h-[250px] p-4 bg-slate-950/40 border border-white/5 rounded-2xl overflow-y-auto mb-4 text-xs leading-relaxed font-mono">
                {isThinking ? (
                  <div className="flex flex-col items-center justify-center gap-3 py-20">
                    <span className="h-6 w-6 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin"></span>
                    <span className="text-xs text-slate-500 font-mono">Querying corporate policies...</span>
                  </div>
                ) : chatReply ? (
                  <div className="text-slate-300 whitespace-pre-wrap select-text">
                    {chatReply}
                  </div>
                ) : (
                  <div className="text-center py-20 text-gray-600 font-mono">
                    Type a query in the prompt below to consult your personal AI.
                  </div>
                )}
              </div>
            </div>

            {/* Prompt input field */}
            <form onSubmit={handleChatSubmit} className="flex gap-2">
              <input 
                type="text" 
                placeholder="Ask policies, draft emails, or search internal data..."
                value={chatPrompt}
                onChange={(e) => setChatPrompt(e.target.value)}
                className="flex-1 px-4 py-3 bg-slate-950 border border-white/10 rounded-xl text-xs text-white focus:outline-none focus:border-cyan-500"
              />
              <button 
                type="submit"
                disabled={isThinking || !chatPrompt.trim()}
                className="p-3 bg-gradient-to-r from-cyan-500 to-violet-600 hover:from-cyan-400 hover:to-violet-500 text-white rounded-xl transition-all"
              >
                <Send className="h-4.5 w-4.5" />
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
};

export default EmployeeDashboard;
