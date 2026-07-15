import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, ArrowLeft, Home } from 'lucide-react';
import { motion } from 'framer-motion';

export const Unauthorized: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="relative min-h-screen w-screen bg-[#030014] flex items-center justify-center p-4 overflow-hidden">
      {/* Decorative Blur Background Orbs */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-red-600/10 rounded-full blur-3xl animate-pulse-slow"></div>
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-violet-600/10 rounded-full blur-3xl animate-pulse-slow"></div>

      <motion.div 
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-md z-10"
      >
        <div className="glass-card p-8 rounded-2xl relative overflow-hidden text-center border border-white/5">
          <div className="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-red-500 via-violet-500 to-red-500"></div>

          <div className="inline-flex p-4 bg-red-500/10 rounded-full mb-6 border border-red-500/20 text-red-500 shadow-glow-red">
            <ShieldAlert className="h-12 w-12" />
          </div>

          <h1 className="text-3xl font-extrabold text-white tracking-tight font-sans mb-2">
            Access Denied
          </h1>
          <p className="text-sm text-red-400 font-mono mb-6">
            ERROR CODE: 403_FORBIDDEN
          </p>

          <p className="text-sm text-gray-400 leading-relaxed mb-8 font-sans">
            You do not have the required permissions or security clearance to access this module. If you believe this is an error, please contact your systems administrator to modify your role.
          </p>

          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <button
              onClick={() => navigate(-1)}
              className="flex items-center justify-center gap-2 px-5 py-3 bg-white/5 border border-white/10 rounded-xl text-white font-semibold hover:bg-white/10 transition-all duration-300 text-sm font-sans"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Go Back</span>
            </button>

            <button
              onClick={() => navigate('/dashboard')}
              className="flex items-center justify-center gap-2 px-5 py-3 bg-gradient-to-r from-cyan-500 to-blue-600 rounded-xl text-white font-semibold hover:from-cyan-400 hover:to-blue-500 shadow-glow-violet hover:shadow-glow-cyan transition-all duration-300 text-sm font-sans"
            >
              <Home className="h-4 w-4" />
              <span>Dashboard</span>
            </button>
          </div>
        </div>

        <p className="text-center text-[10px] text-gray-600 mt-8 font-mono tracking-widest uppercase">
          NovaOS Security Gatekeeper
        </p>
      </motion.div>
    </div>
  );
};

export default Unauthorized;
