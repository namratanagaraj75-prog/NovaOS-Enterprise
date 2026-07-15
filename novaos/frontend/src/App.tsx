import React, { useState } from "react";
import {
  BrowserRouter as Router,
  Routes,
  Route,
  Navigate,
  Outlet,
  useNavigate,
} from "react-router-dom";
import { AppProvider, useAppContext } from "./context/AppContext";
import { NotificationProvider } from "./context/ToastContext";
import Sidebar from "./components/Sidebar";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import AICommandCenter from "./pages/HiringCommandCenter";
import HiringRequests from "./pages/HiringRequests";
import HiringRequestDetails from "./pages/HiringRequestDetails";
import RecruitmentPipeline from "./pages/RecruitmentPipeline";
import CandidateIntelligence from "./pages/CandidateIntelligence";
import WorkflowAutomation from "./pages/WorkflowAutomation";
import DecisionPassport from "./pages/DecisionPassport";
import Unauthorized from "./pages/Unauthorized";
import SuperAdminDashboard from "./pages/SuperAdminDashboard";
import HrAdminDashboard from "./pages/HrAdminDashboard";
import ManagerDashboard from "./pages/ManagerDashboard";
import LegalDashboard from "./pages/LegalDashboard";
import EmployeeDashboard from "./pages/EmployeeDashboard";
import FinanceDashboard from "./pages/FinanceDashboard";
import { AuthProvider, useAuth } from "./context/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";
import AppErrorBoundary from "./components/AppErrorBoundary";
import { Loader2, Sparkles, Bell, Search, Cpu, X } from "lucide-react";
import { motion } from "framer-motion";

// Role → Home Path mapping
const getRoleHomePath = (role: string) => {
  const r = (role || "").toUpperCase();
  if (r === "SUPER_ADMIN") return "/admin";
  if (r === "CEO") return "/dashboard";
  if (r === "HR_ADMIN") return "/hr";
  if (r === "HIRING_MANAGER") return "/manager";
  if (r === "LEGAL") return "/legal";
  if (r === "FINANCE") return "/finance";
  return "/employee";
};

