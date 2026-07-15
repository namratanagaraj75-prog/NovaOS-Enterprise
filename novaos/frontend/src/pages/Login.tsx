import React, { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { motion, AnimatePresence } from "framer-motion";
import {
  Sparkles,
  ShieldAlert,
  Chrome,
  Loader2,
  Mail,
  Lock,
  Eye,
  EyeOff,
  Bot,
  FileText,
  ThumbsUp,
  GitFork,
  BarChart3,
  KeyRound,
  X,
  Users,
  ShieldCheck,
  CheckSquare,
  LockKeyhole,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { sendPasswordResetEmail } from "firebase/auth";
import { auth } from "../lib/firebase";
import { useToast } from "../context/ToastContext";

interface LoginProps {
  initialView?: "login";
}

export const Login: React.FC<LoginProps> = ({ initialView = "login" }) => {
  const { loginWithGoogle, login, authError, clearAuthError } = useAuth();
  const { showToast } = useToast();

  const [viewState, setViewState] = useState<"login" | "forgot">(initialView);
  const [isGoogleSigningIn, setIsGoogleSigningIn] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  // Forgot Password inputs
  const [resetEmail, setResetEmail] = useState("");
  const [isResetting, setIsResetting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm();

  // Progress loader texts
  const [loadingStep, setLoadingStep] = useState(0);
  const loadingStatements = [
    "Authenticating with Google...",
    "Verifying enterprise account...",
    "Checking Firestore permissions...",
    "Loading workspace...",
    "Initializing NovaOS...",
  ];

  const isLoading = isGoogleSigningIn || isSubmitting;

  useEffect(() => {
    clearAuthError();
    return () => clearAuthError();
  }, [viewState]);

  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (isLoading) {
      interval = setInterval(() => {
        setLoadingStep((prev) => (prev + 1) % loadingStatements.length);
      }, 1400);
    } else {
      setLoadingStep(0);
    }
    return () => clearInterval(interval);
  }, [isLoading]);

  const handleGoogleSignIn = async () => {
    setIsGoogleSigningIn(true);
    try {
      await loginWithGoogle();
    } catch (err) {
      // Handled in context
    } finally {
      setIsGoogleSigningIn(false);
    }
  };

  const onSubmitLogin = async (data: any) => {
    try {
      await login(data.email, data.password);
    } catch (err) {
      // Handled in context
    }
  };

  const handlePasswordReset = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resetEmail) {
      showToast("Email address is required.", "error");
      return;
    }
    setIsResetting(true);
    try {
      await sendPasswordResetEmail(auth, resetEmail);
      showToast("Password reset link sent to your email.", "success");
      setViewState("login");
      setResetEmail("");
    } catch (error: any) {
      showToast(error.message || "Failed to send reset link.", "error");
    } finally {
      setIsResetting(false);
    }
  };
  return (
    <div className="min-h-screen w-screen bg-[#020208] text-slate-100 flex flex-col lg:flex-row overflow-hidden relative font-sans">
      {/* Background glowing gradients */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#ffffff01_1px,transparent_1px),linear-gradient(to_bottom,#ffffff01_1px,transparent_1px)] bg-[size:32px_32px] pointer-events-none" />
      <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] bg-blue-600/5 rounded-full blur-3xl animate-pulse-slow pointer-events-none" />
      <div className="absolute bottom-1/4 right-1/4 w-[600px] h-[600px] bg-purple-600/5 rounded-full blur-3xl animate-pulse-slow pointer-events-none" />

      {/* --- LEFT PANEL --- */}
      <div className="w-full lg:w-1/2 flex flex-col justify-between p-8 lg:p-14 relative z-10 h-screen overflow-hidden select-none">
        {/* Top Branding Section */}
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-[#121b3a]/60 border border-blue-500/20 rounded-xl shadow-lg">
            <Sparkles className="h-6 w-6 text-blue-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-extrabold text-2xl tracking-tight text-white leading-none">
                NovaOS
              </h1>
            </div>
          </div>
        </div>

        {/* Hero Section & Projection Hologram */}
        <div className="my-auto space-y-6 py-4">
          {/* Outlined Pill Caption */}
          <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-blue-500/5 border border-blue-500/20 rounded-full text-[10px] font-medium text-blue-400/90 font-mono tracking-wide uppercase">
            <ShieldCheck className="h-3.5 w-3.5" />
            <span>Enterprise AI Employee Copilot</span>
          </div>

          {/* Heading */}
          <div className="space-y-3">
            <h2 className="text-4xl lg:text-[54px] font-black tracking-tight leading-none text-white font-sans">
              AI-Powered.
              <br />
              <span className="bg-gradient-to-r from-cyan-400 to-violet-500 bg-clip-text text-transparent">
                People-Focused.
              </span>
              <br />
              Enterprise-Ready.
            </h2>
            <p className="text-slate-400 text-xs lg:text-sm max-w-md leading-relaxed">
              Streamline operations, automate approvals, and empower every
              employee with intelligent AI assistance.
            </p>
          </div>

          {/* Hologram Projections Animation (matches screenshot layout) */}
          <div className="h-48 w-full relative flex items-center justify-center pt-4">
            {/* Projection Base (Rings on the Floor) */}
            <div className="absolute bottom-2 w-52 h-14 bg-blue-500/5 border-2 border-blue-500/20 rounded-full scale-y-[0.3] shadow-[0_0_40px_rgba(59,130,246,0.3)] flex items-center justify-center">
              <div className="w-40 h-10 border border-cyan-400/30 rounded-full" />
              <div className="w-24 h-6 border border-purple-500/20 rounded-full" />
            </div>

            {/* Glowing beam extending upwards */}
            <div
              className="absolute bottom-6 w-36 h-40 bg-gradient-to-t from-blue-500/20 via-cyan-500/5 to-transparent rounded-full blur-md"
              style={{
                clipPath: "polygon(15% 0%, 85% 0%, 100% 100%, 0% 100%)",
              }}
            />

            {/* Floating central checklist card */}
            <motion.div
              animate={{ y: [0, -8, 0] }}
              transition={{ repeat: Infinity, duration: 4, ease: "easeInOut" }}
              className="absolute bottom-10 p-3 bg-[#0d122b]/80 border border-blue-500/30 rounded-xl shadow-[0_0_20px_rgba(59,130,246,0.25)] flex flex-col gap-2 w-32 relative z-20"
            >
              <div className="flex items-center gap-1.5 border-b border-white/5 pb-1">
                <Sparkles className="h-3 w-3 text-cyan-400 animate-pulse" />
                <span className="text-[9px] font-bold font-mono text-slate-200">
                  AI AGENT
                </span>
              </div>
              <div className="space-y-1.5">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="flex items-center gap-1.5">
                    <CheckSquare className="h-2.5 w-2.5 text-cyan-400" />
                    <div className="h-1.5 bg-slate-800 rounded w-full border border-white/5" />
                  </div>
                ))}
              </div>
            </motion.div>

            {/* Orbiting Icons */}
            <motion.div
              animate={{ y: [0, -5, 0] }}
              transition={{
                repeat: Infinity,
                duration: 3,
                ease: "easeInOut",
                delay: 0.5,
              }}
              className="absolute w-60 h-36 z-30 pointer-events-none"
            >
              <div className="absolute top-1/2 left-4 -translate-y-1/2 p-2 bg-[#090d22]/90 border border-white/10 rounded-lg text-blue-400 shadow-md">
                <BarChart3 className="h-4 w-4" />
              </div>
              <div className="absolute top-4 left-1/3 p-2 bg-[#090d22]/90 border border-white/10 rounded-lg text-purple-400 shadow-md">
                <ShieldCheck className="h-4 w-4" />
              </div>
              <div className="absolute top-1/4 right-1/4 p-2 bg-[#090d22]/90 border border-white/10 rounded-lg text-indigo-400 shadow-md">
                <Users className="h-4 w-4" />
              </div>
              <div className="absolute top-1/2 right-4 -translate-y-1/2 p-2 bg-[#090d22]/90 border border-white/10 rounded-lg text-emerald-400 shadow-md">
                <FileText className="h-4 w-4" />
              </div>
              <div className="absolute bottom-4 right-1/3 p-2 bg-[#090d22]/90 border border-white/10 rounded-lg text-rose-400 shadow-md">
                <Bot className="h-4 w-4" />
              </div>
            </motion.div>
          </div>

          {/* Bottom 5 Features Row (exactly as screenshot layout) */}
          <div className="grid grid-cols-5 gap-2.5 pt-2">
            {[
              { icon: Users, label: "HR Automation" },
              { icon: FileText, label: "AI Legal Assistant" },
              { icon: ThumbsUp, label: "Workflow Approvals" },
              { icon: BarChart3, label: "Smart Analytics" },
              { icon: ShieldCheck, label: "Enterprise Security" },
            ].map((item, idx) => (
              <div
                key={idx}
                className="bg-[#090d22]/40 border border-white/5 p-3 rounded-xl flex flex-col items-center justify-center text-center gap-2 hover:border-blue-500/20 transition-all group"
              >
                <item.icon className="h-4 w-4 text-blue-400 group-hover:scale-110 transition-transform" />
                <span className="text-[9px] font-bold text-slate-350 tracking-tight leading-tight block">
                  {item.label}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Security badge at bottom left */}
        <div className="bg-[#090d22]/40 border border-white/5 p-3.5 rounded-xl flex items-center gap-3.5 mt-4">
          <div className="p-2.5 bg-[#121b3a]/60 border border-blue-500/25 rounded-lg text-blue-400 shrink-0">
            <LockKeyhole className="h-4 w-4 text-purple-400" />
          </div>
          <div className="text-[10px] leading-relaxed">
            <h4 className="font-bold text-purple-400">
              Secure Enterprise Authentication
            </h4>
            <p className="text-gray-400">
              Protected with Firebase Authentication & Role-Based Access Control
            </p>
          </div>
        </div>
      </div>

      {/* --- RIGHT PANEL --- */}
      <div className="w-full lg:w-1/2 flex flex-col justify-center items-center p-6 lg:p-12 relative z-10 h-screen min-h-screen">
        {/* Main Floating Authentication Card */}
        <div className="w-full max-w-[480px] bg-[#070913]/60 border border-white/5 backdrop-blur-2xl p-9 rounded-[24px] shadow-2xl relative overflow-hidden flex flex-col justify-between">
          <div className="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-blue-500 via-indigo-500 to-purple-600"></div>

          <AnimatePresence mode="wait">
            {/* 1. Login View */}
            {viewState === "login" && (
              <motion.div
                key="login"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="space-y-6"
              >
                {/* Titles */}
                <div className="text-center">
                  <h2 className="text-3xl font-extrabold text-white tracking-tight">
                    Welcome back
                  </h2>
                  <p className="text-xs text-slate-400 mt-1.5">
                    Sign in to continue to NovaOS Enterprise Suite.
                  </p>
                </div>

                {/* Form Level Error warnings */}
                <AnimatePresence>
                  {authError && (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: "auto" }}
                      exit={{ opacity: 0, height: 0 }}
                      className="bg-rose-500/10 border border-rose-500/20 text-rose-300 p-3.5 rounded-xl text-xs flex items-start gap-2.5 leading-relaxed overflow-hidden"
                    >
                      <ShieldAlert className="h-4.5 w-4.5 text-rose-400 shrink-0 mt-0.5" />
                      <span>{authError}</span>
                    </motion.div>
                  )}
                </AnimatePresence>

                {/* Google Sign-in */}
                <button
                  onClick={handleGoogleSignIn}
                  disabled={isLoading}
                  className="w-full flex items-center justify-center gap-3 py-2.5 px-4 bg-white text-slate-900 hover:bg-slate-50 font-bold rounded-xl transition-all duration-200 text-xs shadow-md border border-slate-200"
                >
                  <svg
                    viewBox="0 0 24 24"
                    className="h-5 w-5 shrink-0"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                      fill="#4285F4"
                    />
                    <path
                      d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                      fill="#34A853"
                    />
                    <path
                      d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                      fill="#FBBC05"
                    />
                    <path
                      d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                      fill="#EA4335"
                    />
                  </svg>
                  <span>Continue with Google</span>
                </button>

                {/* OR divider */}
                <div className="flex items-center gap-4 my-2 text-[10px] text-gray-500 font-mono tracking-widest uppercase justify-center">
                  <div className="h-[1px] bg-white/5 w-1/3" />
                  <span>OR</span>
                  <div className="h-[1px] bg-white/5 w-1/3" />
                </div>

                {/* Credentials Form */}
                <form
                  onSubmit={handleSubmit(onSubmitLogin)}
                  className="space-y-4"
                >
                  {/* Email */}
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                      Corporate Email
                    </label>
                    <div className="relative">
                      <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 pointer-events-none text-gray-500">
                        <Mail className="h-4.5 w-4.5" />
                      </span>
                      <input
                        type="email"
                        placeholder="you@company.com"
                        className="w-full pl-10 pr-4 py-2.5 bg-[#090d22]/80 border border-white/10 rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-indigo-500 transition-all font-sans"
                        {...register("email", {
                          required: "Corporate email is required",
                          pattern: {
                            value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                            message: "Invalid email address formatting",
                          },
                        })}
                      />
                    </div>
                    {errors.email && (
                      <span className="text-[10px] text-red-400 mt-1 block font-mono">
                        {errors.email.message as string}
                      </span>
                    )}
                  </div>

                  {/* Password */}
                  <div>
                    <div className="flex justify-between items-center mb-1.5">
                      <label className="block text-xs font-semibold text-slate-300">
                        Password
                      </label>
                      <button
                        type="button"
                        onClick={() => setViewState("forgot")}
                        className="text-xs text-blue-500 hover:text-blue-400 font-medium"
                      >
                        Forgot Password?
                      </button>
                    </div>
                    <div className="relative">
                      <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 pointer-events-none text-gray-500">
                        <Lock className="h-4.5 w-4.5" />
                      </span>
                      <input
                        type={showPassword ? "text" : "password"}
                        placeholder="Enter your password"
                        className="w-full pl-10 pr-10 py-2.5 bg-[#090d22]/80 border border-white/10 rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-indigo-500 transition-all font-sans"
                        {...register("password", {
                          required: "Password is required",
                        })}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-gray-500 hover:text-white"
                      >
                        {showPassword ? (
                          <EyeOff className="h-4 w-4" />
                        ) : (
                          <Eye className="h-4 w-4" />
                        )}
                      </button>
                    </div>
                    {errors.password && (
                      <span className="text-[10px] text-red-400 mt-1 block font-mono">
                        {errors.password.message as string}
                      </span>
                    )}
                  </div>

                  {/* Remember Me */}
                  <div className="flex items-center text-xs text-gray-400">
                    <label className="flex items-center gap-2 cursor-pointer select-none">
                      <input
                        type="checkbox"
                        className="rounded bg-slate-950 border-white/10 text-indigo-500 focus:ring-0 focus:ring-offset-0 h-3.5 w-3.5"
                      />
                      <span>Remember this machine</span>
                    </label>
                  </div>

                  {/* Submit Button */}
                  <button
                    type="submit"
                    disabled={isLoading}
                    className="w-full flex items-center justify-center gap-2 py-3 bg-gradient-to-r from-blue-500 via-indigo-600 to-purple-600 hover:from-blue-400 hover:to-purple-500 text-white font-bold rounded-xl transition-all duration-200 text-xs shadow-glow-violet mt-3"
                  >
                    <span>Sign In →</span>
                  </button>
                </form>
              </motion.div>
            )}
            {/* Forgot Password View */}
            {viewState === "forgot" && (
              <motion.div
                key="forgot"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="space-y-4"
              >
                <div>
                  <h3 className="text-sm font-bold text-slate-200 uppercase tracking-wider font-mono">
                    Reset Password
                  </h3>
                  <p className="text-[10px] text-gray-500">
                    Receive a Firebase password reset link.
                  </p>
                </div>

                <form
                  onSubmit={handlePasswordReset}
                  className="space-y-4 font-sans text-xs"
                >
                  <input
                    type="email"
                    required
                    placeholder="you@company.com"
                    value={resetEmail}
                    onChange={(e) => setResetEmail(e.target.value)}
                    className="w-full px-3.5 py-2.5 bg-[#090d22]/80 border border-white/10 rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-indigo-500"
                  />

                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={() => setViewState("login")}
                      className="flex-1 py-2 bg-white/5 border border-white/10 rounded-xl font-bold text-white hover:bg-white/10"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={isResetting}
                      className="flex-1 py-2 bg-gradient-to-r from-blue-500 to-indigo-600 hover:from-blue-400 hover:to-indigo-500 text-white rounded-xl font-bold flex items-center justify-center gap-1.5"
                    >
                      {isResetting ? (
                        <>
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          <span>Issuing link...</span>
                        </>
                      ) : (
                        <span>Send Link</span>
                      )}
                    </button>
                  </div>
                </form>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Footer brand badges (matches screenshot layout) */}
        <div className="mt-8 space-y-3.5 text-center select-none">
          <span className="text-[10px] text-slate-500 font-mono tracking-widest uppercase block">
            Powered by
          </span>
          <div className="flex flex-wrap items-center justify-center gap-5 text-[10px] text-slate-400 font-mono">
            {/* Firestore */}
            <div className="flex items-center gap-1.5">
              <svg
                viewBox="0 0 32 32"
                className="h-4 w-4 shrink-0"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M6.012 26.27l2.802-17.604 7.625 7.609-10.427 10.0z"
                  fill="#FFC107"
                />
                <path
                  d="M25.992 26.27l-4.217-18.06-2.225 2.22 6.442 15.84z"
                  fill="#F4B400"
                />
                <path
                  d="M16.438 4.015l-3.805 7.755 3.805 3.795z"
                  fill="#FFA000"
                />
                <path
                  d="M16.438 31.005c5.362 0 9.708-4.27 9.708-9.54s-4.346-9.54-9.708-9.54-9.708 4.27-9.708 9.54 4.346 9.54 9.708 9.54z"
                  fill="#DD2C00"
                />
              </svg>
              <span>Firestore</span>
            </div>

            {/* Firebase */}
            <div className="flex items-center gap-1.5">
              <svg
                viewBox="0 0 32 32"
                className="h-4 w-4 shrink-0"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M6.012 26.27l2.802-17.604 7.625 7.609-10.427 10.0z"
                  fill="#FFC107"
                />
                <path
                  d="M25.992 26.27l-4.217-18.06-2.225 2.22 6.442 15.84z"
                  fill="#F4B400"
                />
                <path
                  d="M16.438 4.015l-3.805 7.755 3.805 3.795z"
                  fill="#FFA000"
                />
                <path
                  d="M16.438 31.005c5.362 0 9.708-4.27 9.708-9.54s-4.346-9.54-9.708-9.54-9.708 4.27-9.708 9.54 4.346 9.54 9.708 9.54z"
                  fill="#DD2C00"
                />
              </svg>
              <span>Firebase</span>
            </div>

            {/* Cloud Firestore */}
            <div className="flex items-center gap-1.5">
              <svg
                viewBox="0 0 24 24"
                className="h-4 w-4 shrink-0"
                fill="none"
                stroke="#FFC107"
                strokeWidth="2.5"
              >
                <ellipse
                  cx="12"
                  cy="6"
                  rx="9"
                  ry="3"
                  fill="#FFA000"
                  stroke="#F57C00"
                  strokeWidth="1"
                />
                <path
                  d="M3 6v6c0 1.66 4 3 9 3s9-1.34 9-3V6"
                  fill="#FFCA28"
                  stroke="#FFA000"
                  strokeWidth="1"
                />
                <path
                  d="M3 12v6c0 1.66 4 3 9 3s9-1.34 9-3v-6"
                  fill="#F57C00"
                  stroke="#DD2C00"
                  strokeWidth="1"
                />
              </svg>
              <span>Cloud Firestore</span>
            </div>

            {/* Spring Boot */}
            <div className="flex items-center gap-1.5">
              <svg
                viewBox="0 0 24 24"
                className="h-4 w-4 shrink-0"
                fill="#6DB33F"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path d="M12.016 1.996C6.5 1.996 2 6.496 2 12.012c0 2.253.765 4.316 2.023 6.012l13.98-13.98A9.972 9.972 0 0012.016 1.996zm6.34 3.992L4.376 19.968a9.972 9.972 0 007.64 4.028c5.516 0 10.012-4.496 10.012-10.012 0-2.996-1.328-5.69-3.672-7.988z" />
              </svg>
              <span>Spring Boot</span>
            </div>

            {/* Firestore */}
            <div className="flex items-center gap-1.5">
              <svg
                viewBox="0 0 24 24"
                className="h-4 w-4 shrink-0"
                fill="#00ED64"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path d="M12 .5a15.8 15.8 0 00-4 10.3c0 6.6 4 10.7 4 10.7s4-4.1 4-10.7A15.8 15.8 0 0012 .5z" />
                <path
                  d="M12 .5v20.7s-2.5-3.3-2.5-10.7c0-3.3 1-7.1 2.5-10z"
                  fill="#00684A"
                />
              </svg>
              <span>Firestore</span>
            </div>
          </div>
        </div>
      </div>

      {/* --- Full-Screen Loading Interstitial Overlay --- */}
      <AnimatePresence>
        {isLoading && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-[#030014]/90 backdrop-blur-md flex flex-col items-center justify-center z-50 font-sans"
          >
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-80 h-80 bg-blue-600/10 rounded-full blur-3xl"></div>
            <div className="glass-card p-8 rounded-2xl border border-white/5 flex flex-col items-center gap-4 text-center max-w-xs shadow-glow-violet relative">
              <div className="inline-flex p-3 bg-gradient-to-tr from-blue-500 to-indigo-600 rounded-xl shadow-glow-violet">
                <Sparkles className="h-6 w-6 text-white animate-pulse" />
              </div>
              <div className="space-y-1">
                <h3 className="font-extrabold text-white text-sm">
                  Initializing Session
                </h3>
                <p className="text-[10px] text-cyan-400 font-mono uppercase tracking-widest transition-all duration-300">
                  {loadingStatements[loadingStep]}
                </p>
              </div>
              <Loader2 className="h-5 w-5 text-cyan-400 animate-spin mt-2" />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default Login;
