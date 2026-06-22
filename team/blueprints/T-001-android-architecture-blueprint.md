# T-001 — Android Architecture Blueprint

> **Status:** Design only (Option A). No Kotlin/Gradle files exist yet. This document is the
> spec a later session scaffolds *from* — every structural decision below is final; do not
> re-derive module boundaries, entity shape, or state names during scaffolding.
> **Owner:** android-engineer · **Reviewed against:** `developer_handoff_brief.md` §4-9,
> `automated_mileage_tracker_spec.md` §1-4.
> **Locked v1 facts used throughout (do not re-litigate):** start confidence 70% with 30s silent
> retry; stop after 3 min confirmed inactivity OR 2 min unstable signal OR manual stop; prompt
> shown within 5s of start, 30s prompt timeout; GPS 10m distance filter, Haversine accumulation;
> OCR ≥80% confidence else manual fallback, trip always saves; `Save odometer photos` defaults ON;
> Work trips need non-empty `businessReason` before completion; target Android 10+, graceful
> limited mode if permissions denied; CSV is completed-trips-only, fixed column order.

---

## 1. Gradle module / layer layout

### Decision: single Gradle module (`:app`), four internal packages, not four Gradle modules

**Call:** for a 2-week solo-developer MVP, use **one Gradle module** (`:app`) with the
ui/domain/data/service split enforced as **Kotlin package boundaries + a lint convention**, not
as separate Gradle modules with their own `build.gradle.kts`/`AndroidManifest.xml`/dependency
graphs.

**Justification:**
- Multi-module Gradle (`:app`, `:domain`, `:data`, `:service` as separate modules) buys you
  enforced compile-time boundaries and parallel build caching. Neither pays off here: the app is
  one developer, one APK, no plans for a second app or library consumer in the MVP window, and
  Hilt's `@Module`/`@InstallIn` wiring across module boundaries is exactly the kind of
  boilerplate that eats two-week budgets in Gradle sync time, not feature time.
- The brief's §6.1 boundary requirement ("UI layer: Compose + ViewModels only... Domain layer:
  trip state logic... Data layer: Room, DAOs, repositories... Service layer: foreground service,
  ActivityRecognition, notifications") is a **dependency-direction rule**, not a build-topology
  rule. Package-level separation + a documented "imports must flow inward" convention
  (`ui` → `domain` → `data`; `service` → `domain` + `data`; nothing imports from `ui`) achieves
  the same architectural guarantee without the build overhead.
- **Flip condition (write this down so nobody re-debates it later):** if the iOS port (T-013)
  ever needs to share domain/data Kotlin via KMP, or if a second Android surface (e.g. a Wear OS
  companion) appears, split `domain` and `data` into real Gradle modules at that point — the
  package boundaries below are already drawn on the lines a future module split would use, so the
  extraction is mechanical, not a redesign.

### Package tree (inside `:app`, root package `com.mileagetracker.app`)

