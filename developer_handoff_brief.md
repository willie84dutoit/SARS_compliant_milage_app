# Developer Handoff Brief

## 1. Project Goal
Build a local-first Android MVP that detects when a user is in a vehicle, asks for trip classification (`Work` or `Private`), captures odometer backup evidence, saves the trip locally, and allows export for sharing.

## 2. What the MVP Must Do
The first release must deliver the following core features:
1. Detect vehicle entry in the background.
2. Prompt the user to classify the trip as `Work` or `Private`.
3. Capture an odometer photo as backup verification.
4. Detect trip end without obvious false stop events.
5. Save all trip data locally in a database.
6. Export trip history to CSV.
7. Support basic trip history review in the app.

## 3. What Is Out of Scope for v1
The following are not required in the first version:
- Full cloud account sync
- Firebase authentication
- Multi-platform support (iOS, Huawei)
- Advanced analytics or dashboard reporting
- Manual trip editing or advanced correction tools
- Real-time sharing to WhatsApp or email from inside the app
- Full OCR accuracy tuning beyond basic temporary verification
- API integration for accounting or payroll systems (Phase 2 / post-MVP)

## 4. Technical Stack Decision for v1
Use the simplest reliable stack that can deliver a working Android MVP in two weeks:
- Language: Kotlin
- UI: Jetpack Compose
- Architecture: MVVM
- Local storage: Room database
- Background detection: ActivityRecognition + foreground/background service handling
- Camera: CameraX
- Background jobs: WorkManager
- OCR / text recognition: temporary on-device OCR for odometer verification using Google ML Kit Text Recognition (Android)
- Export: CSV generation to the Android Downloads folder
- Dependency injection: Hilt

This stack is preferred because it is native, fast to implement, and suitable for Android-only deployment.

## 5. Functional Requirements

### 5.1 Vehicle Detection
- The app must detect that the user has entered a vehicle.
- Detection must run in the background with low-power behavior.
- The app must avoid obvious false positives during normal movement.
- If confidence is low, the app should retry silently rather than interrupt the user.
- In v1, the detection methods are automatic and not user-selectable. The app should always enable the built-in detection path by default.

#### Detection Method Policy
- Primary method: Android ActivityRecognition transition detection (`IN_VEHICLE`) to detect trip start.
- Optional advanced method for single-vehicle users: Bluetooth head-unit detection. The app should scan nearby Bluetooth devices, present them to the user, and allow the user to select and save one device as their trusted work vehicle / vehicle trigger.
- Bluetooth workflow for v1: scan for nearby devices on demand, show a list of discovered devices, require the normal Android pairing flow if the device is not already paired, let the user choose one, save the selected device as the trusted vehicle, and use that saved device only for the trip-start trigger.
- Bluetooth behavior for v1: only one trusted vehicle device may be saved at a time; if the user changes it, the previous device is replaced and no other device is used automatically.
- Bluetooth detection must be off by default for v1. It is an advanced optional feature, not the standard detection path.
- Secondary method: GPS/location sampling after classification to measure distance, confirm movement, and support trip-end logic.
- Fallback method: manual confirmation when ActivityRecognition, Bluetooth, location, or notification access is unavailable or restricted by the OS.
- Fixed priority order in v1 for the default experience: 1) ActivityRecognition start detection, 2) GPS movement confirmation, 3) manual confirmation fallback.
- If the user enables the Bluetooth option and saves a trusted work vehicle device, the app should wake up when that connection is made, prefer that saved vehicle trigger when available, and still fall back to the standard automatic path if Bluetooth is unavailable, disconnected, or unsupported.
- The default experience should remain automatic and not require the user to choose a detection mode. The Bluetooth trigger is an optional advanced setting for users who specifically want that single-vehicle setup.

