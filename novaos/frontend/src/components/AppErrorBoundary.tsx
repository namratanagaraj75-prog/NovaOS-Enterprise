import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface State { error: Error | null }

export class AppErrorBoundary extends React.Component<{ children: React.ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('NovaOS render failure:', error, info);
  }

  private recover = () => {
    localStorage.removeItem('novaos_state');
    window.location.assign('/dashboard');
  };

  render() {
    if (!this.state.error) return this.props.children;
    return <div className="min-h-screen bg-[#080613] text-slate-200 grid place-items-center p-6">
      <div className="w-full max-w-lg bg-slate-900 border border-rose-500/20 rounded-2xl p-6 shadow-2xl">
        <AlertTriangle className="h-7 w-7 text-rose-400" />
        <h1 className="text-xl font-bold text-white mt-4">NovaOS could not render this page</h1>
        <p className="text-sm text-slate-400 mt-2">A saved application record is incompatible with the current workspace. Resetting the local app state will preserve your login.</p>
        <pre className="mt-4 p-3 bg-slate-950 border border-slate-800 rounded-xl text-[11px] text-rose-300 overflow-auto">{this.state.error.message}</pre>
        <button onClick={this.recover} className="mt-5 flex items-center gap-2 bg-cyan-500 text-slate-950 px-4 py-2.5 rounded-xl text-xs font-bold">
          <RefreshCw className="h-4 w-4" /> Reset application state
        </button>
      </div>
    </div>;
  }
}
export default AppErrorBoundary;