```
app/
├── build.gradle.kts                          # single module, Compose + Hilt + Room + CameraX deps
├── src/main/
│   ├── AndroidManifest.xml                   # foreground service decl, permissions, FCM-free
│   ├── kotlin/com/mileagetracker/app/
│   │   ├── MileageTrackerApplication.kt      # @HiltAndroidApp
│   │   ├── MainActivity.kt                   # single-activity host, NavHost
│   │   │
│   │   ├── ui/                               # === UI LAYER === Compose + ViewModel ONLY
│   │   │   ├── navigation/
│   │   │   │   ├── MileageTrackerNavHost.kt
│   │   │   │   └── Screen.kt                 # sealed route definitions (7 screens, §5 below)
│   │   │   ├── setup/
│   │   │   │   ├── SetupPermissionsScreen.kt
│   │   │   │   └── SetupPermissionsViewModel.kt
│   │   │   ├── home/
│   │   │   │   ├── HomeStatusScreen.kt
│   │   │   │   └── HomeStatusViewModel.kt
│   │   │   ├── classification/
│   │   │   │   ├── TripClassificationScreen.kt
│   │   │   │   └── TripClassificationViewModel.kt
│   │   │   ├── odometer/
│   │   │   │   ├── OdometerCaptureScreen.kt
│   │   │   │   └── OdometerCaptureViewModel.kt
│   │   │   ├── history/
│   │   │   │   ├── TripHistoryScreen.kt
│   │   │   │   └── TripHistoryViewModel.kt
│   │   │   ├── export/
│   │   │   │   ├── ExportScreen.kt
│   │   │   │   └── ExportViewModel.kt
│   │   │   ├── settings/
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── SettingsViewModel.kt
│   │   │   └── common/                       # shared Compose components (buttons, cards)
│   │   │       └── MileageTrackerTheme.kt
│   │   │
│   │   ├── domain/                           # === DOMAIN LAYER === pure Kotlin, no Android deps
│   │   │   ├── model/
│   │   │   │   ├── Trip.kt                   # domain model (mirrors entity, no Room annotations)
│   │   │   │   ├── TripClassification.kt     # enum: WORK, PRIVATE
│   │   │   │   ├── TripStatus.kt             # enum: ACTIVE, COMPLETED, PENDING_BUSINESS_REASON, PENDING_OCR
│   │   │   │   └── PhotoRetentionMode.kt     # enum: TEMPORARY, SAVED
│   │   │   ├── statemachine/
│   │   │   │   ├── TripLifecycleStateMachine.kt   # §4 below — pure transition logic, no I/O
│   │   │   │   ├── TripStartEvent.kt          # sealed: ConfidentVehicleEntry, LowConfidenceRetryExhausted, ManualStart
│   │   │   │   └── TripStopEvent.kt           # sealed: ConfirmedInactivity, UnstableSignalTimeout, ManualStop
│   │   │   ├── classification/
│   │   │   │   └── ClassificationRules.kt     # business-reason-required-for-WORK validation
│   │   │   ├── ocr/
│   │   │   │   ├── OdometerOcrResult.kt       # sealed: Confident(value, confidence), LowConfidence, NoTextFound
│   │   │   │   └── OdometerTextParser.kt      # regex \b\d{5,6}\b extraction + bounding-box filtering rules
│   │   │   ├── export/
│   │   │   │   ├── CsvExportRules.kt          # completed-trips-only filter, blank-businessReason guard
│   │   │   │   └── CsvRow.kt                  # the 10 fixed columns, in order
│   │   │   └── repository/                    # INTERFACES ONLY — domain owns the contract
│   │   │       ├── TripRepository.kt
│   │   │       ├── TripPhotoRepository.kt
│   │   │       └── SettingsRepository.kt
│   │   │
│   │   ├── data/                              # === DATA LAYER === Room, DAOs, repo impls, file I/O
│   │   │   ├── local/
│   │   │   │   ├── MileageTrackerDatabase.kt   # RoomDatabase, version 1
│   │   │   │   ├── TripEntity.kt               # §2 below
│   │   │   │   ├── TripPhotoEntity.kt          # §2 below
│   │   │   │   ├── TripDao.kt                  # §2 below
│   │   │   │   ├── TripPhotoDao.kt             # §2 below
│   │   │   │   └── Converters.kt               # TypeConverters: enum<->String
│   │   │   ├── repository/
│   │   │   │   ├── TripRepositoryImpl.kt       # implements domain.repository.TripRepository
│   │   │   │   ├── TripPhotoRepositoryImpl.kt
│   │   │   │   └── SettingsRepositoryImpl.kt   # backed by DataStore (see Hilt graph §3)
│   │   │   ├── export/
│   │   │   │   └── CsvFileWriter.kt            # MediaStore/Downloads write, filename per spec
│   │   │   └── ocr/
│   │   │       └── MlKitOdometerOcrClient.kt   # wraps ML Kit TextRecognizer; returns domain.ocr result
│   │   │
│   │   └── service/                            # === SERVICE LAYER === foreground service, sensors
│   │       ├── TripTrackingForegroundService.kt    # the one foreground service, persistent notification
│   │       ├── activityrecognition/
│   │       │   ├── ActivityTransitionReceiver.kt   # BroadcastReceiver for ActivityTransitionResult
│   │       │   └── ActivityRecognitionRegistrar.kt # registers/unregisters the PendingIntent request
│   │       ├── location/
│   │       │   └── TripLocationCallback.kt         # FusedLocationProviderClient callback, 10m filter
│   │       ├── notification/
│   │       │   ├── TripAlertNotificationChannel.kt # creates mileage_tracker_trip_alerts (HIGH)
│   │       │   └── TripClassificationNotificationBuilder.kt  # lock-screen action intents
│   │       └── di/                                 # service-scoped Hilt bindings, see §3
│   │           └── ServiceModule.kt
│   │
│   └── res/                                    # standard Android resources (strings, icons, themes)
│
└── src/test/kotlin/com/mileagetracker/app/
    ├── domain/statemachine/TripLifecycleStateMachineTest.kt
    ├── domain/classification/ClassificationRulesTest.kt
    ├── domain/ocr/OdometerTextParserTest.kt
    └── domain/export/CsvExportRulesTest.kt
```

**Dependency-direction rule (enforce in code review, not tooling, for v1):**
`ui` → `domain`; `data` → `domain` (implements its interfaces); `service` → `domain` + `data`.
Nothing imports from `ui`. `domain` imports nothing Android-specific (no `android.*`,
no Room/Compose/CameraX annotations) so it stays unit-testable on the JVM without
Robolectric/instrumentation.

---

## 2. Room schema — `Trip` entity, `trip_photo` table, DAOs

### `TripEntity` (table `trips`)