### 5.2 Trip Classification
- On detection, the app must present a simple prompt: `Work` or `Private`.
- The wake-up notification used to ask the user to classify the trip must be independent of the detection method. It should appear whenever the app detects a trip-start event, regardless of whether that event came from ActivityRecognition, Bluetooth, or manual fallback.
- The notification should be a standard Android notification with at least one actionable button to open the app and confirm the trip classification.
- Notification channel for v1: use a dedicated `mileage_tracker_trip_alerts` channel with importance `HIGH` and a visible notification icon.
- The notification action must be usable from the lock screen if the device allows it, and the app should aim to support lock-screen interaction for the classification flow.
- If the user taps the notification action on a locked device, the app should wake the screen and open the classification screen directly, not just the home screen.
- If the device does not allow lock-screen interaction, the app must still open the app normally after the user taps the notification.
- The classification must be stored with the trip record.
- If the trip is classified as `Work`, the user must enter a business reason for the trip before the trip can be saved as completed.
- The business reason field must be mandatory for `Work` trips, non-empty, and stored exactly as entered by the user for logbook compliance.

### 5.3 Odometer Backup
- The app must allow the user to capture a photo of the odometer at trip start and/or trip end.
- The photo is used for temporary on-device OCR verification.
- The app should provide a camera setting option: `Save odometer photos` = on/off.
- OCR failure behavior for v1: if OCR confidence is below 80% or no valid odometer text is found, the app must show a manual entry fallback and allow the trip to continue without blocking completion.
- Default behavior for v1: the setting should default to on, so photos are saved by default unless the user switches it off.
- If the setting is off, the image should be deleted after OCR succeeds and the user confirms the result.
- If the setting is on, the photo may be saved as configured by the user.
- The verified odometer value should be retained as part of the trip record if OCR succeeds.

### 5.4 Trip End Detection
- The app must detect when the trip has ended.
- The stop logic must avoid false stops from brief pauses, slow traffic, or traffic lights.
- MVP default rule: treat a trip as ended only after confirmed inactivity, a lost/unstable activity signal, or a manual stop confirmation by the user.
- The trip must be marked as completed after the stop event.
- The stop-detection path is also automatic in v1; the user does not choose a separate stop-detection mode.

### 5.5 Local Storage
- Every trip must be stored locally before any export or sync.
- The app must persist trip history even when the app is closed.

### 5.6 Export
- The app must export trip records to CSV.
- CSV should include at least:
  - trip ID
  - classification
  - start time
  - end time
  - start odometer
  - end odometer
  - trip distance
  - verified odometer value if available
  - business reason (required when classification is `Work`)
- Export destination for v1: the Android Downloads folder.
- CSV file naming for v1: `mileage_trips_YYYYMMDD_HHMMSS.csv`.
- ~~CSV format for v1: UTF-8 plain text, comma-separated columns in this fixed order: `tripId,classification,startTimestamp,endTimestamp,startOdometerKm,endOdometerKm,verifiedOdometerKm,distanceKm,businessReason,status`.~~
  <!-- AMENDED 2026-06-25 (session 16, user-directed): appended startDateTime,endDateTime (SAST) after status. -->
- CSV format for v1: UTF-8 plain text, comma-separated columns in this fixed order: `tripId,classification,startTimestamp,endTimestamp,startOdometerKm,endOdometerKm,verifiedOdometerKm,distanceKm,businessReason,status,startDateTime,endDateTime`. The two trailing columns `startDateTime`/`endDateTime` are the millis timestamps formatted `yyyy-MM-dd HH:mm:ss` in `Africa/Johannesburg` (SAST), produced at export time only (stored values stay epoch-millis).
- Export rules for v1: export only completed trips; pending or incomplete trips are excluded; the export must overwrite any existing file with the same timestamped name and must not include blank business reasons for `Work` trips.

### 5.7 Default MVP Rules to Lock In
- A trip is considered started at the first confirmed vehicle-entry event.
- A trip is considered ended after the app confirms the vehicle has stopped and remained inactive for a short period, rather than at a brief pause.
- If the trip is classified as `Work`, the user must enter a business reason before the trip can be saved.
- The business reason should be a short free-text field, required for compliance and export.
- If the user leaves the business reason blank for a `Work` trip, the trip must stay pending and the user must be prompted to complete it.
- Distance should be treated as the GPS-based trip length, while the verified odometer value is retained as supporting evidence.
- Raw odometer images are temporary unless the user explicitly enables photo saving in settings; if the setting is off, the image must be deleted after OCR succeeds and the user confirms the result.

