# 🚀 NovaOS – AI Employee Copilot

<p align="center">
  <strong>Enterprise AI Employee Copilot for Intelligent HR Automation</strong>
</p>

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
- Secure SMTP delivery
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
 Offer Letter Generator              SMTP Email Service
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

- Gmail SMTP

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
SMTP_HOST=
SMTP_PORT=
SMTP_USERNAME=
SMTP_PASSWORD=
FIREBASE_SERVICE_ACCOUNT=
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