| Field | Kotlin type | Room column | Nullable | Notes |
|---|---|---|---|---|
| id | `String` | `id` (PK) | No | UUIDv4, generated client-side at trip-start time |
| classification | `TripClassification` | `classification` | No | stored as `"work"`/`"private"` via `Converters` |
| startTimestamp | `Long` | `start_timestamp` | No | epoch millis |
| endTimestamp | `Long` | `end_timestamp` | No | epoch millis; **see note below — not truly optional** |
| startOdometerKm | `Double` | `start_odometer_km` | No | manual entry or 0.0 placeholder until captured |
| endOdometerKm | `Double` | `end_odometer_km` | No | same placeholder rule as start |
| verifiedOdometerKm | `Double?` | `verified_odometer_km` | Yes | set only on OCR success ≥80% or manual confirm |
| distanceKm | `Double` | `distance_km` | No | Haversine-accumulated; 0.0 while active |
| businessReason | `String?` | `business_reason` | Yes | required (non-blank) before status can be `completed` for `work` trips |
| startLatitude | `Double?` | `start_latitude` | Yes | null if location permission denied (limited mode) |
| startLongitude | `Double?` | `start_longitude` | Yes | " |
| endLatitude | `Double?` | `end_latitude` | Yes | " |
| endLongitude | `Double?` | `end_longitude` | Yes | " |
| status | `TripStatus` | `status` | No | `active`\|`completed`\|`pending_business_reason`\|`pending_ocr` — see §4 |
| photoRetention | `PhotoRetentionMode` | `photo_retention` | No | `temporary`\|`saved`, snapshot of the setting *at trip start* |
| createdAt | `Long` | `created_at` | No | epoch millis, set once on insert |
| updatedAt | `Long` | `updated_at` | No | epoch millis, set on every update (Room does not do this for you — the repository must set it explicitly on every write) |

**Resolving an ambiguity in the brief (decision, not a re-derivation of locked facts):**
`endTimestamp` is typed `Long` (non-null) in the brief's field list, but a trip in `active` status
has no end time yet. **Decision:** while `status == active`, `endTimestamp` is written as the same
value as `startTimestamp` (a sentinel meaning "not yet ended") rather than introducing a nullable
column that contradicts the brief's explicit type. The repository, not the UI, is responsible for
treating `endTimestamp == startTimestamp` as "trip still open" wherever duration is computed. This
keeps the Room schema textually identical to brief §6 while staying internally consistent.

**Indexes:** `@Index("status")` on `trips` — every recovery-on-restart query and the Home screen's
"is there an active trip" check filters by status, and the table will only ever hold low thousands
of rows for an MVP, but the index costs nothing and removes any doubt about query plans.

### `TripPhotoEntity` (table `trip_photos`)

| Field | Kotlin type | Room column | Nullable | Notes |
|---|---|---|---|---|
| id | `String` | `id` (PK) | No | UUIDv4 |
| tripId | `String` | `trip_id` (FK → `trips.id`) | No | `@ForeignKey(onDelete = CASCADE)` |
| imageUri | `String` | `image_uri` | No | content:// URI from MediaStore/app-private storage |
| capturedAt | `Long` | `captured_at` | No | epoch millis |

`@Index("trip_id")` on `trip_photos` for the FK lookup.

**Photo-retention interaction (decision-complete, not left to the coder to invent):** a row is
inserted into `trip_photos` **only when `photoRetention == saved`**. If the trip's setting is
`temporary`, the OCR pipeline reads the bitmap, runs ML Kit, and the file is deleted immediately
after OCR success + user confirmation — no `TripPhotoEntity` row is ever created for that capture.
This means `trip_photos` is allowed to have zero rows for a given trip even though a photo was
taken; that is correct behavior, not a bug, and should be the exact comment left in
`TripPhotoRepositoryImpl.kt` when scaffolded.

### `TripDao` interface (method signatures — exact, for the coder to implement against)

```kotlin
@Dao
interface TripDao {

    @Insert
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    // Recovery requirement: exactly one of these must exist after restart, or none.
    // "active" and "pending_*" are mutually exclusive in practice (see state machine §4),
    // but the query covers all three so recovery logic has one call site, not three.
    @Query(
        "SELECT * FROM trips WHERE status IN ('active', 'pending_business_reason', 'pending_ocr') " +
        "ORDER BY created_at DESC LIMIT 1"
    )
    suspend fun getInProgressTrip(): TripEntity?

    // Same query as a Flow, for the Home screen to reactively show "trip in progress" banner.
    @Query(
        "SELECT * FROM trips WHERE status IN ('active', 'pending_business_reason', 'pending_ocr') " +
        "ORDER BY created_at DESC LIMIT 1"
    )
    fun observeInProgressTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE status = 'completed' ORDER BY start_timestamp DESC")
    fun observeTripHistory(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status = 'completed' ORDER BY start_timestamp DESC")
    suspend fun getCompletedTripsForExport(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE status = 'pending_business_reason'")
    fun observePendingBusinessReasonTrips(): Flow<List<TripEntity>>

    @Query("UPDATE trips SET status = :newStatus, updated_at = :updatedAt WHERE id = :tripId")
    suspend fun updateTripStatus(tripId: String, newStatus: TripStatus, updatedAt: Long)

    @Query("UPDATE trips SET business_reason = :businessReason, updated_at = :updatedAt WHERE id = :tripId")
    suspend fun updateBusinessReason(tripId: String, businessReason: String, updatedAt: Long)

    @Delete
    suspend fun deleteTrip(trip: TripEntity)
}
```

### `TripPhotoDao` interface