### 5.8 Trip History
- The user must be able to view saved trips in the app.
- The user must be able to export the visible trip list.

### 5.9 Operational Rules and Default Thresholds for v1
These defaults must be implemented as hard rules in the first release unless a later test cycle proves a different behavior is required:
- Start detection: treat the trip as started at the first confirmed `IN_VEHICLE` event. If activity confidence is low, retry silently for up to 30 seconds before asking the user.
- Stop detection: treat the trip as ended only after 3 minutes of confirmed inactivity, or 2 minutes of lost/unstable activity signal, or the user manually confirms the stop.
- Threshold values for v1 are fixed at: start confidence threshold 70%, stop inactivity threshold 3 minutes, unstable-signal threshold 2 minutes, prompt timeout 30 seconds.
- Detection mode is automatic by default in v1, but the app may expose an optional Bluetooth vehicle-trigger setting for supported single-vehicle users.
- If Bluetooth detection is enabled, it should be treated as an optional start trigger, not as the only detection path.
- Prompt timing: show the classification prompt within 5 seconds of the start event, and keep the prompt visible long enough for the user to respond without losing the trip.
- Work-trip compliance: a `Work` trip is not considered complete until the business reason field is non-empty. If the field is blank, the trip must remain in `pending_business_reason` state and must not be exported as complete.
- OCR fallback: if OCR fails, the user must be allowed to enter the odometer value manually. The trip must still be saved even when OCR is unavailable.
- Photo retention: default the `Save odometer photos` setting to `on`. If the setting is `off`, delete the raw image immediately after OCR success and user confirmation, and do not keep the image in the local history.
- Recovery behavior: on app restart, the app must restore any previously active or pending trip from Room and must never create a duplicate trip for the same session.
- Export behavior: export only completed trips by default; pending or incomplete trips should be clearly marked in the UI and excluded from the CSV export until the user completes them.

## 6. Data Model
Use the following minimum data structure for every saved trip:

### 6.1 Architecture Boundaries for v1
- UI layer: Compose screens and view models only. No database or service logic should live directly in the UI layer.
- Domain layer: trip state logic, classification rules, OCR result handling, and export rules.
- Data layer: Room database, DAO interfaces, repository implementations, and local file export helpers.
- Service layer: foreground service, ActivityRecognition callback handling, and notification triggers.
- The developer should keep repository logic separate from the ViewModel and should not place Room calls directly inside Compose screens.

### Trip Entity
- id: string
- classification: `work` | `private`
- startTimestamp: long
- endTimestamp: long
- startOdometerKm: double
- endOdometerKm: double
- verifiedOdometerKm: double?
- distanceKm: double
- businessReason: string?
- startLatitude: double? 
- startLongitude: double?
- endLatitude: double?
- endLongitude: double?
- status: `active` | `completed` | `pending_business_reason` | `pending_ocr`
- photoRetention: `temporary` | `saved`
- createdAt: long
- updatedAt: long

### Optional Supporting Table
- trip_photo
  - id
  - tripId
  - imageUri
  - capturedAt

## 7. Recommended App Screens
1. Setup / Permissions screen
   - first-run setup
   - permission requests
   - clear explanation of why location and background access are needed
2. Home / Status screen
   - current app status
   - last detected trip
   - quick export action
3. Trip Classification prompt
   - `Work` / `Private`
4. Odometer Photo Capture screen
   - capture or choose image
   - temporary OCR verification
5. Trip History screen
   - list of saved trips
6. Export screen
   - export current trips to CSV to Downloads
7. Settings screen
   - permissions
   - export path
   - background behavior
   - `Save odometer photos` toggle
   - optional Bluetooth vehicle trigger (disabled by default; scan nearby devices and save one trusted work vehicle)
   - no mandatory user-selectable detection-mode option in v1

