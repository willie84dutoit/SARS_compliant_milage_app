# Full Implementation Plan — every task broken into literal, do-this-exact-thing steps

> **Read this if you are building this app.** Every `T-###` task below is broken into numbered
> micro-steps (`T-001.1`, `T-001.2`, ...). Each micro-step has three parts: the action, a **How**
> naming exactly which API/class/method/command/config value to use and what to set it to, and a
> **Verify** stating exactly how to confirm that step actually worked — not a finished
> implementation, but nothing left to decide or guess either. Where a step produces code, follow
> **TDD**: write the test first, run it and confirm it fails (RED), write the minimum code to pass
> it, run it again and confirm it passes (GREEN) — the Verify line for those steps *is* that
> red/green check. No open decisions remain — every constant and API choice below (GPS noise
> floor, OCR confidence scoring, analytics event names, etc.) is a final, decided value.
>
> Sources: `developer_handoff_brief.md`, `automated_mileage_tracker_spec.md`,
> `post_mvp_api_plan.md`, `publisheing guide.md`, `team/blueprints/T-001-android-architecture-blueprint.md`,
> `team/blueprints/T-010-backend-cost-model.md`. No-delete rule applies to this file — strike
> through, don't remove, when a value changes later.

---

# T-001 · Scaffold the Android project

### T-001.1 · Install the toolchain
**How:** Install JDK 17 (Temurin). Install the Android SDK (Android Studio, or standalone
`cmdline-tools`). Set `ANDROID_HOME` and add `platform-tools` to `PATH`.
**Verify:** `java -version` reports 17; `adb --version` runs without error.

### T-001.2 · Create the Gradle project skeleton
**How:** Create `settings.gradle.kts` (module `:app`, `google()`/`mavenCentral()` repositories) and
a root `build.gradle.kts` declaring plugin versions: Android Gradle Plugin 8.5.2, Kotlin 1.9.24,
Compose compiler plugin 1.9.24, Hilt 2.51.1, KSP 1.9.24-1.0.20. Generate the wrapper:
`gradle wrapper --gradle-version 8.7`.
**Verify:** `./gradlew --version` reports Gradle 8.7 with no errors.

### T-001.3 · Configure `app/build.gradle.kts`
**How:** Apply the `com.android.application`, `org.jetbrains.kotlin.android`,
`org.jetbrains.kotlin.plugin.compose`, `com.google.dagger.hilt.android`, and
`com.google.devtools.ksp` plugins. Set `namespace = "com.mileagetracker.app"`, `minSdk = 29`,
`targetSdk = 34`, `compileSdk = 34`, `buildFeatures { compose = true }`. Add dependencies pinned to
these exact versions: Compose BOM `2024.06.00`, Room `2.6.1` (+ KSP compiler), Hilt `2.51.1` (+ KSP
compiler) with `hilt-navigation-compose 1.2.0`, DataStore Preferences `1.1.1`, CameraX
(`camera-core`/`camera-camera2`/`camera-lifecycle`/`camera-view`) `1.3.4`, ML Kit
`text-recognition` `16.0.1`, `play-services-location` `21.3.0`,
`kotlinx-coroutines-play-services` `1.8.1`, WorkManager `2.9.1`.
**Verify:** `./gradlew :app:dependencies` resolves every dependency with zero "could not find" errors.