```kotlin
@Dao
interface TripPhotoDao {

    @Insert
    suspend fun insertTripPhoto(photo: TripPhotoEntity)

    @Query("SELECT * FROM trip_photos WHERE trip_id = :tripId")
    suspend fun getPhotosForTrip(tripId: String): List<TripPhotoEntity>

    @Query("DELETE FROM trip_photos WHERE trip_id = :tripId")
    suspend fun deletePhotosForTrip(tripId: String)
}
```

### Recovery requirement — exact mechanism (no duplicate trips after restart)

This is the concrete answer to "how does restart recovery actually avoid duplicates," which the
brief states as a requirement (§5.9, §9, §10.4/10.5) but doesn't mechanize:

1. **On every app/service start** (both `MileageTrackerApplication.onCreate()` cold start and
   `TripTrackingForegroundService.onStartCommand()` after an OS-triggered restart), call
   `TripDao.getInProgressTrip()` **before** registering any new `ActivityRecognitionClient`
   transition request.
2. If it returns non-null: that trip is the source of truth. Resume the state machine in the
   state matching its persisted `status` (e.g. `pending_business_reason` → re-show the
   classification prompt with the reason field empty, not a fresh trip). **Do not call
   `insertTrip` again.**
3. If it returns null: the device is free to start a brand-new trip on the next `IN_VEHICLE`
   transition.
4. The `id` used for a trip is generated exactly once, at the moment the state machine transitions
   into `active` (see §4) — never regenerated, never re-derived from a timestamp (timestamps can
   collide across a service restart inside the same second; UUIDv4 cannot). This is the actual
   anti-duplication guarantee: there is only ever one code path that calls `insertTrip`, and it is
   gated by step 1 having already returned null.
5. `ActivityRecognitionRegistrar` must be idempotent: registering the same `PendingIntent` request
   twice (e.g. service restarted while a trip was already active) must not create a second trip
   when the next transition fires — this is enforced by step 1's check happening on every
   `onStartCommand`, not just the very first one.

---

## 3. Hilt dependency-injection graph

**Module file layout** (each in `data/di/` unless noted):

```
data/di/
├── DatabaseModule.kt        # @InstallIn(SingletonComponent::class)
├── RepositoryModule.kt      # @InstallIn(SingletonComponent::class)
├── OcrModule.kt             # @InstallIn(SingletonComponent::class)
├── LocationModule.kt        # @InstallIn(SingletonComponent::class)
└── SettingsModule.kt        # @InstallIn(SingletonComponent::class) — DataStore<Preferences>

service/di/
└── ServiceModule.kt         # @InstallIn(ServiceComponent::class) — service-scoped bindings
```

| Module | Provides | Scope | Binds to |
|---|---|---|---|
| `DatabaseModule` | `MileageTrackerDatabase` (via `Room.databaseBuilder`) | `@Singleton` | concrete class, app-wide |
| `DatabaseModule` | `TripDao` (`database.tripDao()`) | `@Singleton` | concrete |
| `DatabaseModule` | `TripPhotoDao` (`database.tripPhotoDao()`) | `@Singleton` | concrete |
| `RepositoryModule` | `TripRepository` | `@Singleton` | `@Binds TripRepositoryImpl` |
| `RepositoryModule` | `TripPhotoRepository` | `@Singleton` | `@Binds TripPhotoRepositoryImpl` |
| `RepositoryModule` | `SettingsRepository` | `@Singleton` | `@Binds SettingsRepositoryImpl` |
| `OcrModule` | `OdometerOcrClient` (interface in `domain.ocr`) | `@Singleton` | `@Binds MlKitOdometerOcrClient` — wraps `TextRecognition.getClient()` |
| `LocationModule` | `FusedLocationProviderClient` | `@Singleton` | `LocationServices.getFusedLocationProviderClient(context)` |
| `LocationModule` | `ActivityRecognitionClient` | `@Singleton` | `ActivityRecognition.getClient(context)` |
| `SettingsModule` | `DataStore<Preferences>` | `@Singleton` | named `"mileage_tracker_settings"`, used by `SettingsRepositoryImpl` for `Save odometer photos` toggle, export path, background-behavior flags |
| `ServiceModule` | `TripLifecycleStateMachine` | `@ServiceScoped` | fresh instance per service lifecycle, but reads its starting state from `TripRepository.getInProgressTrip()` on construction (per §2 recovery mechanism) |

**Constructor-injection (no explicit module needed — Hilt resolves these via `@Inject constructor`):**
- All 7 ViewModels: `@HiltViewModel class XViewModel @Inject constructor(...)` — each takes only
  the repository/use-case interfaces it needs (never `TripDao` directly — see §5 for exactly
  which dependency each ViewModel takes).
- `TripTrackingForegroundService`: `@AndroidEntryPoint`, injects `TripRepository`,
  `ActivityRecognitionRegistrar`, `FusedLocationProviderClient`, `TripAlertNotificationChannel`.
- `ActivityTransitionReceiver`: `@AndroidEntryPoint` (BroadcastReceiver supports Hilt injection),
  injects `TripRepository` and the state machine entry point so it can hand off transition events
  without going through the service if the service isn't already running.