const NotificationBell = () => {
  const { notifications, markNotificationRead } = useAppContext();
  const [isOpen, setIsOpen] = useState(false);
  const navigate = useNavigate();
  const unreadCount = notifications.filter((item) => !item.read).length;
  return (
    <div className="relative font-sans text-left">
      <button
        onClick={() => setIsOpen((open) => !open)}
        className="relative p-2.5 text-gray-400 hover:text-white bg-slate-900 border border-slate-800 rounded-xl"
        title="View system alerts"
      >
        <Bell className="h-4 w-4" />
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 h-2 w-2 bg-rose-500 rounded-full animate-pulse" />
        )}
      </button>
      {isOpen && (
        <div className="absolute right-0 mt-3 w-80 bg-slate-900 border border-white/5 rounded-2xl shadow-2xl p-4 z-50">
          <div className="flex justify-between pb-3 border-b border-white/5">
            <h4 className="font-bold text-xs text-slate-200 uppercase font-mono">
              System Alerts
            </h4>
            {unreadCount > 0 && (
              <button
                onClick={() => markNotificationRead()}
                className="text-[10px] text-cyan-400"
              >
                Mark all read
              </button>
            )}
          </div>
          <div className="max-h-64 overflow-y-auto space-y-2 mt-3">
            {notifications.length === 0 ? (
              <p className="text-center py-6 text-xs text-gray-500">
                No active notifications.
              </p>
            ) : (
              notifications.map((item) => (
                <motion.div
                   key={item.id}
                   initial={{ opacity: 0, y: -4 }}
                   animate={{ opacity: 1, y: 0 }}
                   onClick={() => {
                     markNotificationRead(item.id);
                     if (item.requestId) {
                       navigate(`/hiring-requests/${item.requestId}`);
                       setIsOpen(false);
                     }
                   }}
                   className={`p-3 rounded-xl border cursor-pointer ${item.read ? "bg-slate-950/40 border-white/5 opacity-60" : "bg-slate-950 border-violet-500/10"}`}
                >
                  <div className="flex justify-between gap-2">
                    <span className="font-bold text-xs text-slate-200">
                      {item.message}
                    </span>
                    <span className="text-[8px] text-gray-500">
                      {new Date(item.createdAt).toLocaleTimeString([], {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </span>
                  </div>
                  {item.sub && (
                    <p className="text-[10px] text-gray-400 mt-1">{item.sub}</p>
                  )}
                </motion.div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};

// Layout wrapper for pages requiring navigation sidebar
const DashboardLayout = () => {
  const { logout, user } = useAuth();
  const { backendOnline } = useAppContext();
  const [searchQuery, setSearchQuery] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);

  return (
    <div className="flex h-screen bg-[#080613] overflow-hidden">
      {/* Persistent Sidebar Ã¢â‚¬â€ never disappears */}
      <Sidebar onLogout={logout} />

      {/* Main content area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Fixed Top Bar */}
        <header className="h-14 bg-[#080613]/90 backdrop-blur-xl border-b border-white/5 flex items-center justify-between px-6 shrink-0 sticky top-0 z-20">
          {/* Left: Search */}
          <div className="flex items-center gap-3 flex-1 max-w-md">
            <div
              className={`flex items-center gap-2 rounded-xl border transition-all duration-200 px-3 py-2 ${
                searchOpen
                  ? "bg-[#121426] border-[#6D5DF6]/40 w-64"
                  : "bg-[#121426] border-white/5 w-48 hover:border-white/10"
              }`}
            >
              <Search className="h-3.5 w-3.5 text-gray-500 shrink-0" />
              <input
                type="text"
                placeholder="Search NovaOS..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onFocus={() => setSearchOpen(true)}
                onBlur={() => setSearchOpen(false)}
                className="bg-transparent text-xs text-white placeholder-gray-500 focus:outline-none flex-1 min-w-0"
              />
              {searchQuery && (
                <button onClick={() => setSearchQuery("")}>
                  <X className="h-3 w-3 text-gray-500 hover:text-white" />
                </button>
              )}
            </div>
          </div>

          {/* Center: Backend status */}
          <div
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border ${backendOnline ? "bg-[#00E676]/5 border-[#00E676]/10" : "bg-rose-500/5 border-rose-500/10"}`}
          >
            <Cpu
              className={`h-3 w-3 animate-pulse shrink-0 ${backendOnline ? "text-[#00E676]" : "text-rose-400"}`}
            />
            <span
              className={`text-[10px] font-mono font-bold tracking-wider ${backendOnline ? "text-[#00E676]" : "text-rose-400"}`}
            >
              {backendOnline ? "GEMINI ENGINE ONLINE" : "OFFLINE SIMULATION"}
            </span>
          </div>

          {/* Right: Notifications + Profile */}
          <div className="flex items-center gap-3 flex-1 justify-end">
            <NotificationBell />

            {/* Profile pill */}
            <div className="flex items-center gap-2 bg-[#121426] border border-white/5 px-3 py-1.5 rounded-xl">
              {user?.photoURL ? (
                <img
                  src={user.photoURL}
                  alt=""
                  className="h-6 w-6 rounded-full object-cover border border-white/10"
                  referrerPolicy="no-referrer"
                />
              ) : (
                <div className="h-6 w-6 rounded-full bg-gradient-to-br from-[#6D5DF6] to-[#00C2FF] flex items-center justify-center text-[9px] font-bold text-white">
                  {(user?.displayName || "NA").substring(0, 2).toUpperCase()}
                </div>
              )}
              <div className="hidden sm:block">
                <span className="text-[11px] font-semibold text-white leading-none block">
                  {user?.displayName || "User"}
                </span>
                <span className="text-[9px] text-[#6D5DF6] font-mono uppercase tracking-wider font-bold">
                  {(user?.role || "EMPLOYEE").replace("_", " ")}
                </span>
              </div>
            </div>
          </div>
        </header>

        {/* Scrollable Page Content */}
        <main className="flex-1 overflow-y-auto">
          <div className="p-6 max-w-screen-2xl mx-auto">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

// Route wrapper for public routes to redirect authenticated users
const PublicRoute = ({ children }: { children: React.ReactNode }) => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen w-screen bg-[#030014] flex flex-col items-center justify-center gap-4 relative overflow-hidden">
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-80 h-80 bg-violet-600/10 rounded-full blur-3xl"></div>
        <div className="glass-card p-8 rounded-2xl border border-white/5 flex flex-col items-center gap-4 text-center max-w-xs shadow-glow-violet relative">
          <div className="inline-flex p-3 bg-gradient-to-tr from-cyan-500 to-violet-600 rounded-xl shadow-glow-violet">
            <Sparkles className="h-6 w-6 text-white animate-pulse" />
          </div>
          <div>
            <h3 className="font-extrabold text-white tracking-tight">
              NovaOS Copilot
            </h3>
            <p className="text-[10px] text-gray-400 font-mono mt-1 uppercase tracking-widest">
              Starting engine...
            </p>
          </div>
          <Loader2 className="h-5 w-5 text-cyan-400 animate-spin mt-2" />
        </div>
      </div>
    );
  }

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};

function AppContent() {
  return (
    <Routes>
      {/* Public Login Route */}
      <Route
        path="/"
        element={
          <PublicRoute>
            <Login />
          </PublicRoute>
        }
      />
      <Route
        path="/login"
        element={
          <PublicRoute>
            <Login initialView="login" />
          </PublicRoute>
        }
      />

      {/* Private Dashboard Routing Layout - Accessible to all authorized roles */}
      <Route
        element={
          <ProtectedRoute>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/chat" element={<AICommandCenter />} />
        <Route path="/hiring-requests" element={<HiringRequests />} />

        {/* Role-specific dashboard fallback routes */}
        <Route path="/employee" element={<EmployeeDashboard />} />
      </Route>

      {/* Admin Panel (Super Admin only) */}
      <Route
        element={
          <ProtectedRoute allowedRoles={["SUPER_ADMIN"]}>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/admin" element={<SuperAdminDashboard />} />
      </Route>

      {/* Recruitment Pipeline Portal (Super Admin, HR Admin & Manager) */}
      <Route
        element={
          <ProtectedRoute
            allowedRoles={["SUPER_ADMIN", "CEO", "HR_ADMIN", "HIRING_MANAGER"]}
          >
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/recruitment" element={<RecruitmentPipeline />} />
      </Route>

      {/* Governed hiring Decision Passport */}
      <Route
        element={
          <ProtectedRoute
            allowedRoles={[
              "SUPER_ADMIN",
              "CEO",
              "HR_ADMIN",
              "HIRING_MANAGER",
              "LEGAL",
              "FINANCE",
            ]}
          >
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/passports/:requestId" element={<DecisionPassport />} />
        <Route path="/hiring-requests/:id" element={<HiringRequestDetails />} />
      </Route>

      {/* HR Portal (Super Admin & HR Admin only) */}
      <Route
        element={
          <ProtectedRoute allowedRoles={["SUPER_ADMIN", "CEO", "HR_ADMIN"]}>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/hr" element={<HrAdminDashboard />} />
        <Route path="/intelligence" element={<CandidateIntelligence />} />
      </Route>

      {/* Manager approvals (Super Admin, HR Admin, Manager only) */}
      <Route
        element={
          <ProtectedRoute
            allowedRoles={["SUPER_ADMIN", "CEO", "HR_ADMIN", "HIRING_MANAGER"]}
          >
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/manager" element={<ManagerDashboard />} />
        <Route path="/workflow" element={<WorkflowAutomation />} />
      </Route>

      {/* Legal Team clearance (Super Admin, Legal only) */}
      <Route
        element={
          <ProtectedRoute allowedRoles={["SUPER_ADMIN", "CEO", "LEGAL"]}>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/legal" element={<LegalDashboard />} />
      </Route>

      {/* Finance Portal (Super Admin, Finance only) */}
      <Route
        element={
          <ProtectedRoute allowedRoles={["SUPER_ADMIN", "CEO", "FINANCE"]}>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/finance" element={<FinanceDashboard />} />
      </Route>

      {/* 403 Forbidden Page */}
      <Route path="/403" element={<Unauthorized />} />

      {/* Fallback Route */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function App() {
  React.useEffect(() => {
    console.log("App Started");
  }, []);

  return (
    <AuthProvider>
      <AppErrorBoundary>
        <AppProvider>
          <NotificationProvider>
            <Router>
              <AppContent />
            </Router>
          </NotificationProvider>
        </AppProvider>
      </AppErrorBoundary>
    </AuthProvider>
  );
}

export default App;
