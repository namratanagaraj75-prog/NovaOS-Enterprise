# 🚀 NovaOS – AI Employee Copilot

<p align="center">
  <strong>Enterprise AI Employee Copilot for Intelligent HR Automation</strong>
</p>

## Rejection notifications and Legal policy review

Rejections run only through Spring Boot. A successful rejection terminates the workflow, records the internal reason and audit event, then sends a privacy-safe candidate email through Resend. Delivery failure never rolls back rejection; authorized reviewers can retry from the hiring request page.

Legal reviewers complete a versioned policy checklist before approval. Mandatory unresolved policies block approval, and policy-specific changes can be routed to HR or Finance. Super Admins manage policies at `/admin/legal-policies`; review history retains policy snapshots.

New Firestore data uses `hiringRequests.candidateEmailNotification`, `legalPolicies`, `legalReviews` (plus `history`), and `auditLogs`. New APIs are rooted at `/api/hiring/requests/{id}/reject`, `/api/hiring/requests/{id}/rejection-email/retry`, `/api/legal/policies`, and `/api/legal/reviews/{id}`.

The backend reads `RESEND_API_KEY`, `RESEND_FROM_EMAIL`, and optional `RESEND_FROM_NAME`. Missing credentials leave the application running and mark the notification `FAILED`.

<p align="center">
  Automating hiring workflows, multi-level approvals, offer letter generation, secure email delivery, and enterprise decision tracking.
</p>

---

# 📌 Overview

NovaOS is an enterprise-grade AI Employee Copilot designed to automate and streamline internal HR operations.

Instead of manually coordinating approvals between HR, Hiring Managers, Finance, and Legal teams, NovaOS manages the complete hiring lifecycle through an intelligent workflow that generates professional offer letters and securely delivers them to candidates.

The platform provides role-based dashboards, approval tracking, AI-assisted document generation, audit history, email automation, and real-time workflow visibility.

---

# ✨ Key Features

## 👥 Multi-Role Authentication

- HR Admin
- Hiring Manager
- Finance Team
- Legal Team
- CEO/Admin (if configured)

Each user only sees the modules and requests relevant to their role.

---

## 📝 Smart Hiring Request Creation

HR can create a hiring request by entering:

- Candidate Name
- Email Address
- Position
- Department
- Salary Package
- Joining Date
- Reporting Manager
- Hiring Manager
- Work Location
- Employment Type

---

## 🔄 Intelligent Approval Workflow

NovaOS automatically routes requests through the approval chain.

```
HR
   │
   ▼
Hiring Manager
   │
   ▼
Finance
   │
   ▼
Legal
   │
   ▼
Offer Letter Generated
   │
   ▼
Email Delivered
```

Every approval is securely tracked with timestamps and audit logs.

---

# 🤖 AI Decision Passport

NovaOS provides complete visibility into every hiring decision.

The AI Decision Passport records:

- Approval history
- Workflow status
- Assigned reviewers
- Processing stages
- Offer generation status
- Email delivery status
- Complete audit trail

---

# 📄 Professional Offer Letter Generation

Once every approval is completed, NovaOS automatically generates a professional enterprise offer letter containing:

- Candidate Details
- Position
- Department
- Compensation
- Joining Date
- Reporting Manager
- Employment Terms
- Confidentiality Clause
- Digital Signatures
- Company Branding

No manual document creation is required.

---

# 📧 Automated Email Delivery

After the PDF is generated:

- Offer Letter is attached automatically
- Secure Resend API delivery
- Candidate receives email instantly
- Delivery status is stored
- Timestamp recorded
- Retry option available if required

---

# 📊 Dashboard

The dashboard provides real-time visibility into hiring activities.

Includes:

- Hiring Requests
- Pending Approvals
- Approved Requests
- Email Status
- Recent Activities
- Workflow Progress
- Notifications

---

# 🔔 Smart Notifications

NovaOS provides:

- Pending approval notifications
- Sidebar notification badges
- Email delivery alerts
- Workflow completion alerts

Each notification is role-based.

---

# 🧾 Audit Trail

Every important action is logged.

Examples:

- Request Created
- HR Approved
- Hiring Manager Approved
- Finance Approved
- Legal Approved
- Offer Generated
- Email Sent

All actions include timestamps.

---

# 🔒 Security

NovaOS follows secure enterprise practices.

- Firebase Authentication
- Role-Based Access Control (RBAC)
- Protected Routes
- Firestore Security Rules
- Backend Email Processing
- Secure API Communication

---

# 🏗️ System Architecture

```
                +----------------------+
                |      React + Vite    |
                +----------+-----------+
                           |
                           |
                   Firebase Authentication
                           |
                           |
                    Firestore Database
                           |
                           |
                Spring Boot REST API
                           |
         +-----------------+----------------+
         |                                  |
         |                                  |
 Offer Letter Generator              Resend Email Service
         |                                  |
         +-----------------+----------------+
                           |
                    Candidate Email
```

---

# 🛠️ Technology Stack

## Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- Framer Motion
- Lucide Icons

---

## Backend

- Spring Boot
- Java
- REST APIs

---

## Database

- Firebase Firestore

---

## Authentication

- Firebase Authentication

---

## Document Generation

- iText PDF

---

## Email Service

- Resend HTTPS API

---

## Cloud Services

- Firebase
- Firestore
- Firebase Authentication

---

# 📂 Project Structure

```
NovaOS
│
├── frontend
│   ├── components
│   ├── pages
│   ├── layouts
│   ├── services
│   ├── hooks
│   ├── utils
│   └── assets
│
├── backend
│   ├── controllers
│   ├── services
│   ├── models
│   ├── repositories
│   ├── security
│   └── configuration
│
├── firebase
│
├── public
│
└── README.md
```

---

# ⚙️ Installation

## Clone Repository

```bash
git clone https://github.com/your-username/NovaOS.git
```

---

## Frontend

```bash
cd frontend

npm install

npm run dev
```

---

## Backend

```bash
cd backend

./mvnw spring-boot:run
```

---

# 🔐 Environment Variables

## Frontend (.env)

```env
VITE_API_URL=
VITE_FIREBASE_API_KEY=
VITE_FIREBASE_AUTH_DOMAIN=
VITE_FIREBASE_PROJECT_ID=
VITE_FIREBASE_STORAGE_BUCKET=
VITE_FIREBASE_MESSAGING_SENDER_ID=
VITE_FIREBASE_APP_ID=
```

---

## Backend

```env
RESEND_API_KEY=
RESEND_FROM_EMAIL=
RESEND_FROM_NAME=Nova HR
FIREBASE_CONFIG_PATH=file:./firebase-service-account.json
FIREBASE_PROJECT_ID=
FIREBASE_DATABASE_URL=
# Alternative to FIREBASE_CONFIG_PATH:
FIREBASE_PRIVATE_KEY_ID=
FIREBASE_PRIVATE_KEY=
FIREBASE_CLIENT_EMAIL=
FIREBASE_CLIENT_ID=
```

---

# 📈 Workflow

```
Create Hiring Request

        ↓

Hiring Manager Approval

        ↓

Finance Approval

        ↓

Legal Approval

        ↓

Generate Offer Letter

        ↓

Attach PDF

        ↓

Send Email

        ↓

Update Audit Logs

        ↓

Workflow Completed
```

---

# 🎯 Highlights

- Enterprise-grade UI
- Multi-level approvals
- AI-assisted hiring workflow
- Automated offer letter generation
- Automated email delivery
- Role-based dashboards
- Audit history
- Notification system
- Secure authentication
- Professional document generation

---

# 🚀 Future Enhancements

- AI Resume Screening
- Interview Scheduling
- Offer Acceptance Portal
- Employee Onboarding
- Analytics Dashboard
- HR Reports
- Digital Signature Integration
- Calendar Integration
- Multi-language Support
- Mobile Application

---

# 👩‍💻 Author

**Namrata N. S.**

B.Tech – Computer Science Engineering (Data Engineering + Generative AI)

Passionate about building AI-powered enterprise automation systems.

---

# 📄 License

This project is developed for educational, research, and hackathon purposes.

---

<p align="center">
Built with ❤️ using React, Spring Boot, Firebase, and AI Automation.
</p>