**Why `TripRepository` is an interface bound via `@Binds`, not a concrete class injected
directly:** this is the literal mechanism that keeps "repository implementations stay out of
ViewModels" honest — a ViewModel can only ever see the `domain.repository.TripRepository`
interface, never `TripRepositoryImpl`, never `TripDao`. Swapping the Room-backed implementation
for a test fake in unit tests is a Hilt test-module swap, not a ViewModel code change.

---

## 4. Trip lifecycle state machine

### Two layers — be precise about which one is which

The brief locks **`Trip.status`** to exactly four persisted values: `active`, `completed`,
`pending_business_reason`, `pending_ocr`. But the full lifecycle described in §5.1-5.4 of the
brief has more granularity than four states *before* a trip even reaches `active` (the silent
retry window) and *during* `active` (the unstable-signal countdown). Persisting every one of
those as a `Trip.status` value would violate the brief's fixed enum, so:

- **Persisted states (`Trip.status` column, 4 values):** govern what's saved to Room and what the
  CSV exporter and Trip History screen see.
- **Transient service-layer states (never written to `Trip.status`, live only in
  `TripTrackingForegroundService` + `TripLifecycleStateMachine` in-memory):** govern the
  pre-trip detection retry loop and the prompt-countdown/unstable-signal timers. These are
  represented as sealed event/intermediate types in `domain.statemachine`, not as enum values
  competing with the locked 4.

### Full transition table

| From (transient or persisted) | Event | Threshold | To | Notes |
|---|---|---|---|---|
| *(no trip)* | `IN_VEHICLE` transition, confidence ≥70% | immediate | **transient: `PromptPending`** | not yet written to Room |
| *(no trip)* | `IN_VEHICLE` transition, confidence <70% | — | **transient: `SilentRetry`** | retries detection silently |
| `SilentRetry` | confidence reaches ≥70% within 30s | ≤30s | **transient: `PromptPending`** | |
| `SilentRetry` | 30s elapses, still <70% | =30s | **transient: `PromptPending`** (forced) | brief §5.9: "retry silently for up to 30 seconds **before asking the user**" — after 30s the user is asked regardless, so this also reaches `PromptPending`, just with a lower-confidence flag passed to the notification builder for telemetry, not for blocking the prompt |
| `PromptPending` | classification notification shown | within 5s of entering `PromptPending` (brief §5.9 prompt-timing rule) | **persisted: `active`** | `insertTrip()` called here — this is the one and only insert point (see §2 recovery mechanism). `startTimestamp` = the moment `PromptPending` was entered, not the moment the notification rendered |
| `active` | user taps `Private` | — | **persisted: `completed`** *(pending GPS/OCR — see "completion is two-part" below)* | |
| `active` | user taps `Work`, reason already non-empty (re-entrant case, unlikely but handled) | — | **persisted: `completed`** | |
| `active` | user taps `Work`, reason empty | — | **persisted: `pending_business_reason`** | |
| `active` | prompt timeout, no tap within 30s | =30s | **persisted: `active`** (unchanged) + repeat notification | brief: "keep the prompt visible long enough... without losing the trip" — the trip is never discarded for prompt timeout, only re-prompted; the foreground service keeps tracking GPS regardless of classification status so distance isn't lost while the user is slow to respond |
| `active` | confirmed inactivity | 3 min (locked) | **persisted: `completed`** (if classified) or stays `active`-but-stopped (if still unclassified — extremely rare, treat as `completed` with `classification` left at a default `private` and flag for user review in Trip History; do not block stop-detection on classification) | |
| `active` | lost/unstable activity signal | 2 min (locked) | same target as above | this is the `UnstableSignalTimeout` sealed event distinct from `ConfirmedInactivity` — both terminate the trip, kept as separate event types because they have different telemetry/debugging value (per brief §13, false-stop detection is the highest-risk area) |
| `active` | manual stop confirmation | user-initiated | same target as above | |
| `pending_business_reason` | user submits non-empty `businessReason` | — | **persisted: `completed`** | `ClassificationRules.validateBusinessReason()` is the single gate — UI calls it via the ViewModel→use-case path, never inlines the non-empty check itself |
| `pending_business_reason` | app restart, trip still pending | — | **persisted: `pending_business_reason`** (unchanged) | recovery re-shows the same prompt; see §2 |

### "Completion is two-part" — resolving the `pending_ocr` ambiguity (decision, stated explicitly)

The brief lists `pending_ocr` as a valid status but also states OCR failure must never block
completion ("the trip must still save even when OCR is unavailable" — brief §5.9). Read literally
those two statements are in tension. **Decision:**

- `pending_ocr` is entered **only** in the narrow window between "user has been asked to confirm
  trip stop" and "OCR pipeline (success, low-confidence-fallback, or manual entry) has produced a
  value the user has accepted" — i.e., it is a UI-blocking-wait state, not a data-completeness
  gate. It exists so the Trip History/Home screens can show "finishing up" instead of either
  `active` (wrong — driving has stopped) or `completed` (wrong — no odometer value confirmed yet).
