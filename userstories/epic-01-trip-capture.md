# Epic 01 — Trip Capture (Android MVP)

The local-first core: detect a drive, classify it, back it with an odometer photo, save it, and
export it. Maps to developer_handoff_brief.md §2 and §9.

---

### US-001 · Automatic vehicle detection   [MVP] [5]
**As a** driver **I want** the app to notice when I start driving **so that** I don't have to start
tracking manually.

**Acceptance criteria**
- [ ] Given background detection is enabled, when a confirmed `IN_VEHICLE` event (≥70% confidence) occurs, then a trip is started within 5 s.
- [ ] Given low confidence, when detection is uncertain, then the app retries silently for up to 30 s before prompting.
- [ ] Given permissions are denied, when a drive begins, then the app falls back to manual start without crashing.

**Owner agent:** geo-sensors-specialist (+ android-engineer)  ·  **Tasks:** T-002  ·  **Status:** Backlog

---

### US-002 · Classify a trip as Work or Private   [MVP] [3]
**As a** driver **I want** a quick Work/Private prompt **so that** my trips are categorised for claims.

**Acceptance criteria**
- [ ] Given a trip starts, when the prompt fires, then a HIGH-importance notification with a lock-screen action appears.
- [ ] Given I tap the action on a locked device, when it opens, then it goes straight to the classification screen.
- [ ] Given I choose Work, when I try to complete, then a non-empty business reason is required first.
- [ ] Given I leave the business reason blank, when I save, then the trip stays `pending_business_reason` and is not exported.

**Owner agent:** android-engineer  ·  **Tasks:** T-003  ·  **Status:** Backlog

---

### US-003 · Capture odometer with OCR + manual fallback   [MVP] [5]
**As a** driver **I want** to photograph my odometer and have the number read automatically
**so that** my mileage has backup evidence without typing.

**Acceptance criteria**
- [ ] Given I capture a photo, when OCR confidence ≥80% and a valid `\d{5,6}` value is found, then the verified value is saved with the trip.
- [ ] Given OCR fails or is <80%, when capture completes, then a manual entry fallback appears and the trip can still be saved.
- [ ] Given `Save odometer photos` is OFF, when OCR succeeds and I confirm, then the raw image is deleted.

**Owner agent:** ml-ocr-specialist (+ android-engineer)  ·  **Tasks:** T-005  ·  **Status:** Backlog

---

### US-004 · Reliable trip-end without false stops   [MVP] [5]
**As a** driver **I want** trips to end only when I've really stopped **so that** traffic lights
don't split one trip into many.

**Acceptance criteria**
- [ ] Given I stop at a light, when I pause briefly, then the trip does not end.
- [ ] Given I park, when inactivity lasts 3 min (or unstable signal 2 min, or I confirm a stop), then the trip is marked completed.

**Owner agent:** geo-sensors-specialist  ·  **Tasks:** T-004  ·  **Status:** Backlog

---

### US-005 · Trips persist and survive restarts   [MVP] [3]
**As a** driver **I want** my trips saved locally and not duplicated **so that** I never lose or
double-count a drive.

**Acceptance criteria**
- [ ] Given a trip is saved, when the app or service restarts, then the active/pending trip is restored and not duplicated.
- [ ] Given the app is closed, when I reopen it, then my full trip history is intact.

**Owner agent:** android-engineer  ·  **Tasks:** T-006  ·  **Status:** Backlog

---

### US-006 · Review and export trip history to CSV   [MVP] [3]
**As a** business owner **I want** to see my trips and export them to CSV **so that** I can submit
them for claims.

**Acceptance criteria**
- [ ] Given saved trips, when I open history, then I see them with status clearly marked.
- [ ] Given I export, when the CSV is written to Downloads, then it is `mileage_trips_YYYYMMDD_HHMMSS.csv`, UTF-8, with the fixed column order, completed trips only, no blank Work business reasons.

**Owner agent:** android-engineer  ·  **Tasks:** T-007  ·  **Status:** Backlog

---

### US-007 · Optional Bluetooth vehicle trigger   [MVP-optional] [3]
**As a** single-vehicle driver **I want** to nominate my car's Bluetooth as the trip trigger
**so that** detection starts the instant I turn on the ignition.

**Acceptance criteria**
- [ ] Given Bluetooth trigger is off by default, when I enable it, then I can scan, pair, and save one trusted vehicle.
- [ ] Given my trusted device connects, when ignition turns on, then a trip starts; when Bluetooth is unavailable, then the standard automatic path still works.

**Owner agent:** geo-sensors-specialist (+ android-engineer)  ·  **Tasks:** T-002  ·  **Status:** Backlog

---

### US-008 · Test detection on emulated GPS routes   [Cross-cutting] [3]
**As a** developer **I want** to replay GPS routes through the Android emulator **so that** I can
verify trip-start, distance, and false-stop logic without physically driving.

**Acceptance criteria**
- [ ] Given the emulator is running, when I replay a normal-trip route, then a trip starts, accumulates distance, and ends after the 3-min stop rule.
- [ ] Given a stop-start-traffic route (brief §10 #2), when replayed, then brief pauses do NOT falsely end the trip.
- [ ] Given route fixtures, when stored in the repo, then they are repeatable in CI/local runs.

**Owner agent:** compliance-qa-specialist (+ geo-sensors-specialist)  ·  **Tasks:** T-015  ·  **Status:** In progress — env wired; blocked on installing a bootable system image (no cmdline-tools / no system image yet)
