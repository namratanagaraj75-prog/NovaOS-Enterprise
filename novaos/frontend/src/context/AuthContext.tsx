import React, { createContext, useContext, useState, useEffect } from "react";
import {
  signInWithPopup,
  GoogleAuthProvider,
  signInWithEmailAndPassword,
  signOut,
  onAuthStateChanged,
  browserLocalPersistence,
  setPersistence,
  User as FirebaseUser,
} from "firebase/auth";
import { doc, getDoc } from "firebase/firestore";
import apiClient from "../services/api";
import { auth, db } from "../lib/firebase";

interface AuthUser {
  uid: string;
  email: string;
  displayName?: string;
  role: string;
  department?: string;
  designation?: string;
  photoURL?: string;
  active?: boolean;
  createdAt?: string;
}

interface AuthContextType {
  user: AuthUser | null;
  isLoading: boolean;
  loginWithGoogle: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string, displayName?: string) => Promise<void>;
  logout: () => Promise<void>;
  authError: string | null;
  clearAuthError: () => void;
}

type VerificationError = Error & { status?: number; code?: string };

const TOKEN_STORAGE_KEY = "novaos_token";
const USER_STORAGE_KEY = "novaos_user";
const ACCESS_DENIED_MESSAGE =
  "Access Denied\nYou are not an authorized NovaOS employee.";

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const readCachedUser = (): AuthUser | null => {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  } catch {
    localStorage.removeItem(USER_STORAGE_KEY);
    return null;
  }
};

const storeSession = (token: string, nextUser: AuthUser) => {
  localStorage.setItem(TOKEN_STORAGE_KEY, token);
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(nextUser));
};

const clearStoredSession = () => {
  localStorage.removeItem(TOKEN_STORAGE_KEY);
  localStorage.removeItem(USER_STORAGE_KEY);
};

const normalizeRole = (role?: string | null) => {
  const value = (role || "").trim().toUpperCase().replace(/[\s-]+/g, "_");
  if (value === "CEO") return "CEO";
  if (value === "SUPER_ADMIN") return "SUPER_ADMIN";
  if (value === "HR" || value === "HR_ADMIN") return "HR_ADMIN";
  if (value === "HIRING_MANAGER" || value === "MANAGER") return "HIRING_MANAGER";
  if (value === "FINANCE") return "FINANCE";
  if (value === "LEGAL") return "LEGAL";
  return value;
};

const authErrorFrom = (error: any, fallback = "Authorization check failed.") => {
  if (error?.response?.data?.message) return error.response.data.message;
  if (error?.message) return error.message;
  return fallback;
};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(() => readCachedUser());
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [authError, setAuthError] = useState<string | null>(null);

  const clearAuthError = () => setAuthError(null);

  const clearRejectedSession = async (message?: string) => {
    if (message) setAuthError(message);
    clearStoredSession();
    setUser(null);
    if (auth.currentUser) {
      await signOut(auth);
    }
  };

  const readAuthorizedEmployee = async (fbUser: FirebaseUser): Promise<AuthUser> => {
    const snapshot = await getDoc(doc(db, "users", fbUser.uid));
    if (!snapshot.exists()) {
      throw new Error(ACCESS_DENIED_MESSAGE);
    }

    const data = snapshot.data();
    if (data.active !== true) {
      throw new Error("Access Denied\nYour NovaOS employee account is inactive.");
    }

    const role = normalizeRole(String(data.role || ""));
    if (!role) {
      throw new Error("Access Denied\nYour NovaOS employee account has no assigned role.");
    }

    return {
      uid: fbUser.uid,
      email: String(data.email || fbUser.email || ""),
      displayName: String(data.displayName || data.name || fbUser.displayName || "NovaOS User"),
      role,
      department: data.department ? String(data.department) : undefined,
      designation: data.designation ? String(data.designation) : undefined,
      photoURL: data.photoURL ? String(data.photoURL) : fbUser.photoURL || undefined,
      active: true,
      createdAt: data.createdAt ? String(data.createdAt) : undefined,
    };
  };

  const verifyWithBackend = async (fbUser: FirebaseUser, frontendUser: AuthUser, forceRefresh = false) => {
    try {
      const idToken = await fbUser.getIdToken(forceRefresh);
      const res = await apiClient.post("/auth/verify", { idToken });
      const backendUser = res.data.user as AuthUser;
      const verifiedUser = {
        ...frontendUser,
        ...backendUser,
        role: normalizeRole(backendUser.role || frontendUser.role),
      };
      storeSession(res.data.token || idToken, verifiedUser);
      return verifiedUser;
    } catch (error: any) {
      const verificationError = new Error(authErrorFrom(error)) as VerificationError;
      verificationError.status = error.response?.status;
      verificationError.code = error.code;
      throw verificationError;
    }
  };

  const completeLogin = async (fbUser: FirebaseUser, forceRefresh = false) => {
    const frontendUser = await readAuthorizedEmployee(fbUser);
    const verifiedUser = await verifyWithBackend(fbUser, frontendUser, forceRefresh);
    setUser(verifiedUser);
    setAuthError(null);
    return verifiedUser;
  };

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      setIsLoading(true);
      try {
        if (!firebaseUser) {
          clearStoredSession();
          setUser(null);
          return;
        }
        await completeLogin(firebaseUser, false);
      } catch (error: any) {
        await clearRejectedSession(error.message || ACCESS_DENIED_MESSAGE);
      } finally {
        setIsLoading(false);
      }
    });

    return () => unsubscribe();
  }, []);

  const loginWithGoogle = async () => {
    setIsLoading(true);
    setAuthError(null);
    try {
      await setPersistence(auth, browserLocalPersistence);
      const provider = new GoogleAuthProvider();
      provider.setCustomParameters({ prompt: "select_account" });
      const credential = await signInWithPopup(auth, provider);
      await completeLogin(credential.user, true);
    } catch (error: any) {
      await clearRejectedSession(authErrorFrom(error, ACCESS_DENIED_MESSAGE));
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const login = async (email: string, password: string) => {
    setIsLoading(true);
    setAuthError(null);
    try {
      await setPersistence(auth, browserLocalPersistence);
      const credential = await signInWithEmailAndPassword(auth, email, password);
      await completeLogin(credential.user, true);
    } catch (error: any) {
      let errorMessage = authErrorFrom(error);
      if (
        error.code === "auth/invalid-credential" ||
        error.code === "auth/wrong-password" ||
        error.code === "auth/user-not-found"
      ) {
        errorMessage = "Invalid email or password. Please verify credentials and try again.";
      } else if (error.code === "auth/invalid-email") {
        errorMessage = "Malformed email address structure. Please verify formatting.";
      } else if (error.code === "auth/network-request-failed") {
        errorMessage = "Network offline or disconnected. Check internet connection parameters.";
      }
      await clearRejectedSession(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const signUp = async () => {
    const message = "Access Denied\nPublic registration is disabled. Contact your NovaOS administrator.";
    setAuthError(message);
    throw new Error(message);
  };

  const logout = async () => {
    setIsLoading(true);
    try {
      await signOut(auth);
      clearStoredSession();
      setUser(null);
      setAuthError(null);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthContext.Provider
      value={{ user, isLoading, loginWithGoogle, login, signUp, logout, authError, clearAuthError }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be called from within an AuthProvider scope.");
  }
  return context;
};