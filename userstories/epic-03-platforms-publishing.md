# Epic 03 — Platforms & Store Publishing (Phase-3/4 + Cross-cutting)

Getting the app onto iOS and Huawei AppGallery, and getting all three stores (Google Play, Apple
App Store, Huawei AppGallery) to actually approve and list it. Maps to `publisheing guide.md` §6–7
and `post_mvp_api_plan.md` iOS notes.

---

### US-201 · iOS app mirrors the Android MVP   [Phase-3] [8]
**As a** driver on iPhone **I want** the same trip-capture experience as Android **so that** my
choice of phone doesn't limit which mileage tracker I can use.

**Acceptance criteria**
- [ ] Given the same trip on both platforms, when detection runs, then iOS (CoreMotion automotive
  state) and Android (ActivityRecognition `IN_VEHICLE`) trigger the same start/stop behaviour
  against the same locked v1 thresholds (70% start, 3-min stop, 2-min unstable, 30s prompt, 10m filter).
- [ ] Given a classification prompt fires, when the device is locked, then an iOS actionable
  notification offers the same Work/Private buttons as the Android lock-screen action.
- [ ] Given an odometer photo is captured, when OCR runs, then Apple's Vision framework
  (`VNRecognizeTextRequest`) produces an equivalent verified-value/manual-fallback outcome to the
  Android ML Kit path, against the same 80% confidence gate.
- [ ] Given the app uses Google Sign-In, when a user signs in, then "Sign in with Apple" is offered
  as an equivalent option (Apple Guideline 4.8 — mandatory, not optional, if any third-party login exists).
- [ ] Given the data model, when trips sync, then the iOS client writes to the same Firestore
  schema/contract as Android — no platform-specific fields, no second source of truth.

**Owner agent:** ios-engineer  ·  **Tasks:** T-013  ·  **Status:** Backlog

---

### US-202 · Pass Google Play and Apple App Store review   [Cross-cutting] [5]
**As a** business owner **I want** the app to actually get approved and stay listed **so that** I
can rely on it instead of it being pulled for a compliance violation.

**Acceptance criteria**
- [ ] Given background location is requested, when the permission prompt appears, then a
  "Prominent Disclosure" screen explaining exactly why and what is collected is shown first
  (Google Play requirement), and a short review-video demonstrating it exists for submission.
- [ ] Given iOS background location, when `Info.plist` is built, then
  `NSLocationAlwaysAndWhenInUseUsageDescription` and `NSMotionUsageDescription` carry detailed,
  accurate justifications, and the app stays functional if the user picks "Allow Once" / "While
  Using App" instead of "Always."
- [ ] Given the app is published, when reviewed, then a hosted, publicly accessible Privacy Policy
  URL exists and accurately describes location + camera data handling.
- [ ] Given store questionnaires, when submitted, then the Google Play Data Safety form and Apple
  App Privacy labels are both completed and consistent with each other and with the actual code.
- [ ] Given this is a new personal Google Play developer account, when applying for production,
  then the app has already run on the Closed Testing track with ≥20 opted-in testers for ≥14
  continuous days.
- [ ] Given account deletion (US-107) exists, when a reviewer checks, then it is reachable in-app
  without contacting support, satisfying both stores' mandatory deletion requirement.

**Owner agent:** compliance-qa-specialist  ·  **Tasks:** T-012  ·  **Status:** Backlog

---

### US-203 · Huawei AppGallery technical compatibility (no GMS)   [Phase-4] [8]
**As a** driver on a Huawei device without Google Mobile Services **I want** the app to work fully
on HMS **so that** I'm not locked out just because of my phone brand.

**Acceptance criteria**
- [ ] Given a Huawei device with no GMS, when I sign in, then Huawei Account Kit (or an
  email/password fallback) works in place of Google OAuth.
- [ ] Given trip detection, when running on HMS, then Huawei Location Kit's
  `ActivityIdentificationService` produces the same `IN_VEHICLE`-equivalent transition and the same
  locked v1 thresholds as the Google-API path.
- [ ] Given odometer capture, when running on HMS, then Huawei ML Kit Text Recognition replaces
  Google ML Kit and meets the same 80% confidence gate and manual-fallback behaviour.
- [ ] Given cloud sync, when running on HMS, then trip data is routed through the Flask backend
  (T-009) via plain HTTPS REST — never a direct Firestore SDK call, since that requires GMS.
- [ ] Given the build, when assembled, then it is a separate product flavor from the GMS build, not
  a runtime branch that ships both SDKs into every APK.

**Owner agent:** geo-sensors-specialist + ml-ocr-specialist (+ backend-engineer)  ·  **Tasks:** T-016  ·  **Status:** Backlog

---

### US-204 · Pass Huawei AppGallery verification and listing   [Phase-4] [3]
**As a** business owner **I want** the Huawei build to actually get listed **so that** Huawei users
can install and use it the same as Play Store / App Store users.

**Acceptance criteria**
- [ ] Given AppGallery Connect developer registration, when submitted, then a passport, driver's
  license, or national ID is provided as required for verification.
- [ ] Given background location is used, when applying, then the dedicated AppGallery Connect
  permission application form is completed with a supporting video demonstration.
- [ ] Given the listing goes live, when reviewed, then a prominent, publicly accessible privacy
  policy URL is present.
- [ ] Given any plan to distribute inside mainland China, when scoped, then local data residency,
  an ICP license, and PIPL compliance are explicitly called out as a separate, larger undertaking —
  not silently assumed to be covered by the existing Firestore/Cloud Run setup.

**Owner agent:** compliance-qa-specialist  ·  **Tasks:** T-012  ·  **Status:** Backlog
