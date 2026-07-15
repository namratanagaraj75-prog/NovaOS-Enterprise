import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Loader2, Sparkles } from 'lucide-react';

interface ProtectedRouteProps {
  children?: React.ReactNode;
  allowedRoles?: string[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles }) => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen w-screen bg-[#030014] flex flex-col items-center justify-center gap-4 relative overflow-hidden">
        {/* Glowing Background Orbs */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-80 h-80 bg-violet-600/10 rounded-full blur-3xl"></div>
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-cyan-600/5 rounded-full blur-3xl"></div>
        
        {/* Splash Loader visual */}
        <div className="glass-card p-8 rounded-2xl border border-white/5 flex flex-col items-center gap-4 text-center max-w-xs shadow-glow-violet relative">
          <div className="inline-flex p-3 bg-gradient-to-tr from-cyan-500 to-violet-600 rounded-xl shadow-glow-violet">
            <Sparkles className="h-6 w-6 text-white animate-pulse" />
          </div>
          <div>
            <h3 className="font-extrabold text-white tracking-tight">NovaOS Copilot</h3>
            <p className="text-[10px] text-gray-400 font-mono mt-1 uppercase tracking-widest">Resuming session...</p>
          </div>
          <Loader2 className="h-5 w-5 text-cyan-400 animate-spin mt-2" />
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to="/403" replace />;
  }

  return children ? <>{children}</> : <Outlet />;
};

export default ProtectedRoute;
