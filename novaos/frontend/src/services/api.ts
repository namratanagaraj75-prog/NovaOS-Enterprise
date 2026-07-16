import axios from "axios";
import { auth } from "../lib/firebase";

const rawApiUrl = (import.meta.env.VITE_API_URL || "/api").replace(/^VITE_API_URL=/, "");

const apiClient = axios.create({
  baseURL: rawApiUrl,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,
});

// Interceptor to automatically attach Authorization header
apiClient.interceptors.request.use(
  async (config) => {
    try {
      const currentUser = auth.currentUser;
      if (currentUser) {
        const idToken = await currentUser.getIdToken();
        config.headers.Authorization = `Bearer ${idToken}`;
        // Dev-only debug: store a masked preview of the token for troubleshooting
        if (import.meta.env.DEV) {
          try {
            const preview = `${idToken.slice(0, 6)}...${idToken.slice(-6)}`;
            console.debug("[api] attaching idToken preview:", preview);
            // store masked preview (not full token) for local troubleshooting
            localStorage.setItem("novaos_debug_idTokenPreview", preview);
          } catch (e) {
            /* ignore preview errors */
          }
        }
      } else {
        const backupToken = localStorage.getItem("novaos_token");
        if (backupToken) {
          config.headers.Authorization = `Bearer ${backupToken}`;
          if (import.meta.env.DEV) {
            try {
              const preview = `${backupToken.slice(0, 6)}...${backupToken.slice(-6)}`;
              console.debug("[api] attaching backup token preview:", preview);
              localStorage.setItem("novaos_debug_idTokenPreview", preview);
            } catch (e) {}
          }
        }
      }
    } catch (error) {
      console.error("Error attaching auth token in interceptor:", error);
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

export default apiClient;
