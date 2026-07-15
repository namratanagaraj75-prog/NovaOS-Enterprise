import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getAnalytics } from 'firebase/analytics';
import { getFirestore } from 'firebase/firestore';

// Web app Firebase client configurations. Reads from Vite env parameters.
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || ""
};

// Check if variables are configured
const hasValidConfig = 
  firebaseConfig.apiKey && 
  firebaseConfig.projectId && 
  firebaseConfig.authDomain;

let app;
let auth: any;
let db: any;
let analytics: any;
let isFirebaseMocked = false;

if (hasValidConfig) {
  try {
    app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();
    auth = getAuth(app);
    db = getFirestore(app);
    analytics = getAnalytics(app);
    console.log("Firebase client and Firestore initialized successfully.");
  } catch (error) {
    console.warn("Failed to initialize Firebase Client SDK:", error);
    isFirebaseMocked = true;
  }
} else {
  console.warn(
    "VITE_FIREBASE_* environment variables are missing. NovaOS is running with simulated mock Authentication."
  );
  isFirebaseMocked = true;
}

export { auth, db, isFirebaseMocked, analytics };