- The moment the user **either** accepts an OCR result (≥80% confidence) **or** submits a manual
  odometer value, the trip leaves `pending_ocr` and becomes `completed` (or
  `pending_business_reason` if it's also a Work trip with no reason yet — `pending_business_reason`
  takes priority for display purposes since it's the actionable blocker).
- If the user backgrounds the app while in `pending_ocr` (closes the odometer capture screen
  without confirming), the trip **stays** `pending_ocr` in Room — this is the second recovery
  case alongside `pending_business_reason` (see §2's `getInProgressTrip()` query, which already
  covers both).
- This means the real transition is: `active` → (stop event) → **persisted: `pending_ocr`** →
  (OCR/manual value confirmed) → **persisted: `completed`** or **persisted:
  `pending_business_reason`**. The transition table above is corrected to insert `pending_ocr` as
  the actual landing state for every "confirmed inactivity / unstable signal / manual stop" row,
  with `completed`/`pending_business_reason` reached only after the odometer step resolves.

### State diagram (textual, persisted states only — for the coder to scaffold the enum + DAO transitions against)

```
            [IN_VEHICLE >=70%, or 30s retry exhausted]
                            │
                   (transient prompt window)
                            │ [notification shown, within 5s]
                            ▼
                        ┌────────┐
                ┌──────►│ active │◄──────┐ (prompt timeout, re-notify; trip unchanged)
                │       └────────┘       │
                │            │ [stop event: 3min inactivity
                │            │  OR 2min unstable OR manual stop]
                │            ▼
                │     ┌─────────────┐
                │     │ pending_ocr │
                │     └─────────────┘
                │            │ [OCR accepted >=80% OR manual odometer entry confirmed]
                │            ▼
       (re-shown on   ┌───────────────────────────┐
        app restart    classification check:       
        if blank)     Work AND reason blank? ──Yes──► pending_business_reason ──[reason submitted]──┐
                            │ No                                                                      │
                            ▼                                                                         │
                       ┌───────────┐                                                                  │
                       │ completed │◄─────────────────────────────────────────────────────────────────┘
                       └───────────┘
```

---

## 5. Screens and ViewModels (7 screens per brief §7)

| # | Screen (Composable) | ViewModel | State the ViewModel owns | Repository / use-case it calls |
|---|---|---|---|---|
| 1 | `SetupPermissionsScreen` | `SetupPermissionsViewModel` | `PermissionState` (which of `ACCESS_FINE_LOCATION`/`ACCESS_BACKGROUND_LOCATION`/`CAMERA`/`POST_NOTIFICATIONS` are granted), whether limited-mode banner shows | `SettingsRepository` (to persist "user has completed first-run setup" flag); no Trip repository access — this screen never touches trips |
| 2 | `HomeStatusScreen` | `HomeStatusViewModel` | current tracking status (idle/active/limited-mode), the in-progress trip if any (via `observeInProgressTrip`), last-completed-trip summary | `TripRepository.observeInProgressTrip()`, `TripRepository.observeTripHistory()` (for "last trip" — take(1)) |
| 3 | `TripClassificationScreen` | `TripClassificationViewModel` | the trip being classified (loaded by tripId nav arg), selected classification (`work`/`private`), business-reason text field state, validation error if Work + blank reason | `TripRepository.getTripById`, `TripRepository.updateClassification` (use-case-wrapped: `ClassifyTripUseCase` calls `ClassificationRules.validateBusinessReason()` before writing) |
| 4 | `OdometerCaptureScreen` | `OdometerCaptureViewModel` | CameraX preview/capture state, OCR-in-progress flag, `OdometerOcrResult` (Confident/LowConfidence/NoTextFound), manual-entry fallback text field | `TripPhotoRepository.savePhotoIfRetentionEnabled`, `OdometerOcrClient.recognizeText` (via domain interface), `TripRepository.updateVerifiedOdometer` |
| 5 | `TripHistoryScreen` | `TripHistoryViewModel` | the list of completed + pending trips (`observeTripHistory` + `observePendingBusinessReasonTrips` merged for display, with pending ones flagged distinctly per brief §5.9's "clearly marked in the UI" requirement) | `TripRepository.observeTripHistory`, `TripRepository.observePendingBusinessReasonTrips` |
| 6 | `ExportScreen` | `ExportViewModel` | export-in-progress flag, last export result (file path / row count / error) | `TripRepository.getCompletedTripsForExport`, `CsvExportRules.buildExportRows` (domain), `CsvFileWriter.writeToDownloads` (data, called through a use-case so the ViewModel never touches file I/O directly) |
| 7 | `SettingsScreen` | `SettingsViewModel` | `Save odometer photos` toggle state, export-path display, background-behavior info, Bluetooth-trigger toggle + saved-device display (off by default, per brief §5.1) | `SettingsRepository` (read/write all settings) |

**Hard rule restated for the coder:** no ViewModel above takes a `TripDao`, `TripPhotoDao`, or
`MileageTrackerDatabase` as a constructor parameter. Every one of them takes only
`TripRepository`/`TripPhotoRepository`/`SettingsRepository` (the domain interfaces) or a
use-case class that wraps one of those repositories. If a diff under review adds a DAO import to
anything in the `ui/` package, that is an automatic HIGH-severity finding.

---

## 6. Build order and android-coder delegation split

### Build order (what gets created first; each step depends on the previous existing and compiling)

1. **Gradle project skeleton** — `settings.gradle.kts`, root `build.gradle.kts`, `:app`
   `build.gradle.kts` with Kotlin, Compose, Hilt, Room, CameraX, ML Kit Text Recognition,
   WorkManager, DataStore dependencies pinned to specific versions (android-engineer picks
   versions; no "latest" ranges). `AndroidManifest.xml` with the foreground-service declaration
   and the full permission list from brief §8 (declared but not yet requested at runtime).
2. **Domain layer, zero Android dependencies** — `domain/model/*`, `domain/statemachine/*`,
   `domain/classification/*`, `domain/ocr/OdometerOcrResult.kt` +
   `OdometerTextParser.kt`, `domain/export/*`, `domain/repository/*interfaces*`. This compiles as
   plain Kotlin/JVM and can be fully unit-tested (per the test files listed in the package tree)
   before a single Room or Compose line exists.
3. **Room schema** — `TripEntity`, `TripPhotoEntity`, `Converters`, `TripDao`, `TripPhotoDao`,
   `MileageTrackerDatabase`. Verify with a Room-generated-schema export check
   (`exportSchema = true`) and one instrumented test that inserts/queries a trip.
4. **Repository implementations + Hilt data modules** — `TripRepositoryImpl`,
   `TripPhotoRepositoryImpl`, `SettingsRepositoryImpl`, `DatabaseModule`, `RepositoryModule`,
   `SettingsModule`. This is the point where domain interfaces get a real backing.
5. **Hilt app bootstrap** — `MileageTrackerApplication`, confirm `@HiltAndroidApp` + the modules
   from step 4 resolve (a trivial "does it inject" smoke test, e.g. inject `TripRepository` into
   `MainActivity` and log a no-op call).
6. **Service layer** — `TripAlertNotificationChannel` (channel creation is a one-time
   `Application.onCreate` call, build this before the service that posts to it),
   `TripTrackingForegroundService` shell (start/stop, persistent notification, no detection logic
   yet), `ActivityRecognitionRegistrar` + `ActivityTransitionReceiver`,
   `TripLocationCallback`. Wire `TripLifecycleStateMachine` into the service last, once both the
   ActivityRecognition and location pieces exist to feed it events. **This step is largely owned
   by `geo-sensors-specialist` for the detection-accuracy parts (T-002/T-004); android-engineer
   owns the service shell and its DI wiring.**
7. **UI layer, screen by screen, in dependency order:** Setup/Permissions first (needed before
   anything else can request runtime permissions) → Home/Status → Trip Classification → Odometer
   Capture (depends on T-005's CameraX/ML Kit wiring from `ml-ocr-specialist`) → Trip History →
   Export → Settings last (lowest risk, no other screen depends on it).
8. **CSV export wiring** — `CsvExportRules`, `CsvRow`, `CsvFileWriter`, `ExportViewModel`. Can be
   built and tested against fixture `TripEntity` rows before the Export screen's UI is finished.
9. **Restart-recovery integration test** — the one test that exercises the full mechanism in
   §2's "Recovery requirement": insert an `active` trip, kill-and-restart the process
   (Robolectric or instrumented), assert `getInProgressTrip()` returns the same `id` and no second
   row was inserted.

### Delegation split: `android-coder` (Haiku) vs. `android-engineer` judgment

**Hand to `android-coder` as soon as this document exists (routine, fully specified, no design
decisions left):**
- Gradle file boilerplate (step 1) — given exact dependency coordinates and versions from
  android-engineer.
- `TripEntity`, `TripPhotoEntity`, `Converters`, `TripDao`, `TripPhotoDao` — the schema and method
  signatures above are exact; this is transcription, not design.
- `domain/model/*` enum classes (`TripClassification`, `TripStatus`, `PhotoRetentionMode`) — pure
  data, no logic.
- `RepositoryModule`, `DatabaseModule`, `SettingsModule`, `OcrModule`, `LocationModule` Hilt
  binding boilerplate — the table in §3 is the exact spec.
- `CsvRow.kt` (the 10-field data class matching the fixed column order) and the basic
  `CsvFileWriter` MediaStore-write mechanics (filename pattern, UTF-8 encoding) — mechanical once
  the column order and naming convention are given (both are locked facts, not decisions).
- Compose screen *shells* (layout, text fields, buttons) once android-engineer has specified the
  exact state hoisting contract per screen (the table in §5 plus a short UI-state data class per
  screen) — but the **ViewModel logic itself** (validation branching, use-case orchestration)
  should not go to the coder if it embeds any of the judgment calls in §4 (e.g. the `pending_ocr`
  → `completed`-vs-`pending_business_reason` priority decision); write that branch once as a
  reference implementation, then the coder can replicate the pattern for the remaining screens.
- Unit test boilerplate for the domain layer (AAA-pattern test bodies) once android-engineer
  specifies the exact test cases (e.g. "blank business reason on a Work trip → validation error;
  non-blank → success") — the coder writes the test code, not the test *cases*.

**Keep with `android-engineer` (judgment, cross-cutting, or risk-concentrated):**
- `TripLifecycleStateMachine` itself — this is the highest-risk file in the app (brief §13: false
  starts/stops are the top risk) and encodes the `pending_ocr` resolution decision from §4; a
  misread here silently corrupts trip data with no compile error to catch it.
- `ActivityTransitionReceiver` / `ActivityRecognitionRegistrar` / `TripLocationCallback` —
  coordinate directly with `geo-sensors-specialist`; these touch the locked thresholds (70%/30s,
  3min/2min, 10m filter) and the restart-recovery idempotency guarantee from §2 step 5.
  android-engineer owns the Hilt wiring and service lifecycle; geo-sensors-specialist owns the
  detection-accuracy tuning inside it.
- `TripTrackingForegroundService`'s lifecycle (start/stop/restart handling, the
  `getInProgressTrip()` recovery check on every `onStartCommand`) — this is exactly the
  no-duplicate-trips guarantee; do not delegate the restart-check call site.
- The `OdometerCaptureViewModel`'s OCR-result branching (confident/low-confidence/no-text →
  exact UI state + whether the photo gets persisted per the retention rule in §2) — coordinate
  with `ml-ocr-specialist` on the OCR client contract first (T-005), then android-engineer wires
  the ViewModel branch logic once, hands the pattern to the coder for any repeated boilerplate
  around it.
- `CsvExportRules.buildExportRows` — the "exclude pending Work trips, never emit a blank
  businessReason" guard is a compliance-relevant filter (SARS logbook integrity per the project's
  broader spec); write and unit-test this one personally even though it's short.
- Anything touching permission-denied / limited-mode fallback branching across screens — this
  cross-cuts Setup, Home, and Settings and needs one consistent mental model, not three
  independently-invented ones.

---

## Open questions for other specialists (flagged, not blocking this document)

1. **GPS/ActivityRecognition wiring detail** (T-002/T-004, `geo-sensors-specialist`): this
   blueprint defines the *service-layer shell and Hilt bindings* for `ActivityRecognitionClient`/
   `FusedLocationProviderClient`, but the actual `ActivityTransitionRequest` construction (which
   transitions to register, how the 70%-confidence read is derived from `DetectedActivity`, which
   doesn't expose a raw percentage in the public API the same way across OS versions) needs
   geo-sensors-specialist's sign-off before step 6 of the build order starts.
2. **OCR client contract detail** (T-005, `ml-ocr-specialist`): `OdometerOcrClient` is specified
   here as a domain interface with a `recognizeText(bitmap) -> OdometerOcrResult` shape, but the
   exact ML Kit `TextRecognizer` configuration (Latin script model, image preprocessing/rotation
   handling) is ml-ocr-specialist's call, not designed here.
3. ~~**Trip signing** (T-008, `security-crypto-specialist`): this blueprint does not add a signature
   or hash-chain field to `TripEntity` because that decision is still open per `team/TASKS.md`
   T-008. If/when that debate resolves, the schema in §2 needs one additional nullable column
   (e.g. `signatureHash: String?`) and a migration — flagging now so it isn't a surprise schema
   change later.~~
   **RESOLVED 2026-06-18 (session 3, `/team-debate` T-008):** per-trip ECDSA P-256 signature
   (Android Keystore, StrongBox-preferred/TEE-fallback), signed at the literal terminal transition
   into `completed` (single call site — same discipline as §2's no-duplicate-trip insert point).
   Tamper-evidence against deletion/reordering comes from a **rolling tail-hash**, NOT a per-row
   chain column: a single `chainTailHash` value lives in a one-row DataStore settings entry
   (already in the Hilt graph via `SettingsModule`), is folded into each trip's signed payload as
   `prevTail`, and advances in **finalization order** (not start order — `pending_business_reason`/
   `pending_ocr` trips can finalize long after later-started trips, so chain-order ≠ calendar-order
   by design; the chronological truth lives in the independently-signed `startTimestamp`/
   `endTimestamp` fields). Room is the durability anchor; DataStore's tail is a derived, rebuildable
   cache reconciled from the most-recently-signed Room trip on every cold start, so a crash between
   the Room write and the DataStore write self-heals without a cross-store transaction.
   **Exact schema impact: two new nullable `TripEntity` columns — `signatureBase64: String?` and
   `signingKeyId: String?`** (a local Keystore alias, generated lazily at first trip completion,
   zero backend dependency) — one additive `Migration(1, 2)`, no `previousTripHash` column. Full
   canonical-field list, serialization rules, and Keystore lifecycle in the T-008 DECISION log entry
   (`team/LOGS.md`). Cost ruling: APPROVE, $0 MVP impact (cost-architect).
4. **Cost/cloud touch:** none. This entire blueprint is local-first with zero network or GCP
   surface, consistent with the MVP's local-first scope — no `cost-architect` ruling needed for
   T-001 itself.