## 8. Android Permission and OS Requirements
The MVP should target recent Android versions and must handle the standard Android background permission model.
Required behavior:
- Target Android 10 and above for v1. If the device is below Android 10, the app should show an unsupported-device message and stop background tracking gracefully.
- Permission matrix for v1:
  - Android 10: `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `CAMERA`
  - Android 11: `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `CAMERA`
  - Android 12: `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `CAMERA`
  - Android 13+: `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `CAMERA`, `POST_NOTIFICATIONS`
- Request the minimum required permissions at startup: `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` (Android 10+), `CAMERA`, and `POST_NOTIFICATIONS` (Android 13+ if notifications are used for trip prompts).
- Use a foreground service whenever background detection or trip tracking is active, and show a persistent notification while tracking is running.
- Show a first-run setup screen that explains why background activity, location, and camera access are needed, and why the app may continue in limited mode if the user denies some permissions.
- If permissions are denied, continue in limited mode with clear warnings, allow manual confirmation, and never block the user from opening the app or viewing history.
- If the user disables background location or notification access, the app must degrade gracefully and fall back to manual start/stop confirmation instead of crashing.

## 9. Acceptance Criteria for v1
The app is considered complete when all of the following are true:
- The app starts in the background and detects a vehicle entry.
- The user can classify the trip as `Work` or `Private`.
- If the trip is marked `Work`, the user must provide a business reason before the trip is marked complete.
- The user can capture an odometer photo for temporary OCR verification.
- The app saves the verified odometer value after OCR succeeds, and allows manual override if OCR fails.
- The app respects the `Save odometer photos` setting: on = save image, off = delete image after OCR succeeds and the user confirms the result.
- The trip is saved locally in Room, and the same trip is not duplicated after app restart or service restart.
- The trip can be viewed from the history screen and exported to CSV without including incomplete `Work` trips.
- The app does not show obvious false stop detections in normal driving conditions.
- The app works on recent Android devices and is publishable to Google Play in principle.
- If Bluetooth vehicle detection is enabled and the user has saved a trusted work vehicle, it must start trips reliably for that single-vehicle setup and fall back automatically to the standard detection path when Bluetooth is unavailable.

## 10. Test Cases the Developer Should Implement
The developer must test at least the following scenarios:
1. Normal trip start and trip end, with the stop threshold confirmed at 3 minutes of inactivity.
2. Stop-start traffic or traffic lights, to confirm that brief pauses do not falsely end the trip.
3. Permission denied during startup, including limited mode and manual confirmation fallback.
4. App backgrounded and resumed, including service restart after OS kill.
5. Local storage persistence after app restart, including duplicate-trip prevention.
6. Export to CSV from stored data, including file naming, column order, and exclusion of incomplete `Work` trips.
7. OCR success and OCR failure, including manual fallback entry.
8. Low battery / restricted background behavior, including foreground-service fallback behavior.
9. User cancels the classification flow or leaves the business reason blank for a `Work` trip.
10. Bluetooth vehicle trigger enabled on a supported single-vehicle setup, including fallback to the standard path when Bluetooth is unavailable.

## 11. Two-Week Delivery Plan

### Week 1
- Set up Android project with Kotlin, Compose, Room, and Hilt.
- Implement basic app shell and trip history screen.
- Implement vehicle detection hook and classification prompt.
- Implement local database and trip save logic.

### Week 2
- Implement odometer photo capture.
- Implement trip end detection and trip completion flow.
- Implement CSV export.
- Perform bug fixes, reliability tests, and MVP polish.

## 12. Final Developer Instruction
The developer should treat this as the minimum usable v1 specification.
If any feature is unclear, the safest default is to keep the implementation simple, local-first, and Android-native.

## 13. Open Risks to Watch
- ActivityRecognition reliability may vary by device.
- Background restrictions on Android may affect detection behavior.
- False positives and false stop detection are the highest risk areas.
- Battery optimization must be considered, but the MVP should prioritize working detection over perfect battery efficiency.

## 14. Post-MVP Expansion
See the separate document [post_mvp_api_plan.md](post_mvp_api_plan.md) for the future API and accounting-integration phase after the MVP is complete.