### T-001.4 · Declare the manifest
**How:** Declare permissions `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `CAMERA`,
`POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`.
Declare `TripTrackingForegroundService` with `android:foregroundServiceType="location"`. Declare
`ActivityTransitionReceiver` as a non-exported `<receiver>`. Set `MainActivity`
`android:showWhenLocked="true"`.
**Verify:** `./gradlew :app:assembleDebug` (run together with T-001.5) succeeds and `aapt2 dump
badging app-debug.apk` lists every declared permission.

### T-001.5 · Build and run the empty shell
**How:** Create an `Application` class annotated `@HiltAndroidApp`. Create `MainActivity`
annotated `@AndroidEntryPoint` with a placeholder Compose screen. Run `./gradlew :app:assembleDebug`,
then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
**Verify:** the app opens on a device/emulator and shows the placeholder screen without crashing.

> Everything from here (T-002–T-008) builds inside this shell, following the dependency direction
> already decided in the architecture blueprint: `ui → domain`, `data → domain`,
> `service → domain + data` — nothing imports `ui`.

---

# T-002 · Vehicle detection (ActivityRecognition)

### T-002.1 · Request the runtime permission
**How:** Use `ActivityResultContracts.RequestMultiplePermissions()` to request
`ACTIVITY_RECOGNITION`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `CAMERA`, and (API 33+)
`POST_NOTIFICATIONS` from `SetupPermissionsScreen`. On denial, set a `limitedMode` flag rather than
blocking navigation.
**Verify:** deny one permission on a test device and confirm the app still opens to Home, with a
visible limited-mode indicator, instead of crashing or getting stuck on Setup.

### T-002.2 · Specify the domain start-event types
**How (TDD):** Write a test asserting the state machine reacts correctly to a confidence-≥70 event
within the retry window. Define a sealed type with exactly three cases: a confident entry (carrying
the confidence percent), a retry-exhausted case (carrying the best confidence percent seen), and a
manual-start case. Implement just enough of the state machine to make the test pass.
**Verify:** the test fails before the sealed type/state-machine method exist (RED), then passes
once they do (GREEN).

### T-002.3 · Register the always-on Transition API trigger
**How:** Build an `ActivityTransitionRequest` with two `ActivityTransition` entries — `IN_VEHICLE`
+ `ACTIVITY_TRANSITION_ENTER`, and `IN_VEHICLE` + `ACTIVITY_TRANSITION_EXIT`. Register it via
`ActivityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent)`, where the
`PendingIntent` targets a `BroadcastReceiver`. In that receiver, call
`ActivityTransitionResult.extractResult(intent)` and, for each `ENTER` event matching
`IN_VEHICLE`, hand off to T-002.4 — do not start a trip directly from this receiver.
**Verify:** with a Robolectric/fake-intent test, confirm the receiver calls the T-002.4 handoff
exactly once per `ENTER` event and zero times for an `EXIT` event.

### T-002.4 · Specify the confidence-acquisition window
**How:** The Transition API does not carry a confidence value on its events, so confidence has to
come from a second, short-lived subscription. On every `ENTER` event from T-002.3, additionally
register `ActivityRecognitionClient.requestActivityUpdates()` at a 5-second interval. From each
delivered `ActivityRecognitionResult`, read the `IN_VEHICLE` entry's `DetectedActivity.getConfidence()`
(0–100) and track the **maximum** value seen so far in the window (not the latest reading — one
high reading should count, not be erased by a lower one after it). Run a 30-second timer starting
from the `ENTER` event. If the tracked maximum reaches **70** before the timer elapses, unregister
the updates and fire the confident-entry event with that confidence value. If the timer elapses
first, unregister the updates and fire the retry-exhausted event with whatever maximum was seen —
this still shows the classification prompt, just flagged low-confidence for telemetry.
**Verify:** with a `TestScope`/virtual-time test: feed confidence readings 50 then 75 within the
window — confirm the confident-entry event fires with value 75 and the 30s timer is cancelled. Feed
only readings below 70 for the full 30s — confirm the retry-exhausted event fires at exactly 30s,
not before or after.

### T-002.5 · Specify the optional Bluetooth trigger (off by default)
**How:** Add a disabled-by-default toggle + "Scan" button on `SettingsScreen`. On scan, call
`BluetoothAdapter.startDiscovery()` and collect devices from the `ACTION_FOUND` broadcast. On
selection, if not already bonded, call `BluetoothDevice.createBond()`; once bonded, persist the
device's MAC address (one saved device at a time — saving a new one replaces the old). Register a
receiver for `ACTION_ACL_CONNECTED`; when the connecting device's address matches the saved one,
route to the same manual-start entry point as T-002.1's fallback.
**Verify:** unit test the save/replace logic (saving device B after device A leaves only B
persisted); on a real device pair, confirm connecting the saved device fires manual-start and
disconnecting it does not break the standard Transition-API path.

---

# T-003 · Trip classification flow (Work/Private)

### T-003.1 · Create the notification channel
**How:** Create a `NotificationChannel` with id `mileage_tracker_trip_alerts` and
`NotificationManager.IMPORTANCE_HIGH`, registered once in `Application.onCreate()`.
**Verify:** in device Settings → Apps → Mileage Tracker → Notifications, the channel shows at High
importance.

### T-003.2 · Specify the business-reason validation rule
**How (TDD):** Write tests: a blank reason on a Work trip must fail validation; a non-blank reason
must pass. Implement a single pure validation function — the only place this check happens; no
screen inlines its own blank-check.
**Verify:** both tests fail before the function exists, both pass after.

### T-003.3 · Specify the lock-screen actionable notification
**How:** Build the notification with `NotificationCompat.Builder` on the T-003.1 channel, set
`setVisibility(NotificationCompat.VISIBILITY_PUBLIC)`, and add two `NotificationCompat.Action`s
("Work", "Private"), each carrying a `PendingIntent` to an activity with the trip id and chosen
classification as extras, `FLAG_ACTIVITY_NEW_TASK`, and `setShowWhenLocked(true)`/
`setTurnScreenOn(true)` so a lock-screen tap opens the classification screen directly.
**Verify:** on a locked emulator, tap an action and confirm the classification screen opens
directly — not the launcher, not the home screen first.

### T-003.4 · Wire the pending-business-reason gate
**How:** On submit, run the value through T-003.2's validation. On failure, set the trip's status
to `pending_business_reason`. On success, proceed toward `completed` (or `pending_ocr` if T-005/
T-006's odometer step hasn't resolved yet).
**Verify:** integration test — submitting blank leaves the row's status as
`pending_business_reason`; submitting non-blank moves it past that state.

---

# T-004 · GPS distance tracking + trip-end detection

### T-004.1 · Configure the location request
**How:** Build a `LocationRequest` with `Priority.PRIORITY_HIGH_ACCURACY` and
`setMinUpdateDistanceMeters(10f)` (locked — do not change), registered via
`FusedLocationProviderClient.requestLocationUpdates()`.
**Verify:** on a device/emulator with GPS mocking, confirm callbacks arrive only after ≥10m of
simulated movement, not on every tick.

### T-004.2 · Specify the distance-accumulation function
**How (TDD):** Write a test asserting the Haversine distance between two points exactly 1° of
latitude apart is ~111,195 meters. Implement a pure Haversine function (no Android imports).
**Verify:** the test fails before the function exists, passes after, within a small tolerance
(e.g. ±50m) of the reference value.

### T-004.3 · Specify the GPS noise floor
**How:** Before accepting any location update, compute the Haversine distance from the last
*accepted* point. If the delta is **below 8.0 meters**, discard it — no distance added, no timer
reset, anchor unchanged. If 8.0m or above, accept it: add to distance, reset the inactivity timer
(T-004.4), move the anchor. 8m sits strictly below the locked 10m provider filter (so it does real
filtering work) and above typical stationary drift (3–7m), below real movement (~8m in under 3s at
10 km/h).
**Verify:** test a 5m delta → no distance added, timer not reset; test a 12m delta → distance
added, timer reset.

### T-004.4 · Specify the two stop timers
**How:** Run two independent timers. The **3-minute inactivity timer** resets on every accepted
(≥8m) delta; on elapsing, fires a confirmed-inactivity stop event. The **2-minute unstable-signal
timer** resets on *any* delivered callback at all, accepted or not; it fires only if updates stop
arriving entirely, as a separate unstable-signal stop event. A manual "End trip" button fires a
third, manual stop event. All three route to the same state-machine entry point.
**Verify:** virtual-time test — 3 minutes of sub-8m deltas only → confirmed-inactivity fires at
exactly 180s; 2 minutes of zero callbacks → unstable-signal fires at exactly 120s; a single ≥8m
delta at 179s resets the inactivity timer and confirmed-inactivity does not fire at 180s.

### T-004.5 · Specify Room write batching for distance
**How:** Accumulate distance deltas in memory. Flush to the database on a **15-second** timer
while the trip is active, using one `UPDATE ... SET distance_km = distance_km + :delta` query.
Also flush unconditionally on every stop event and on service teardown.
**Verify:** integration test — accumulate distance, simulate a kill before the 15s tick, call the
unconditional flush path, confirm the database's stored distance reflects the full accumulated
amount, not zero.

---

# T-005 · Odometer OCR capture + manual fallback

### T-005.1 · Specify the capture screen
**How:** Bind a CameraX `ImageCapture` use case to the screen's lifecycle. On capture, convert the
`ImageProxy` to an upright `Bitmap`, applying `imageInfo.rotationDegrees` before handing the bitmap
onward.
**Verify:** capture a photo with the device rotated 90°/180°/270° and confirm the resulting bitmap
displays upright in all three cases.

### T-005.2 · Specify the digit-extraction regex
**How (TDD):** Write tests: a 6-digit string matches; a 4-digit string does not; a clock-like
string (`"12:45"`) does not. Implement the match using `\b\d{5,6}\b` applied to each recognized
text element.
**Verify:** all three tests fail before the regex exists, all three pass after.

### T-005.3 · Specify the OCR client and its confidence score
**How:** Use `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` (Latin script,
regardless of device locale — odometer digits are always Latin numerals). Run every captured
bitmap through it, extract every element matching T-005.2's regex; if none match, the result is
"no text found." ML Kit's on-device API has no per-element confidence field, so for every matching
candidate compute four 0–100 sub-scores and combine with these exact weights:
- **Digit-count match**, weight 0.25 — 6 digits scores 100, 5 digits scores 80.
- **Isolation from other recognized text**, weight 0.35 — normalized distance to the nearest other
  recognized element anywhere in the frame (farther = higher score); this is what excludes a clock
  or temperature reading near the digits.
- **Vertical position in frame**, weight 0.15 — closeness to the image's vertical center.
- **Structural purity**, weight 0.25 — 100 if the candidate's text line contains none of
  `: ° AM PM`, else 0.

Take the highest-scoring candidate. **80 or above → "confident," and the score is the confidence
value carried forward** (no second hidden threshold). Below 80 → "low confidence."
**Verify:** an isolated 6-digit candidate scores ≥80 in a unit test; the same candidate placed
adjacent to a `"14:32"`-style element scores below 80 in the same test harness.

### T-005.4 · Specify the sanity check before trusting any OCR result
**How:** Even on a "confident" result, reject it if the parsed value is less than the trip's
starting odometer reading. On rejection, fall through to manual entry.
**Verify:** test feeding a confident result lower than `startOdometerKm` and confirm it routes to
manual entry, not to a direct save.

### T-005.5 · Specify the manual fallback
**How:** On "low confidence," "no text found," or a T-005.4 rejection, show a numeric text field.
The submitted value goes through the exact same "save verified odometer" call as a successful OCR
result.
**Verify:** confirm both the OCR-success path and the manual-entry path land on the same
repository method call, by asserting the trip's `verifiedOdometerKm` is set correctly via either
path in two separate tests.

### T-005.6 · Specify photo retention
**How:** Read the photo-retention setting at trip start and snapshot it onto the trip record. After
OCR finishes: "saved" → write a photo record and keep the file; "temporary" → delete the file
immediately after OCR success + user confirmation, never writing a photo record for that capture.
**Verify:** with retention set to "temporary," confirm the photo file no longer exists on disk
after confirmation and the photo table has zero rows for that trip.

---

# T-006 · Local persistence + recovery (Room)

### T-006.1 · Specify the `Trip` table
**How:** Create a Room entity with these exact columns:

| Field | Type | Nullable | Notes |
|---|---|---|---|
| id | String (PK) | No | UUIDv4, generated once at trip-start |
| classification | String | No | `"work"` \| `"private"` |
| startTimestamp | Long | No | epoch millis |
| endTimestamp | Long | No | while active, set equal to startTimestamp as a sentinel |
| startOdometerKm | Double | No | |
| endOdometerKm | Double | No | |
| verifiedOdometerKm | Double | Yes | set only on OCR-confident or manual confirm |
| distanceKm | Double | No | |
| businessReason | String | Yes | required non-blank before `completed` for Work trips |
| startLatitude/Longitude, endLatitude/Longitude | Double | Yes | null if location permission denied |
| status | String | No | `active` \| `completed` \| `pending_business_reason` \| `pending_ocr` |
| photoRetention | String | No | `temporary` \| `saved` |
| createdAt, updatedAt | Long | No | `updatedAt` set explicitly by the repository on every write |
| signatureBase64 | String | Yes | T-008 |
| signingKeyId | String | Yes | T-008 |

Add an index on `status`. Also create a `TripPhoto` table: `id`, `tripId` (foreign key, indexed,
cascade delete), `imageUri`, `capturedAt`.
**Verify:** `exportSchema = true` (T-006.3) produces a schema JSON listing every column above with
the correct nullability.

### T-006.2 · Specify the DAO queries
**How:** Provide: insert, update, get-by-id; a single query returning the trip whose status is
`active`, `pending_business_reason`, or `pending_ocr` (both one-shot and observable) — one call
site for all three in-progress states; an observable stream of completed trips newest-first; a
one-shot read of completed trips for export; an update-status query; an update-business-reason
query; the T-004.5 distance-increment query; a delete.
**Verify:** an instrumented test against an in-memory database exercises every query at least once
and gets the expected row(s) back.

### T-006.3 · Specify the database class
**How:** A Room database, `exportSchema = true`, version 1, containing the two T-006.1 entities,
exposing both DAOs.
**Verify:** `./gradlew :app:kspDebugKotlin` generates the schema JSON under `app/schemas/` with no
errors.

### T-006.4 · Specify the no-duplicate-trip recovery mechanism
**How (TDD):** Write an integration test (in-memory Room): insert a trip with status `active`;
query for the in-progress trip and confirm it's returned; construct a fresh DAO instance against
the same database (simulating a restart) and query again, confirming still exactly one row, same
id. Implement: every place that could register a new trip-start must query for an in-progress trip
**first**, before registering any new detection request — insert only if that query returns
nothing.
**Verify:** the test fails before the recovery check exists, passes after; confirm manually by
force-stopping the app mid-trip and reopening it — the same trip resumes, no duplicate appears in
history.

---

# T-007 · CSV export to Downloads

### T-007.1 · Specify the export filter
**How (TDD):** Write tests: a completed Work trip with a non-blank reason is included; a
`pending_business_reason` trip is excluded; a completed trip with a blank reason is excluded
(belt-and-braces). Implement: keep only `status == completed`, then drop any row where
classification is Work and the reason is blank.
**Verify:** all three tests fail before the filter exists, all three pass after.

### T-007.2 · Specify the fixed column order
**How:** Every export row has exactly these columns, in this order, no others:
`tripId, classification, startTimestamp, endTimestamp, startOdometerKm, endOdometerKm, verifiedOdometerKm, distanceKm, businessReason, status`.
**Verify:** a unit test asserts the header line of a generated export matches this string exactly.

### T-007.3 · Specify the file write
**How:** Use `MediaStore.Downloads` via `ContentResolver.insert()` (not a raw file path — required
for Android 10+ scoped storage). Filename: `mileage_trips_YYYYMMDD_HHMMSS.csv`. Encoding: UTF-8.
**Verify:** instrumented test — write a known row set, read the file back through the returned
URI, confirm header + rows match byte-for-byte, including correct UTF-8 encoding.

---

# T-008 · Cryptographic trip signing — design decided, build steps below

### T-008.1 · Specify key generation
**How:** Generate an EC key pair on curve `secp256r1` in the Android Keystore, purpose "sign,"
digest SHA-256, attempting StrongBox first and falling back to TEE if unavailable. Generate it
lazily on first trip completion, not at install; reuse the same alias afterward.
**Verify:** on a StrongBox-capable device, confirm the generated key reports StrongBox-backed; on
one without it, confirm generation still succeeds via the TEE fallback rather than crashing.

### T-008.2 · Specify the canonical signed payload
**How:** Serialize exactly these fields, in this exact order: id, classification (lowercase),
startTimestamp, endTimestamp (epoch millis), startOdometerKm, endOdometerKm, verifiedOdometerKm
(2dp strings), distanceKm (2dp string), businessReason (string or explicit null), status
(lowercase), prevTail, tripSequenceNumber. Excluded: createdAt/updatedAt, lat/long.
**Verify:** unit test asserts the serialized string for a fixture trip matches an expected
golden-string exactly, field order included.

### T-008.3 · Specify when signing happens and how the chain advances
**How:** Sign the canonical payload (SHA256withECDSA, Keystore private key) at the literal moment
the trip transitions into `completed` — one call site. Store the signature (base64) and key alias
on the trip row. Maintain one rolling "tail hash" in a single settings entry; after signing,
advance it to the hash of the payload just signed, chaining in **finalization order**, not start
order.
**Verify:** sign two trips that finalize out of start-time order (a Work trip pending for hours,
then a later-started trip completing first) and confirm the tail still advances correctly in
finalization order, not corrupted by the start-time mismatch.

### T-008.4 · Specify the cold-start self-heal
**How:** On every app cold start, recompute the tail hash from the most-recently-signed trip in
Room and overwrite the settings entry with it.
**Verify:** simulate a crash between the Room write and the settings-entry update, restart the
app, and confirm the settings entry is correctly rebuilt from Room rather than left stale.

### T-008.5 · Specify the verification test
**How (TDD):** Sign a fixture trip, verify the signature against the stored public key, confirm it
passes. Mutate one field of the already-signed payload and confirm verification now fails.
**Verify:** both assertions hold — valid signature passes, tampered payload fails — in the same
test run.

---

# T-009 · Backend sync API (Flask / Cloud Run / Firestore)

### T-009.1 · Specify the project setup
**How:** Inside the backend's `.venv`, install Flask, the Firebase Admin SDK, and the Firestore
client library, pinned to the versions in `backend/requirements.txt`. Add a sync blueprint/route
module.
**Verify:** `pip check` reports no dependency conflicts; the Flask app starts locally with
`flask run` and responds on its health-check route.

### T-009.2 · Specify server-side auth verification
**How:** Read the `Authorization: Bearer <token>` header on every request; verify via the Firebase
Admin SDK's ID-token verification call; use the verified UID for every downstream operation; never
accept a client-supplied `userId`.
**Verify:** a request with a missing/invalid token returns 401; a request with a valid token
proceeds and the UID used downstream matches the token's subject, not any client-supplied field.

### T-009.3 · Specify the idempotent sync endpoint
**How (TDD):** Write a test that POSTs the same trip payload twice against the Firestore emulator
and asserts exactly one document exists afterward. Implement so the client-generated trip id **is**
the Firestore document id, written as an upsert, not an always-insert. Batch every trip in one sync
call into a single write operation, not one write per trip.
**Verify:** the test fails before the endpoint exists, passes after; a second test confirms a
20-trip batch produces exactly one Firestore write operation, not 20.

### T-009.4 · Specify the security rules
**How:** Deploy the rules from `automated_mileage_tracker_spec.md` §5, verbatim: create allowed
only when the authenticated UID matches the trip's `userId`; read/update/delete allowed only when
the authenticated UID matches the existing document's `userId`.
**Verify:** using the Firestore emulator's rules test harness, confirm a write from user A to user
B's document is denied, and a write from user A to their own document is allowed.

### T-009.5 · Specify photo upload
**How:** Upload only if the user opted in (default off). Compress client-side to ~300KB or less
before upload. Apply a storage lifecycle rule moving photos to cheaper storage after 30–90 days.
**Verify:** confirm a non-opted-in user's photo never reaches a network call; confirm an opted-in
upload's stored size is at or below ~300KB.

### T-009.6 · Specify account deletion
**How:** On a verified delete request, remove every trip document for that UID, the user's profile
document, and every stored photo under that user's storage prefix, as one logical operation.
Calling it twice must succeed both times.
**Verify:** call delete twice in a row in a test and confirm both calls return success with no
error, and that no trip/profile/photo data remains after the first call already.

---

# T-010 · Backend cost model — DONE; apply these constraints when building T-009

**How:** Deploy Cloud Run with zero minimum instances and a small maximum instance cap. Always
write to Firestore in batches (T-009.3), never per-trip in a loop. Keep photo upload off by default
(T-009.5). Never enable any cloud OCR API. Link billing only together with a budget (50/90/100%
alerts) and a Pub/Sub-triggered kill switch that actually disables billing at 100%.
**Verify:** check the deployed Cloud Run service's `min-instances` is 0; check the GCP project has
no Cloud Vision API enabled; if billing is linked, confirm the kill-switch Cloud Function exists
and the budget alerts are configured, not just documented as a plan.

---

# T-011 · Analytics events + SARS-ready reporting

### T-011.1 · Specify the event taxonomy
**How:** Instrument exactly these six events, each carrying `userId`, `tripId`, and a timestamp:

| Event | Fired when | Extra properties |
|---|---|---|
| `trip_started` | state machine enters `active` | trigger source (activity recognition / Bluetooth / manual) |
| `trip_classified` | user taps Work/Private | classification |
| `trip_completed` | state machine enters `completed` | distance, whether odometer was verified |
| `trip_flagged` | admin flags a trip for correction | flag reason |
| `trip_approved` | admin approves a trip | — |
| `export_generated` | a CSV/SARS export is produced | row count, export type |

**Verify:** trigger each of the six flows manually and confirm exactly one matching event appears
in the analytics backend's debug/event viewer.

### T-011.2 · Specify client instrumentation
**How:** Log each event from its exact call site through a single logging interface, so the
underlying analytics SDK can be swapped without touching call sites.
**Verify:** swap the logging interface's implementation for a no-op fake in a test and confirm no
call site breaks or needs editing.

### T-011.3 · Specify the admin review query
**How:** Query trips where review status is "unreviewed," paginated with a fixed page size and
cursor — never an unbounded read of the whole collection.
**Verify:** with more rows than one page size seeded, confirm the first query returns exactly one
page and a usable cursor for the next.

### T-011.4 · Specify the SARS export query
**How:** Query trips where status is "completed" **and** review status is "approved," built only
from this query.
**Verify:** seed one approved-completed trip and one completed-but-unapproved trip; confirm the
export query returns only the first.

---

# T-012 · Store publishing readiness (Play / App Store / Huawei)

### T-012.1 · Specify the disclosure screen
**How:** Show a screen, before the OS background-location prompt, stating plainly what's collected
and why.
**Verify:** confirm the disclosure screen renders and is dismissed *before* the OS permission
dialog appears, not after or concurrently.

### T-012.2 · Specify the review video
**How:** Record disclosure screen → OS permission prompt → a full trip start-to-finish. Attach it
to the Play Console review submission.
**Verify:** the recording is attached to the actual submission, not just saved locally.

### T-012.3 · Specify the iOS usage-description strings
**How:** Write specific, accurate justification text for the location and motion usage-description
keys.
**Verify:** the strings describe this app's actual behavior, not generic placeholder text — read
them back against what the app does feature-by-feature.

### T-012.4 · Specify the privacy policy
**How:** Publish a static, publicly reachable page describing location/camera data handling. Link
it from both store listings.
**Verify:** the URL loads without authentication from a fresh browser session.

### T-012.5 · Specify the data-safety forms
**How:** Complete both stores' data-safety/privacy questionnaires matching the app's actual
declared permissions.
**Verify:** cross-check each form entry against the manifest/`Info.plist` permission list line by
line; no contradictions.

### T-012.6 · Specify the Play closed-testing requirement
**How:** Recruit at least 20 opted-in testers onto the closed-testing track; let 14 continuous days
pass before applying for production release.
**Verify:** the Play Console testing dashboard shows ≥20 testers and ≥14 continuous days elapsed
before the production-release application is submitted.

### T-012.7 · Specify the Huawei submission
**How:** Submit developer-identity verification to AppGallery Connect, complete its
background-location form and video, reuse the T-012.4 privacy policy URL.
**Verify:** AppGallery Connect shows the verification and permission-form submissions as accepted,
not pending or rejected.

---

# T-013 · iOS port (Swift, CoreMotion, CoreLocation, Vision)

### T-013.1 · Specify detection
**How:** Use `CMMotionActivityManager` background activity updates watching for the automotive
state, against the same locked thresholds as Android (70%, 30s, 3min/2min).
**Verify:** field-test on an iPhone — a real drive triggers a trip start within the same time
budget as the Android equivalent.

### T-013.2 · Specify the notification
**How:** Use an actionable notification category with Work/Private actions, handled without
launching the full app UI.
**Verify:** tap an action from the lock screen and confirm classification is recorded without the
app's main UI appearing first.

### T-013.3 · Specify location tracking
**How:** Use `CLLocationManager` with best accuracy, a 10-meter distance filter, background
indicator enabled; use the native distance-between-points function.
**Verify:** confirm distance accumulated over a known test route matches the Android app's
accumulated distance for the same route within a small tolerance.

### T-013.4 · Specify OCR
**How:** Use `VNRecognizeTextRequest`, applying the same regex and composite scoring as T-005.2/
T-005.3, reimplemented in Swift.
**Verify:** run the same fixture odometer photos used in Android's T-005.3 tests through the iOS
pipeline and confirm matching confident/low-confidence outcomes.

### T-013.5 · Specify authentication
**How:** Add "Sign in with Apple" alongside the existing Google sign-in.
**Verify:** both sign-in options are visible and functional on the same screen; App Store
Connect's review checklist item for this is satisfied.

### T-013.6 · Specify the data contract
**How:** Write to the exact same Firestore document shape T-009 defines for Android.
**Verify:** sync one trip from iOS and one from Android, fetch both from Firestore, confirm
identical field sets and types.

---

# T-016 · Huawei HMS technical adaptation (no GMS)

### T-016.1 · Specify the build split
**How:** Add a Gradle product-flavor dimension splitting a Google-services build from a
Huawei-services build at build time.
**Verify:** building the Huawei flavor produces an APK with zero Google Play Services
dependencies in its merged manifest/dependency tree.

### T-016.2 · Specify the auth swap
**How:** In the Huawei flavor, replace Google sign-in with Huawei Account Kit, keeping an
email/password fallback shared with the Google flavor.
**Verify:** sign in successfully on a Huawei device with no GMS installed.

### T-016.3 · Specify the detection swap
**How:** In the Huawei flavor, replace the Activity Recognition client with Huawei Location Kit's
activity-identification service, against the same locked thresholds.
**Verify:** a real drive on a Huawei device triggers a trip start within the same time budget as
the Google-flavor build.

### T-016.4 · Specify the OCR swap
**How:** In the Huawei flavor, replace ML Kit's text recognizer with Huawei ML Kit's text
analyzer; reuse the existing regex/scoring logic unchanged.
**Verify:** run the same fixture odometer photos through the Huawei pipeline and confirm matching
confident/low-confidence outcomes to the Google-flavor build.

### T-016.5 · Specify the sync-routing swap
**How:** In the Huawei flavor, route trip sync through T-009's REST endpoints over plain HTTPS
instead of the Firestore SDK directly.
**Verify:** sync a trip from a Huawei-flavor build and confirm it appears in Firestore identically
to a trip synced from the Google-flavor build, with no direct Firestore SDK call ever made
client-side.

---

# T-017 · Compose UI screens — layout, navigation, visual design (all 7 screens)

> T-002–T-007 own each screen's *state and logic* (what data a ViewModel holds, what a button
> click does). This task owns the *layout* — what's actually on screen, how it's arranged, how
> navigation connects the 7 screens, and what the loading/empty/error states look like. Build this
> alongside T-002–T-007, screen by screen, not after all of them are done.

### T-017.1 · Specify the navigation graph and shared theme
**How:** Build a `NavHost` with one route per screen (Setup, Home, Classification, Odometer,
History, Export, Settings) using `androidx.navigation.compose`. Define a `MaterialTheme` wrapper
with an explicit `ColorScheme`, `Typography`, and `Shapes` — do not ship the unmodified Material3
defaults. Wire the lock-screen notification deep link from T-003.3 to land directly on the
Classification route with the trip id as a navigation argument, not on the graph's start
destination.
**Verify:** launch the app and confirm it starts on Setup (first run) or Home (subsequent runs);
trigger the classification notification and confirm it navigates straight to the Classification
screen with the correct trip id, not through Home first.

### T-017.2 · Build `SetupPermissionsScreen`
**How:** Lay out the permission-rationale copy (why location/camera/notifications are needed),
a request-permissions button wired to T-002.1's permission launcher, and a limited-mode banner
shown when `limitedMode` is true. Use `Scaffold` + `Column` with Material3 `Text`/`Button`
components — no custom canvas drawing needed for this screen.
**Verify:** deny a permission and confirm the limited-mode banner renders with a clear explanation,
not a blank or broken layout.

### T-017.3 · Build `HomeStatusScreen`
**How:** Lay out current tracking status (idle/active/limited-mode), an in-progress-trip banner
when one exists (bound to T-002/T-004's `observeInProgressTrip`), the last completed trip's
summary, and a quick-export action. Use `Card` components for the status and last-trip summary,
not a flat list.
**Verify:** with an active trip, confirm the in-progress banner appears and updates live as
distance accumulates (T-004.5's 15s flush should visibly move the displayed number).

### T-017.4 · Build `TripClassificationScreen`
**How:** Lay out Work/Private selection (e.g. `SegmentedButton` or two `FilterChip`s), a
business-reason `OutlinedTextField` shown only when Work is selected, and an inline validation
error tied to T-003.2's validation result. Disable the confirm action while the field is blank on
a Work trip — don't let the user submit and then show an error after the fact.
**Verify:** select Work with a blank reason and confirm the confirm action is disabled/blocked, not
just showing an error after a failed submit.

### T-017.5 · Build `OdometerCaptureScreen`
**How:** Lay out the CameraX preview (`PreviewView` via `AndroidView` interop), a capture button,
an OCR-in-progress indicator while T-005.3 runs, and the manual-fallback numeric field from T-005.5
shown only on low-confidence/no-text/sanity-check-failure outcomes.
**Verify:** capture a clear, isolated odometer photo and confirm the confident-result path shows
the parsed value for confirmation rather than silently auto-saving it.

### T-017.6 · Build `TripHistoryScreen` and `ExportScreen`
**How:** `TripHistoryScreen`: a `LazyColumn` of trips with a visually distinct treatment (e.g. a
badge or different card color) for `pending_business_reason`/`pending_ocr` rows versus completed
ones — per the brief's "clearly marked in the UI" requirement. `ExportScreen`: an export button, an
in-progress state, and a result state showing the written filename or an error message.
**Verify:** seed one pending and one completed trip, confirm they're visually distinguishable at a
glance, not just by reading status text.

### T-017.7 · Build `SettingsScreen`
**How:** Lay out the photo-retention toggle (T-005.6), the Bluetooth-trigger toggle + scan/saved-device
display (T-002.5), and read-only display of export path/background-behavior info.
**Verify:** toggling photo retention persists across an app restart (reads back from the same
settings store T-005.6 writes to).

### T-017.8 · Accessibility pass across all 7 screens
**How:** Check color contrast against WCAG AA, confirm every interactive element meets the minimum
touch-target size (48dp), and add `contentDescription`/semantic labels for icons and images so a
screen reader can announce them.
**Verify:** run TalkBack across all 7 screens and confirm every interactive element is announced
with a meaningful label, not "button" or silence.

---

> **Nothing in this document is marked "open" or "TBD."** Every step names a literal action and a
> literal check. If real-world testing later shows a specific constant needs to change (the 8m
> noise floor, the 80-point OCR gate, the 15s flush interval, the scoring weights), that's a
> measured revision to a value that was already decided — write in the new value and strike
> through the old one, don't leave anything blank.
