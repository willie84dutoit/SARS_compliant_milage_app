# Epic 02 — Cloud Sync & Compliance (Phase-2+)

Added only after the MVP and data model are stable (post_mvp_api_plan.md). Sync, tamper-evidence,
web review, SARS export, and the third-party API.

---

### US-101 · Tamper-evident trip records   [Cross-cutting] [8]
**As a** business owner **I want** each trip cryptographically protected **so that** my logbook is
defensible in a SARS audit.

**Acceptance criteria**
- [ ] Given a trip is completed, when it is saved, then a signature/hash covering the canonical fields is stored with it.
- [ ] Given a stored trip is altered, when integrity is checked, then verification fails and is flagged.
- [ ] Given a trip is synced to Firestore, when the web app reads it, then the integrity proof still verifies.

**Owner agent:** security-crypto-specialist  ·  **Tasks:** T-008  ·  **Status:** Backlog

---

### US-102 · Sync completed trips to the cloud   [Phase-2] [5]
**As a** driver **I want** my completed trips uploaded securely **so that** I can access them on the web.

**Acceptance criteria**
- [ ] Given network is available, when a background sync runs, then unsynced completed trips upload and are marked `synced`.
- [ ] Given the same trip uploads twice, when the server processes it, then no duplicate is created (idempotent).
- [ ] Given a user, when they query, then they can only read their own trips (security rules enforced).

**Owner agent:** backend-engineer (+ security-crypto-specialist)  ·  **Tasks:** T-009  ·  **Status:** Backlog

---

### US-103 · Admin review and approval   [Phase-2] [5]
**As an** admin **I want** to review, flag, and approve trips **so that** only compliant trips reach a SARS export.

**Acceptance criteria**
- [ ] Given synced trips, when I open the review screen, then I can filter by date, classification, and status.
- [ ] Given a non-compliant trip (missing business reason / verified odometer / unresolved review), when I review, then I can flag it for correction.
- [ ] Given a trip, when I approve it, then it becomes eligible for SARS export.

**Owner agent:** backend-engineer (+ analytics-specialist)  ·  **Tasks:** T-011  ·  **Status:** Backlog

---

### US-104 · SARS-ready export   [Phase-2] [5]
**As a** business owner **I want** a SARS-ready report **so that** I can submit a compliant mileage claim.

**Acceptance criteria**
- [ ] Given approved + completed trips, when I export, then only those are included with all required fields.
- [ ] Given any draft/flagged/incomplete trip, when I export, then it is excluded or clearly flagged.
- [ ] Given the export, when generated, then it is built from synced Firestore data, not raw device files.

**Owner agent:** analytics-specialist (+ backend-engineer)  ·  **Tasks:** T-011  ·  **Status:** Backlog

---

### US-105 · Third-party accounting API   [Phase-2] [5]
**As an** accounting integrator **I want** a secure API for approved trips **so that** I can pull mileage into payroll/accounting.

**Acceptance criteria**
- [ ] Given a scoped token, when I call the export endpoint, then I receive only approved records as JSON/CSV with pagination.
- [ ] Given any access, when it happens, then it is audit-logged.
- [ ] Given records are pulled, when I mark them exported, then the audit trail records what was shared.

**Owner agent:** backend-engineer (+ security-crypto-specialist)  ·  **Tasks:** T-009  ·  **Status:** Backlog

---

### US-106 · Containerised, version-controlled, reproducible   [Cross-cutting] [3]
**As a** developer **I want** the backend in Docker, the code in GitHub, and Python in a .venv
**so that** anyone can build and deploy it reproducibly.

**Acceptance criteria**
- [ ] Given a clean machine, when I follow the README, then I can build the image and run it locally via docker-compose.
- [ ] Given the repo, when I inspect it, then `.venv/` and all secrets are git-ignored and absent from history.
- [ ] Given a push, when CI runs, then lint + tests + container build all execute and fail loudly on error.

**Owner agent:** devops-engineer  ·  **Tasks:** T-014  ·  **Status:** Backlog
