# Post-MVP API and Accounting Integration Plan

## Purpose
This document captures the future phase after the Android MVP is complete. It is intentionally separate from the main developer handoff so the MVP remains focused, simple, and deliverable in two weeks.

## Phase 2 Goals
After the MVP is stable, the next phase can add a secure API layer so approved trip data can be shared with accounting, payroll, or compliance systems.

## Proposed Direction
- Build a secure REST API for exporting approved trip data in structured JSON and CSV formats.
- Use authenticated access for business or accounting integrations, with role-based permissions for reading exported data.
- Keep the API optional and separate from the local-first MVP so the app remains usable offline.
- Design the API to support future accounting-system integration, but keep it out of scope for the initial two-week MVP.

## Suggested API Scope
- Export approved trips only
- Provide structured trip metadata for accounting workflows
- Support pagination and filtering for large trip histories
- Include audit-friendly fields such as timestamps, classification, business reason, and verified odometer values

## Suggested Technical Direction
- Backend: lightweight REST API (for example, Node.js, Firebase Functions, or a simple cloud service)
- Authentication: token-based or OAuth-based access for approved integrations
- Storage: reuse the local trip model and expose a clean API contract for downstream systems

## Web App / Dashboard Phase
After the API is available, a separate web app can be built to support reporting, review, and downstream integration for accounting or compliance users.

### Proposed Web App Direction
Use a Flask + Firestore + Google Cloud Platform stack for the post-MVP web layer:
- Backend: Flask on GCP (Cloud Run or App Engine)
- Database: Firestore for trip records and user metadata
- Authentication: Google sign-in or email/password via Firebase Authentication
- Sync layer: Android app uploads completed trips to Firestore through a secure API
- External integration: a separate API for third-party accounting or compliance systems

### Web App Goals
- Show approved trip history in a simple dashboard
- Allow users or administrators to review trips, business reasons, and exported records
- Provide a clean place for future accounting or compliance workflows

### Suggested Web App Scope
- Authentication for staff or business users
- Search and filtering by date, classification, and status
- Exportable reports in CSV or PDF
- Read-only review screens for approved trip data
- User management for admins and business users
- SARS-ready export workflows for approved trip history
- Workflow rules for review, approval, correction, and export

### Syncing from the Android App
- The Android app uploads completed trips to Firestore through a secure API endpoint.
- Each trip record should include a user identity, timestamps, classification, business reason, verified odometer value, and status.
- The sync process should be idempotent so repeated uploads do not create duplicate records.
- The web app should read only the approved, synced trip records from Firestore.
- The sync path should preserve the original trip source, the OCR result, and the local status so the web app can show what was verified locally and what still needs review.

### External API for Third-Party Apps
- Expose a secure REST API for approved third-party systems to retrieve trip data.
- Use authentication and scoped access for each integration.
- Provide export endpoints for JSON and CSV.
- Limit access to approved records only, with audit logging for API usage.
- The API should support a clear workflow: pull approved trip data, mark records as exported, and keep an audit trail of what was shared.

### User Management
- Support at least two roles: user and admin.
- Users should be able to manage their own trip data and export their records.
- Admins should be able to review, approve, and manage account access.
- The system should support account creation, login, and account disablement.
- The web app should also support a review workflow where an admin can flag suspicious, incomplete, or non-compliant trips for correction before export.

### User Interface
- Dashboard for summary metrics and recent trips
- Trip review screen for completed and pending records
- Export screen for CSV and SARS-ready reporting
- Admin screen for user management and system status
- Correction screen for fixing incomplete or flagged trip entries
- Keep the UI simple, review-focused, and business-friendly

### SARS Export
- Provide a dedicated export flow for SARS-ready trip information.
- Export should include the fields required for compliance and reporting.
- The export should be generated from the synced data stored in Firestore, not from raw local device files.
- The export process should clearly distinguish between draft, approved, and completed trips.
- The export should support a compliance rule that only approved and completed trips are included in the official SARS report.
- Any trip with missing business reason, missing verified odometer data, or unresolved review status should be excluded or flagged before export.

### Technical Direction for the Web App
- Frontend: simple React or similar web app
- Backend integration: consume the same secure API used by accounting integrations
- Design principle: keep the web app lightweight and focused on review and reporting, not trip capture
- The web app should enforce the same core compliance rules as the Android MVP: Work trips require a business reason, OCR fallback must be visible, and incomplete trips must not be exported as final records.

## Important Design Principle
The MVP should remain local-first. The API layer should be added only after the core Android app is working reliably and the data model is stable.
