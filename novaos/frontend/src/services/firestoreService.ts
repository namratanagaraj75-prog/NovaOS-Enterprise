import { db } from "../lib/firebase";
import {
  collection,
  doc,
  getDoc,
  getDocs,
  addDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  setDoc,
} from "firebase/firestore";

// ----- Users Management -----
export interface FirestoreUser {
  uid: string;
  name: string;
  email: string;
  role: string;
  department?: string;
  designation?: string;
  status: "Pending" | "Approved" | "Suspended";
  createdAt: string;
  lastLogin?: string;
}

export const getUser = async (uid: string): Promise<FirestoreUser | null> => {
  const docRef = doc(db, "users", uid);
  const snap = await getDoc(docRef);
  return snap.exists() ? (snap.data() as FirestoreUser) : null;
};

export const createUser = async (user: FirestoreUser): Promise<void> => {
  const docRef = doc(db, "users", user.uid);
  try {
    await updateDoc(docRef, { ...user } as any);
  } catch (err) {
    // If update fails because document doesn't exist, create it using setDoc
    await setDoc(docRef, user);
  }
};

// ----- Employees Management -----
export interface FirestoreEmployee {
  id?: string;
  name: string;
  email: string;
  corporateRole: string;
  department: string;
  status: "Active" | "Onboarding" | "Terminated";
  joinedAt: string;
}

export const getEmployees = async (): Promise<FirestoreEmployee[]> => {
  const colRef = collection(db, "employees");
  const snap = await getDocs(colRef);
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }) as FirestoreEmployee);
};

export const createEmployee = async (
  emp: FirestoreEmployee,
): Promise<string> => {
  const colRef = collection(db, "employees");
  const docRef = await addDoc(colRef, emp);
  return docRef.id;
};

// ----- Departments -----
export interface FirestoreDepartment {
  id?: string;
  name: string;
  code: string;
  manager: string;
  headcount: number;
}

export const getDepartments = async (): Promise<FirestoreDepartment[]> => {
  const colRef = collection(db, "departments");
  const snap = await getDocs(colRef);
  return snap.docs.map(
    (d) => ({ id: d.id, ...d.data() }) as FirestoreDepartment,
  );
};

// ----- Workflow Requests -----
export interface FirestoreWorkflowRequest {
  id?: string;
  candidateName: string;
  requestedBy: string;
  status: "Pending" | "Approved" | "Rejected";
  stage: string;
  timestamp: string;
}

export const getWorkflowRequests = async (): Promise<
  FirestoreWorkflowRequest[]
> => {
  const colRef = collection(db, "workflowRequests");
  const q = query(colRef, orderBy("timestamp", "desc"));
  const snap = await getDocs(q);
  return snap.docs.map(
    (d) => ({ id: d.id, ...d.data() }) as FirestoreWorkflowRequest,
  );
};

export const createWorkflowRequest = async (
  req: Omit<FirestoreWorkflowRequest, "id">,
): Promise<string> => {
  const colRef = collection(db, "workflowRequests");
  const docRef = await addDoc(colRef, req);
  return docRef.id;
};

// ----- Notifications Management -----
export interface FirestoreNotification {
  id?: string;
  title: string;
  message: string;
  read: boolean;
  timestamp: string;
  priority: "low" | "medium" | "high";
}

export const getNotifications = async (): Promise<FirestoreNotification[]> => {
  const colRef = collection(db, "notifications");
  const q = query(colRef, orderBy("timestamp", "desc"));
  const snap = await getDocs(q);
  return snap.docs.map(
    (d) => ({ id: d.id, ...d.data() }) as FirestoreNotification,
  );
};

export const createNotification = async (
  notif: Omit<FirestoreNotification, "id">,
): Promise<string> => {
  const colRef = collection(db, "notifications");
  const docRef = await addDoc(colRef, notif);
  return docRef.id;
};

export const markNotificationRead = async (id: string): Promise<void> => {
  const docRef = doc(db, "notifications", id);
  await updateDoc(docRef, { read: true });
};

// ----- Audit Logs -----
export interface AuditLog {
  id?: string;
  action: string;
  actor: string;
  timestamp: string;
  details?: string;
}

export const getAuditLogs = async (): Promise<AuditLog[]> => {
  const colRef = collection(db, "auditLogs");
  const q = query(colRef, orderBy("timestamp", "desc"));
  const snap = await getDocs(q);
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }) as AuditLog);
};

export const writeAuditLog = async (
  action: string,
  actor: string,
  details?: string,
): Promise<string> => {
  const colRef = collection(db, "auditLogs");
  const docRef = await addDoc(colRef, {
    action,
    actor,
    timestamp: new Date().toISOString(),
    details,
  });
  return docRef.id;
};

// ----- Access Requests -----
export interface AccessRequest {
  name: string;
  email: string;
  department: string;
  reason: string;
  status: string;
  createdAt: string;
}

export const createAccessRequest = async (
  req: Omit<AccessRequest, "status" | "createdAt">,
): Promise<string> => {
  const colRef = collection(db, "accessRequests");
  const docRef = await addDoc(colRef, {
    ...req,
    status: "Pending",
    createdAt: new Date().toISOString(),
  });
  return docRef.id;
};

export default {
  getUser,
  createUser,
  getEmployees,
  createEmployee,
  getDepartments,
  getWorkflowRequests,
  createWorkflowRequest,
  getNotifications,
  createNotification,
  markNotificationRead,
  getAuditLogs,
  writeAuditLog,
  createAccessRequest,
};
