import React from "react";
import { NavLink, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  Terminal,
  GitBranch,
  BrainCircuit,
  GitFork,
  LogOut,
  Sparkles,
  Shield,
  Users,
  FileText,
  Briefcase,
  Scale,
  DollarSign,
  ChevronRight,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { useAppContext } from "../context/AppContext";

interface SidebarProps {
  onLogout?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ onLogout }) => {
  const navigate = useNavigate();
  const { logout, user } = useAuth();
  const { backendOnline, hiringRequests } = useAppContext();

  const userRole = (user?.role || "EMPLOYEE").toUpperCase();

  const getUnreadBadge = (itemName: string) => {
    if (!user || !hiringRequests) return 0;
    const uid = user.uid;
    const role = user.role;

    if (itemName === "Hiring Requests" || itemName === "HR Portal") {
      if (role !== "HR_ADMIN" && role !== "SUPER_ADMIN" && role !== "CEO") return 0;
      return hiringRequests.filter(r => !r.readBy || !r.readBy.includes(uid)).length;
    }

    if (itemName === "Manager View") {
      if (role !== "HIRING_MANAGER" && role !== "SUPER_ADMIN" && role !== "CEO") return 0;
      return hiringRequests.filter(r => r.currentApproverRole === "HIRING_MANAGER" && (!r.readBy || !r.readBy.includes(uid))).length;
    }

    if (itemName === "Legal") {
      if (role !== "LEGAL" && role !== "SUPER_ADMIN" && role !== "CEO") return 0;
      return hiringRequests.filter(r => r.currentApproverRole === "LEGAL" && (!r.readBy || !r.readBy.includes(uid))).length;
    }

    if (itemName === "Finance") {
      if (role !== "FINANCE" && role !== "SUPER_ADMIN" && role !== "CEO") return 0;
      return hiringRequests.filter(r => r.currentApproverRole === "FINANCE" && (!r.readBy || !r.readBy.includes(uid))).length;
    }

    return 0;
  };

  const menuItems = [
    {
      section: "WORKSPACE",
      items: [
        {
          name: "Dashboard",
          path: "/dashboard",
          icon: LayoutDashboard,
          roles: [
            "SUPER_ADMIN",
            "CEO",
            "HR_ADMIN",
            "HIRING_MANAGER",
            "LEGAL",
            "FINANCE",
          ],
        },
        {
          name: "AI Command Center",
          path: "/chat",
          icon: Terminal,
          roles: [
            "SUPER_ADMIN",
            "CEO",
            "HR_ADMIN",
            "HIRING_MANAGER",
            "LEGAL",
            "FINANCE",
          ],
          badge: "AI",
        },
      ],
    },
    {
      section: "PEOPLE & TALENT",
      items: [
        {
          name: "Recruitment",
          path: "/recruitment",
          icon: GitBranch,
          roles: ["SUPER_ADMIN", "CEO", "HR_ADMIN", "HIRING_MANAGER"],
        },
        {
          name: "Candidate AI",
          path: "/intelligence",
          icon: BrainCircuit,
          roles: ["SUPER_ADMIN", "CEO", "HR_ADMIN"],
          badge: "NEW",
        },
        {
          name: "HR Portal",
          path: "/hr",
          icon: Users,
          roles: ["SUPER_ADMIN", "CEO", "HR_ADMIN"],
        },
        {
          name: "Hiring Requests",
          path: "/hiring-requests",
          icon: FileText,
          roles: [
            "SUPER_ADMIN",
            "CEO",
            "HR_ADMIN",
            "HIRING_MANAGER",
            "FINANCE",
            "LEGAL",
          ],
        },
      ],
    },
    {
      section: "OPERATIONS",
      items: [
        {
          name: "Workflow",
          path: "/workflow",
          icon: GitFork,
          roles: ["SUPER_ADMIN", "CEO", "HR_ADMIN", "HIRING_MANAGER"],
        },
        {
          name: "Manager View",
          path: "/manager",
          icon: Briefcase,
          roles: ["SUPER_ADMIN", "CEO", "HR_ADMIN", "HIRING_MANAGER"],
        },
        {
          name: "Legal",
          path: "/legal",
          icon: Scale,
          roles: ["SUPER_ADMIN", "CEO", "LEGAL"],
        },
        {
          name: "Finance",
          path: "/finance",
          icon: DollarSign,
          roles: ["SUPER_ADMIN", "CEO", "FINANCE"],
        },
      ],
    },
    {
      section: "SYSTEM",
      items: [
        {
          name: "Admin Console",
          path: "/admin",
          icon: Shield,
          roles: ["SUPER_ADMIN"],
        },
      ],
    },
  ];

  const handleLogoutClick = async () => {
    if (onLogout) {
      onLogout();
    } else {
      await logout();
      navigate("/");
    }
  };

  const userEmail = user?.email || "admin@nova-os.ai";
  const userDisplayName = user?.displayName || "NovaOS User";
  const roleLabel = userRole.replace("_", " ");

  return (
    <aside className="w-64 bg-[#080613] border-r border-white/5 h-screen flex flex-col sticky top-0 overflow-y-auto animate-slide-in-left shrink-0">
      {/* Brand */}
      <div className="flex items-center gap-3 px-5 py-5 border-b border-white/5">
        <div className="p-2 bg-gradient-to-tr from-[#6D5DF6] to-[#00C2FF] rounded-xl shadow-glow-violet shrink-0">
          <Sparkles className="h-5 w-5 text-white" />
        </div>
        <div>
          <h1 className="font-black text-lg tracking-tight text-white leading-none">
            Nova<span className="text-[#00C2FF]">OS</span>
          </h1>
          <span className="text-[9px] text-gray-500 tracking-widest uppercase font-mono">
            Enterprise Suite
          </span>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-6 overflow-y-auto">
        {menuItems.map((section) => {
          const filtered = section.items.filter((item) =>
            item.roles.includes(userRole),
          );
          if (filtered.length === 0) return null;

          return (
            <div key={section.section}>
              <p className="text-[9px] font-bold text-gray-600 tracking-widest uppercase px-3 mb-2 font-mono">
                {section.section}
              </p>
              <div className="space-y-0.5">
                {filtered.map((item) => (
                  <NavLink
                    key={item.name}
                    to={item.path}
                    className={({ isActive }) => `
                      flex items-center justify-between px-3 py-2.5 rounded-xl font-medium text-sm transition-all duration-150 group
                      ${
                        isActive
                          ? "bg-[#6D5DF6]/15 text-white border border-[#6D5DF6]/30"
                          : "text-gray-400 hover:text-white hover:bg-white/5 border border-transparent"
                      }
                    `}
                  >
                    <div className="flex items-center gap-3">
                      <item.icon className="h-4 w-4 shrink-0 transition-transform duration-200 group-hover:scale-110" />
                      <span className="text-[13px]">{item.name}</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      {"badge" in item && item.badge && (
                        <span className="text-[8px] font-black tracking-widest bg-[#6D5DF6]/20 text-[#6D5DF6] border border-[#6D5DF6]/30 px-1.5 py-0.5 rounded font-mono">
                          {item.badge}
                        </span>
                      )}
                      {getUnreadBadge(item.name) > 0 && (
                        <span className="flex h-5 min-w-[20px] items-center justify-center rounded-full bg-rose-500 px-1.5 text-[9px] font-bold text-white shadow-glow-rose font-mono">
                          {getUnreadBadge(item.name)}
                        </span>
                      )}
                      <ChevronRight className="h-3.5 w-3.5 opacity-0 group-hover:opacity-40 transition-opacity" />
                    </div>
                  </NavLink>
                ))}
              </div>
            </div>
          );
        })}
      </nav>

      {/* User Footer */}
      <div className="border-t border-white/5 p-4">
        {/* Status indicator */}
        <div className="flex items-center gap-2 px-1 mb-3">
          <span
            className={`h-1.5 w-1.5 rounded-full animate-pulse shrink-0 ${backendOnline ? "bg-[#00E676]" : "bg-rose-500"}`}
          ></span>
          <span className="text-[9px] text-gray-500 font-mono uppercase tracking-widest">
            {backendOnline ? "Connected" : "Offline Simulation"}
          </span>
        </div>

        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5 overflow-hidden">
            {user?.photoURL ? (
              <img
                src={user.photoURL}
                alt={userDisplayName}
                className="h-8 w-8 rounded-full object-cover shrink-0 border border-white/10"
                referrerPolicy="no-referrer"
              />
            ) : (
              <div className="h-8 w-8 rounded-full bg-gradient-to-br from-[#6D5DF6] to-[#00C2FF] flex items-center justify-center font-bold text-xs text-white shrink-0">
                {userDisplayName.substring(0, 2).toUpperCase()}
              </div>
            )}
            <div className="flex flex-col min-w-0">
              <span className="text-xs font-semibold text-white truncate leading-tight">
                {userDisplayName}
              </span>
              <span className="text-[9px] text-[#6D5DF6] font-bold font-mono uppercase tracking-wide">
                {roleLabel}
              </span>
            </div>
          </div>
          <button
            onClick={handleLogoutClick}
            className="p-1.5 hover:bg-red-500/10 rounded-lg text-gray-500 hover:text-red-400 transition-colors duration-200 shrink-0"
            title="Log Out"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </aside>
  );
};
export default Sidebar;
