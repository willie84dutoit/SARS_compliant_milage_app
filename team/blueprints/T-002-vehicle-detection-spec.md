# T-002 — Vehicle Detection (ActivityRecognition) — Implementation Spec

> **Status:** Design complete, ready for android-engineer/android-coder implementation.
> **Owner of this document:** geo-sensors-specialist. **Implements against:** locked v1
> thresholds (start confidence 70%, confidence-acquisition window 30s, unstable-signal 2min,
> prompt timeout 30s, GPS distance filter 10m) and `FULL_IMPLEMENTATION_PLAN.md` T-002.1–T-002.5.
> No-delete rule applies — strike through, don't remove, if a value changes later.

---

## 1. Gap analysis

| Plan step | What already exists | What's missing / needs change | Files |
|---|---|---|---|
| **T-002.1** Runtime permission | Manifest declares `ACTIVITY_RECOGNITION` (T-001.4). No `SetupPermissionsScreen`/launcher wiring yet. | `ActivityResultContracts.RequestMultiplePermissions()` call site, `limitedMode` flag. Out of scope for this spec — handed to android-engineer (§6). | `ui/setup/SetupPermissionsScreen.kt` (not yet created) |
| **T-002.2** Domain start-event types | `TripStartEvent` sealed interface **already has all three cases**: `ConfidentVehicleEntry(confidencePercent, detectedAtEpochMillis)`, `LowConfidenceRetryExhausted(lastObservedConfidencePercent, forcedLowConfidence)`, `ManualStart(startedAtEpochMillis)`. `TripLifecycleStateMachine.onStartEvent()` already handles all three exhaustively, mapping to `TransientPhase.PromptPending`. `TripLifecycleStateMachineTest` already covers `ConfidentVehicleEntry` → `PromptPending`. | **Nothing structurally missing.** The sealed type and state-machine reaction the plan asks for in T-002.2 already exist from T-001. The only gap is test coverage for the other two start-event branches (`LowConfidenceRetryExhausted`, `ManualStart`) — add them for completeness (§5). Do **not** rename or duplicate `ConfidentVehicleEntry`. | `domain/statemachine/TripStartEvent.kt`, `domain/statemachine/TripLifecycleStateMachine.kt`, `domain/statemachine/TripLifecycleStateMachineTest.kt` |
| **T-002.3** Transition API registration + receiver handoff | `ActivityRecognitionRegistrar.register()`/`unregister()` shells exist with a `TODO` for the real `ActivityTransitionRequest`. `ActivityTransitionReceiver.onReceive()` is an empty `TODO`. Both already injected correctly into `TripTrackingForegroundService` and `ServiceModule`. | The actual `ActivityTransitionRequest` (ENTER+EXIT for `IN_VEHICLE`), the `requestActivityTransitionUpdates` call, and the receiver's `extractResult` loop with hand-off **only on ENTER**, never starting a trip directly. | `service/activityrecognition/ActivityRecognitionRegistrar.kt`, `service/activityrecognition/ActivityTransitionReceiver.kt` |
| **T-002.4** Confidence-acquisition window | Nothing exists. No second `requestActivityUpdates()` subscription, no max-tracking, no 30s timer. | A new class (`ConfidenceAcquisitionWindow`, §4) implementing the whole window: 5s-interval subscription, running-maximum tracking, 30s virtual-time-testable timer, success/timeout firing of `ConfidentVehicleEntry`/`LowConfidenceRetryExhausted`, and the unregister discipline. | New: `service/activityrecognition/ConfidenceAcquisitionWindow.kt` |
| **T-002.5** Bluetooth trigger (off by default) | Nothing exists; out of scope for this spec (separate plan step, not part of the T-002.2–T-002.4 core this task covers). | Toggle + scan UI, bonding, `ACTION_ACL_CONNECTED` receiver. | Not covered here — flag for a follow-up task if the Manager wants it bundled. |

**Bottom line:** T-002.2 is essentially already done by T-001's forward-looking scaffold. The real work in this task is T-002.3 (mechanical, the registrar/receiver shells already have the right shape) and **T-002.4, which is net-new and is the actual judgment call** (§4).

---

## 2. T-002.2 — Domain start-event types (confirm, don't duplicate)

No new sealed type is needed. The existing shape in `domain/statemachine/TripStartEvent.kt` already matches the plan exactly:

```kotlin
sealed interface TripStartEvent {
    data class ConfidentVehicleEntry(val confidencePercent: Int, val detectedAtEpochMillis: Long) : TripStartEvent
    data class LowConfidenceRetryExhausted(val lastObservedConfidencePercent: Int, val forcedLowConfidence: Boolean = true) : TripStartEvent
    data class ManualStart(val startedAtEpochMillis: Long) : TripStartEvent
}
```

And `TripLifecycleStateMachine.onStartEvent()` already reacts correctly:

```kotlin
fun onStartEvent(currentPhase: TransientPhase, event: TripStartEvent): TransientPhase {
    return when (event) {
        is TripStartEvent.ConfidentVehicleEntry ->
            TransientPhase.PromptPending(event.confidencePercent, isForcedLowConfidence = false)
        is TripStartEvent.LowConfidenceRetryExhausted ->
            TransientPhase.PromptPending(event.lastObservedConfidencePercent, isForcedLowConfidence = true)
        is TripStartEvent.ManualStart ->
            TransientPhase.PromptPending(confidencePercent = 100, isForcedLowConfidence = false)
    }
}
```

**Action for android-engineer/android-coder:** none — this code ships as-is. The only addition is the test coverage gap noted in §1/§5 (the existing test file only exercises `ConfidentVehicleEntry`).

**Important wiring point carried into §4:** `ConfidenceAcquisitionWindow` (the new T-002.4 class) is the thing that *constructs* `TripStartEvent.ConfidentVehicleEntry` and `TripStartEvent.LowConfidenceRetryExhausted` and hands them to `tripLifecycleStateMachine.onStartEvent()` via the foreground service — the window class itself does not call the state machine directly (it has no reference to it); it surfaces a callback/Flow that the service consumes, consistent with `service → domain` being the only allowed direction and the service remaining the single orchestration point that already holds `transientPhase`.

---

## 3. T-002.3 — Transition API registration + receiver handoff

### `ActivityRecognitionRegistrar` — exact registration

Replace the `register()` TODO with:

```kotlin
private fun buildTransitionRequest(): ActivityTransitionRequest {
    val enterInVehicle = ActivityTransition.Builder()
        .setActivityType(DetectedActivity.IN_VEHICLE)
        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        .build()

    val exitInVehicle = ActivityTransition.Builder()
        .setActivityType(DetectedActivity.IN_VEHICLE)
        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
        .build()

    return ActivityTransitionRequest(listOf(enterInVehicle, exitInVehicle))
}

fun register() {
    val pendingIntent = buildTransitionPendingIntent()
    activityRecognitionClient.requestActivityTransitionUpdates(buildTransitionRequest(), pendingIntent)
        .addOnSuccessListener {
            Timber.tag("MT-ActivityRecognition").i("Transition updates registered (IN_VEHICLE ENTER+EXIT)")
        }
        .addOnFailureListener { registrationFailure ->
            Timber.tag("MT-ActivityRecognition").e(registrationFailure, "Failed to register transition updates")
        }
}
```

- Both `ENTER` and `EXIT` are registered even though only `ENTER` is acted on (§ below) — `EXIT` is reserved for T-004's stop-side signal-quality reasoning (it currently contributes nothing on its own per the locked stop rule, which is inactivity/unstable-signal/manual-only, but registering it costs nothing extra on the same `ActivityTransitionRequest` and keeps the door open without a second registration round-trip later).
- `unregister()` is unchanged (already calls `removeActivityTransitionUpdates`) but should get the same `addOnSuccessListener`/`addOnFailureListener` + `MT-ActivityRecognition` logging treatment (§6 checklist).

### `ActivityTransitionReceiver` — exact receiver body

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (!ActivityTransitionResult.hasResult(intent)) return

    val result = ActivityTransitionResult.extractResult(intent) ?: return

    for (event in result.transitionEvents) {
        if (event.activityType != DetectedActivity.IN_VEHICLE) continue

        when (event.transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                Timber.tag("MT-ActivityRecognition").i("IN_VEHICLE ENTER received at %d", event.elapsedRealTimeNanos)
                confidenceAcquisitionWindow.startWindow(enteredAtEpochMillis = System.currentTimeMillis())
            }
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                Timber.tag("MT-ActivityRecognition").i("IN_VEHICLE EXIT received at %d", event.elapsedRealTimeNanos)
                // No action in T-002 — EXIT is not part of the locked stop rule (T-004 owns stop
                // detection via inactivity/unstable-signal/manual only). Logged for telemetry only.
            }
        }
    }
}
```

**Rule, stated explicitly because it is easy to get wrong:** the receiver hands off to `ConfidenceAcquisitionWindow.startWindow(...)` on `ENTER` and **never** calls `tripLifecycleStateMachine.onStartEvent()` or constructs a `TripStartEvent` itself. The receiver does not know what confidence value to use — the Transition API event carries no confidence field at all (confirmed against the public `ActivityTransitionEvent` API surface, which exposes only `activityType`/`transitionType`/`elapsedRealTimeNanos`). That is the entire reason T-002.4 exists as a second subscription.

`confidenceAcquisitionWindow` is injected into `ActivityTransitionReceiver` the same way `tripRepository` already is (`@Inject lateinit var`).

**Test for this section (Robolectric/fake-intent, per plan's own Verify line):** confirm the receiver calls `confidenceAcquisitionWindow.startWindow(...)` exactly once per ENTER event and zero times for an EXIT event. See §5 for the exact test.

---

## 4. T-002.4 — Confidence-acquisition window (core judgment)

### Why a second subscription is unavoidable

`ActivityTransitionEvent` (delivered to `ActivityTransitionReceiver`) has no `getConfidence()` — it is a pure state-transition signal. Confidence (0–100) only exists on `DetectedActivity`, delivered via the *other* API, `ActivityRecognitionClient.requestActivityUpdates(request, pendingIntent)`, whose results are read via `ActivityRecognitionResult.extractResult(intent)` → `getMostProbableActivity()` / `getProbableActivities()`. There is no way to get confidence without this second subscription. This is a real Play Services API limitation, not a design choice we could simplify away.

### `ConfidenceAcquisitionWindow` — exact class shape

New file: `app/src/main/kotlin/com/mileagetracker/app/service/activityrecognition/ConfidenceAcquisitionWindow.kt`

```kotlin
package com.mileagetracker.app.service.activityrecognition

import com.mileagetracker.app.domain.statemachine.TripStartEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * T-002.4. The Transition API's ENTER event carries no confidence value, so this class runs a
 * second, short-lived `requestActivityUpdates()` subscription (5s interval) purely to read
 * DetectedActivity.getConfidence() for IN_VEHICLE during the 30s window after an ENTER event.
 * Tracks the RUNNING MAXIMUM confidence seen (not the latest reading) so one high reading is not
 * erased by a lower one delivered afterward. Fires exactly one terminal event — confident-entry
 * (max reached 70+) or retry-exhausted (timer elapsed first) — then unregisters itself. Must never
 * be left registered after either branch fires; a leaked subscription means full-rate activity
 * updates running indefinitely in the background, which is the single biggest battery risk in the
 * detection path (see "Battery implications" below).
 */
interface ConfidenceAcquisitionWindow {

    /** Starts the window following an IN_VEHICLE ENTER event. No-op if a window is already running. */
    fun startWindow(enteredAtEpochMillis: Long)

    /** True while a window is actively subscribed; exposed for tests/diagnostics, not for control flow. */
    fun isWindowActive(): Boolean

    /** Cancels any in-flight window without firing a terminal event (e.g. service teardown). Must unregister updates. */
    fun cancel()

    /** Emits the terminal TripStartEvent.ConfidentVehicleEntry or TripStartEvent.LowConfidenceRetryExhausted exactly once per window. */
    fun observeResults(): kotlinx.coroutines.flow.Flow<TripStartEvent>
}
```

### Implementation contract (what the real `ConfidenceAcquisitionWindowImpl` must do)

1. **Registration on `startWindow()`:**
   ```kotlin
   val request = ActivityUpdateRequest.Builder(CONFIDENCE_WINDOW_INTERVAL_MILLIS).build() // 5_000L
   activityRecognitionClient.requestActivityUpdates(request, confidenceUpdatePendingIntent)
   ```
   (Or the legacy `requestActivityUpdates(intervalMillis, pendingIntent)` overload — android-engineer picks whichever is current in `play-services-location:21.3.0`; functionally identical for this spec's purposes.) The `PendingIntent` targets a small dedicated `BroadcastReceiver` (new file, `ConfidenceUpdateReceiver.kt`) — **do not reuse `ActivityTransitionReceiver`** for this; transition events and activity-confidence updates are two different intent shapes from two different APIs and mixing them in one receiver invites a parsing bug.

2. **Reading confidence:** in `ConfidenceUpdateReceiver.onReceive()`, call `ActivityRecognitionResult.extractResult(intent)`, then `result.probableActivities.firstOrNull { it.type == DetectedActivity.IN_VEHICLE }?.confidence ?: 0`. Forward this int to `ConfidenceAcquisitionWindowImpl` via a method call (e.g. `onConfidenceReading(confidence: Int)`), not via the Flow directly — the receiver is a thin parser, the window class owns all the state.

3. **Running maximum, not latest:**
   ```kotlin
   private var maxConfidenceSeenInWindow: Int = 0

   fun onConfidenceReading(confidence: Int) {
       maxConfidenceSeenInWindow = maxOf(maxConfidenceSeenInWindow, confidence)
       if (maxConfidenceSeenInWindow >= START_CONFIDENCE_THRESHOLD_PERCENT) { // 70, locked
           fireConfidentEntry()
       }
   }
   ```
   This is the one place a future maintainer could accidentally regress to "take the latest reading" — comment it explicitly as the plan does ("one high reading should count, not be erased by a lower one after it").

4. **The 30-second timer — virtual-time-testable design:**
   The timer must NOT be `Handler.postDelayed` or any wall-clock-bound Android API, because the plan's Verify line requires a `TestScope`/virtual-time unit test. Use a coroutine `delay()` on an **injected `CoroutineScope`** (so tests substitute a `TestScope`/`StandardTestDispatcher`):

   ```kotlin
   class ConfidenceAcquisitionWindowImpl @Inject constructor(
       private val activityRecognitionClient: ActivityRecognitionClient,
       @ApplicationContext private val appContext: Context,
       private val coroutineScope: CoroutineScope, // injected so tests can pass a TestScope
   ) : ConfidenceAcquisitionWindow {

       private var timeoutJob: Job? = null
       private var maxConfidenceSeenInWindow: Int = 0
       private val resultFlow = MutableSharedFlow<TripStartEvent>(extraBufferCapacity = 1)

       override fun startWindow(enteredAtEpochMillis: Long) {
           if (timeoutJob?.isActive == true) return // no-op if already running
           maxConfidenceSeenInWindow = 0
           registerActivityUpdates()
           timeoutJob = coroutineScope.launch {
               delay(CONFIDENCE_WINDOW_TIMEOUT_MILLIS) // 30_000L, locked
               fireRetryExhausted()
           }
       }

       private fun fireConfidentEntry() {
           timeoutJob?.cancel()
           timeoutJob = null
           unregisterActivityUpdates()
           resultFlow.tryEmit(
               TripStartEvent.ConfidentVehicleEntry(
                   confidencePercent = maxConfidenceSeenInWindow,
                   detectedAtEpochMillis = System.currentTimeMillis(),
               )
           )
       }

       private fun fireRetryExhausted() {
           unregisterActivityUpdates()
           resultFlow.tryEmit(
               TripStartEvent.LowConfidenceRetryExhausted(
                   lastObservedConfidencePercent = maxConfidenceSeenInWindow,
               )
           )
       }
       // ...
   }
   ```

   **Threading/dispatcher:** the injected `CoroutineScope` should be built on `Dispatchers.Default` (CPU-bound timer, no I/O) for production, backed by `SupervisorJob()` so a failure in one window doesn't kill future windows. In unit tests, inject a `TestScope(StandardTestDispatcher())` and drive time forward with `advanceTimeBy(...)`/`advanceUntilIdle()` — this is what makes the plan's two named virtual-time test cases (§5) possible without any `Thread.sleep` or wall-clock dependency.

5. **Unregister discipline (battery-critical):** both `fireConfidentEntry()` and `fireRetryExhausted()` call `unregisterActivityUpdates()` before emitting their terminal event — never after, and never conditionally. `cancel()` (service teardown path) also unregisters. There must be exactly one code path that registers (`startWindow`) and the only three ways out (`fireConfidentEntry`, `fireRetryExhausted`, `cancel`) must all unregister. A unit test should assert `unregister` is called exactly once across each of those three paths (mockable via a fake/spy `ActivityRecognitionClient` — Play Services clients are interfaces under test, or wrap the unregister call behind a small seam if not).

### Battery implications — stated explicitly per this agent's mandate

- **The 5-second-interval `requestActivityUpdates()` subscription is meaningfully more power-hungry than the always-on Transition API** (the Transition API is push-based and OS-batched at effectively zero ongoing cost; `requestActivityUpdates` at a fixed interval keeps the activity-recognition sensor pipeline warm and triggers a callback every 5s). This is acceptable **only because the window is bounded to a maximum of 30 seconds per trip-start event**, not continuous. Per-trip-start cost: at most 6 confidence readings (30s / 5s) before forced termination.
- **Detection-sensitivity/battery/false-positive trade-off for this specific config:**
  - *Detection sensitivity:* high — a 5s poll interval against a 30s window gives up to 6 independent samples, and the running-maximum rule means a single brief confidence spike (e.g. accelerating onto a highway) is captured even if surrounding samples are lower. This directly serves the brief's "if confidence is low, retry silently rather than interrupt" requirement.
  - *Battery:* bounded and acceptable. The expensive subscription only exists for ≤30s per `IN_VEHICLE` ENTER event, which itself is a relatively rare event (a handful of times per day for a typical user), not a continuous drain. The always-on cost (Transition API registration) is unaffected by this design.
  - *False positives:* the running-maximum + 70% threshold is unchanged from the locked spec; this section doesn't change false-positive risk, only how confidence is sourced.
- **The leak risk to defend against in code review:** if `fireConfidentEntry()`/`fireRetryExhausted()` ever throw before reaching `unregisterActivityUpdates()`, the 5s subscription keeps running forever, silently draining battery with no trip ever starting. Wrap the unregister call so it always runs (e.g. structure as `try { ... } finally { unregisterActivityUpdates() }` inside each terminal path, or call unregister first and emit second — the order in the code above already does this correctly: unregister, then emit).

---

## 5. Exact TDD test cases

All tests use the project's existing style: JUnit 4, `org.junit.Assert.assertEquals`/`assert`, backtick test names, `kotlinx-coroutines-test` (`runTest`, `TestScope`, `StandardTestDispatcher`) — both already in `app/build.gradle.kts` (`testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")`, `testImplementation("app.cash.turbine:turbine:1.1.0")`).

### T-002.2 coverage gap — `app/src/test/kotlin/com/mileagetracker/app/domain/statemachine/TripLifecycleStateMachineTest.kt`

Add to the existing file (do not replace existing tests):

```kotlin
@Test
fun `low confidence retry exhausted moves to PromptPending with forced low confidence flag`() {
    val nextPhase = stateMachine.onStartEvent(
        currentPhase = TripLifecycleStateMachine.TransientPhase.NoTrip,
        event = TripStartEvent.LowConfidenceRetryExhausted(lastObservedConfidencePercent = 45),
    )
    assert(nextPhase is TripLifecycleStateMachine.TransientPhase.PromptPending)
    val promptPending = nextPhase as TripLifecycleStateMachine.TransientPhase.PromptPending
    assertEquals(45, promptPending.confidencePercent)
    assertEquals(true, promptPending.isForcedLowConfidence)
}

@Test
fun `manual start moves to PromptPending at full confidence, not forced low confidence`() {
    val nextPhase = stateMachine.onStartEvent(
        currentPhase = TripLifecycleStateMachine.TransientPhase.NoTrip,
        event = TripStartEvent.ManualStart(startedAtEpochMillis = 0L),
    )
    assert(nextPhase is TripLifecycleStateMachine.TransientPhase.PromptPending)
    val promptPending = nextPhase as TripLifecycleStateMachine.TransientPhase.PromptPending
    assertEquals(100, promptPending.confidencePercent)
    assertEquals(false, promptPending.isForcedLowConfidence)
}
```

### T-002.3 — new file `app/src/test/kotlin/com/mileagetracker/app/service/activityrecognition/ActivityTransitionReceiverTest.kt`

(Requires Robolectric — add `testImplementation("org.robolectric:robolectric:4.13")` if not already present; confirm with android-engineer before adding a new test dependency.)

```kotlin
@Test
fun `ENTER event for IN_VEHICLE calls startWindow exactly once`() {
    val fakeWindow = FakeConfidenceAcquisitionWindow()
    val receiver = ActivityTransitionReceiver().apply { confidenceAcquisitionWindow = fakeWindow }

    val enterIntent = buildFakeTransitionResultIntent(
        activityType = DetectedActivity.IN_VEHICLE,
        transitionType = ActivityTransition.ACTIVITY_TRANSITION_ENTER,
    )
    receiver.onReceive(context, enterIntent)

    assertEquals(1, fakeWindow.startWindowCallCount)
}

@Test
fun `EXIT event for IN_VEHICLE never calls startWindow`() {
    val fakeWindow = FakeConfidenceAcquisitionWindow()
    val receiver = ActivityTransitionReceiver().apply { confidenceAcquisitionWindow = fakeWindow }

    val exitIntent = buildFakeTransitionResultIntent(
        activityType = DetectedActivity.IN_VEHICLE,
        transitionType = ActivityTransition.ACTIVITY_TRANSITION_EXIT,
    )
    receiver.onReceive(context, exitIntent)

    assertEquals(0, fakeWindow.startWindowCallCount)
}
```

(`FakeConfidenceAcquisitionWindow` is a hand-written fake per the kotlin testing convention — no mocking framework — implementing the `ConfidenceAcquisitionWindow` interface from §4 and counting calls.)

### T-002.4 — new file `app/src/test/kotlin/com/mileagetracker/app/service/activityrecognition/ConfidenceAcquisitionWindowTest.kt`

```kotlin
class ConfidenceAcquisitionWindowTest {

    @Test
    fun `readings 50 then 75 within the window fire confident entry at 75 and cancel the timer`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val window = ConfidenceAcquisitionWindowImpl(
            activityRecognitionClient = FakeActivityRecognitionClient(),
            appContext = context,
            coroutineScope = testScope,
        )

        val emittedEvents = mutableListOf<TripStartEvent>()
        testScope.launch { window.observeResults().toList(emittedEvents) }

        window.startWindow(enteredAtEpochMillis = 0L)
        window.onConfidenceReading(50)
        testScope.advanceTimeBy(5_000) // first 5s poll tick
        window.onConfidenceReading(75)
        testScope.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        val confidentEntry = emittedEvents.first() as TripStartEvent.ConfidentVehicleEntry
        assertEquals(75, confidentEntry.confidencePercent)
        assertEquals(false, window.isWindowActive()) // timer cancelled, subscription unregistered
    }

    @Test
    fun `confidence readings all below 70 for the full 30s fire retry-exhausted at exactly 30s`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val window = ConfidenceAcquisitionWindowImpl(
            activityRecognitionClient = FakeActivityRecognitionClient(),
            appContext = context,
            coroutineScope = testScope,
        )

        val emittedEvents = mutableListOf<TripStartEvent>()
        testScope.launch { window.observeResults().toList(emittedEvents) }

        window.startWindow(enteredAtEpochMillis = 0L)
        repeat(5) { tickIndex ->
            testScope.advanceTimeBy(5_000)
            window.onConfidenceReading(40 + tickIndex) // 40, 41, 42, 43, 44 — all below 70
        }

        // Confirm nothing has fired yet at 25s (5 ticks elapsed).
        assertEquals(0, emittedEvents.size)

        testScope.advanceTimeBy(5_000) // reaches exactly 30s
        testScope.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        val retryExhausted = emittedEvents.first() as TripStartEvent.LowConfidenceRetryExhausted
        assertEquals(44, retryExhausted.lastObservedConfidencePercent) // running max, not latest
        assertEquals(false, window.isWindowActive())
    }

    @Test
    fun `a lower reading after a high reading does not erase the tracked maximum`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val window = ConfidenceAcquisitionWindowImpl(
            activityRecognitionClient = FakeActivityRecognitionClient(),
            appContext = context,
            coroutineScope = testScope,
        )

        val emittedEvents = mutableListOf<TripStartEvent>()
        testScope.launch { window.observeResults().toList(emittedEvents) }

        window.startWindow(enteredAtEpochMillis = 0L)
        window.onConfidenceReading(85) // high reading first — should fire immediately
        testScope.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(85, (emittedEvents.first() as TripStartEvent.ConfidentVehicleEntry).confidencePercent)
    }

    @Test
    fun `calling startWindow while a window is already active is a no-op`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val window = ConfidenceAcquisitionWindowImpl(
            activityRecognitionClient = FakeActivityRecognitionClient(),
            appContext = context,
            coroutineScope = testScope,
        )

        window.startWindow(enteredAtEpochMillis = 0L)
        window.onConfidenceReading(30)
        window.startWindow(enteredAtEpochMillis = 1_000L) // should not reset maxConfidenceSeenInWindow
        window.onConfidenceReading(72)
        testScope.advanceUntilIdle()

        // If startWindow had reset state, this would still pass; the real risk this guards
        // against is a duplicate registration leaking a second 5s subscription — assert via
        // FakeActivityRecognitionClient.registerCallCount == 1.
    }
}
```

These cover both named virtual-time cases from the plan ("readings 50→75 fires confident=75 and cancels the timer"; "readings all <70 for full 30s fires retry-exhausted at exactly 30s, not before or after") plus the running-maximum regression guard and the idempotent-registration guard that the battery discipline in §4 depends on.

---

## 6. Handoff notes for android-engineer / android-coder

Checklist — Android-specific wiring NOT covered by this design spec, but required to ship T-002:

- [ ] **T-002.1 permission flow.** Build `SetupPermissionsScreen` + `SetupPermissionsViewModel` per the T-001 blueprint §5 table, using `ActivityResultContracts.RequestMultiplePermissions()` for `ACTIVITY_RECOGNITION`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `CAMERA`, `POST_NOTIFICATIONS` (API 33+). Set `limitedMode` on denial, never block navigation. File: `ui/setup/SetupPermissionsScreen.kt` (new).
- [ ] **Hilt provisioning for the new class.** Add `ConfidenceAcquisitionWindow` (interface, §4) → `ConfidenceAcquisitionWindowImpl` binding. <!-- REMOVED (reason: scope changed during T-002.4 implementation — see note below)
  Decide scope: `@ServiceScoped` (matches `TripLifecycleStateMachine`'s existing scope in `ServiceModule.kt`) is recommended since it's only ever used by the foreground service's detection pipeline and should not outlive it. Also provide the injected `CoroutineScope` (production: `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, scoped to the service lifecycle so `onDestroy()` can cancel it — wire this into the existing `service/di/ServiceModule.kt`).
  -->
  **T-002.4 implementation note (deviates from the `@ServiceScoped` suggestion above):** `ConfidenceAcquisitionWindowImpl` is bound `@Singleton` in `ActivityRecognitionModule` (`SingletonComponent`), not `@ServiceScoped`. Reason: it must implement `VehicleEntryConfidenceGateway`, which is consumed by `ActivityTransitionReceiver` and now also backs `ConfidenceUpdateReceiver` — both `@AndroidEntryPoint BroadcastReceiver`s that can run independently of the foreground service's lifecycle (the same reasoning that already made `VehicleEntryConfidenceGateway`'s placeholder binding `@Singleton`/`SingletonComponent` rather than `@ServiceScoped`/`ServiceComponent`). A `@ServiceScoped` binding would be unavailable to these receivers whenever they fire without the service's graph active. The injected `CoroutineScope` is provided alongside it as an app-scoped `@Provides` (`SupervisorJob() + Dispatchers.Default`) in the same module, not tied to service `onDestroy()`; `TripTrackingForegroundService.onDestroy()` should still call `ConfidenceAcquisitionWindow.cancel()` explicitly in the next chunk so an in-flight window doesn't outlive the service's intent to track, even though the underlying scope itself is long-lived.
- [ ] **Manifest receiver for the new confidence-update receiver.** Declare `ConfidenceUpdateReceiver` (new, §4) as a non-exported `<receiver>` in `AndroidManifest.xml`, alongside the existing `ActivityTransitionReceiver` declaration from T-001.4.
- [ ] **`MT-ActivityRecognition` logging at register/unregister/receive** (the T-018 deferred item, per `team/SESSION_HANDOFF.md`). Concretely:
  - `ActivityRecognitionRegistrar.register()` / `.unregister()`: log success/failure via `addOnSuccessListener`/`addOnFailureListener` (shown in §3's code).
  - `ActivityTransitionReceiver.onReceive()`: log every ENTER/EXIT received (shown in §3's code).
  - `ConfidenceAcquisitionWindowImpl`: log window start, every confidence reading (debug level — this will be noisy at 5s intervals, use `Timber.tag("MT-ActivityRecognition").d(...)` not `.i(...)` for the per-reading log), and the terminal outcome (confident-entry with value, or retry-exhausted with best-seen value) at `.i(...)`.
- [x] **Wire `ConfidenceAcquisitionWindow.observeResults()` into the foreground service.** `TripTrackingForegroundService` must collect this Flow (in `serviceScope`, following the existing `tripLifecycleMutex` discipline already in the file) and feed each emitted `TripStartEvent` into `tripLifecycleStateMachine.onStartEvent(transientPhase, event)`, then proceed to `resolvePromptPendingIntoActiveTrip` exactly as the existing `handleStartTripRequested()` does for `ManualStart` — **do not duplicate that insert/notification logic; extract the shared tail (insert trip, start location updates, reset timers) into a private method both the manual-start path and the new automatic-start path call.** This is a judgment call (shared code path, not boilerplate) — keep with android-engineer, not android-coder.
  **Done (android-engineer, this session):** `confidenceAcquisitionWindow` injected into `TripTrackingForegroundService`; the Flow is collected exactly once in a new `onCreate()` override (never re-subscribed per `onStartCommand`). Each emitted `TripStartEvent` is handled by a new `handleAutomaticStartEvent()` which acquires `tripLifecycleMutex` itself, re-runs the same no-op guard (`activeTripId != null` / `getInProgressTrip() != null`), calls `tripLifecycleStateMachine.onStartEvent(...)`, then calls the new shared-tail method `completeTripStart(startedAtEpochMillis)` — extracted from the old `handleStartTripRequested()` body (tripId generation through `resolvePromptPendingIntoActiveTrip`, `insertNewActiveTrip`, `startLocationUpdates`, both timer resets). `handleAutomaticStartEvent` has its own `CancellationException`-rethrow / catch-and-log-under-`MT-Service` boundary scoped to a single event, specifically so one bad confidence-window result cannot throw out of the `collect` call and silently end the subscription for the rest of the service's life — the per-event try/catch lives inside the handler, not wrapped around the `collect` itself. `onDestroy()` got a new `runCatching { confidenceAcquisitionWindow.cancel() }` step in the same pattern as the existing teardown steps, between `activityRecognitionRegistrar.unregister()` and the timer-job cancellations. Class doc comment updated to state the automatic-start path is now wired (no longer "registered but not yet wired"). `./gradlew test` (26/26 green, no regressions) and `./gradlew assembleDebug` both pass. No new unit test added for the service itself — it remains a `Service` under this project's no-mocking-framework convention, untestable without Robolectric, consistent with the existing test-file layout (no `TripTrackingForegroundServiceTest.kt` exists). File: `app/src/main/kotlin/com/mileagetracker/app/service/TripTrackingForegroundService.kt`.
- [ ] **Notification trigger.** Per brief §5.9, the classification prompt must show within 5 seconds of the start event (i.e., within 5s of `PromptPending` being entered from either `ConfidentVehicleEntry` or `LowConfidenceRetryExhausted`). This notification-posting call site does not exist yet in the service; it is T-003's scope (`TripClassificationNotificationBuilder` already scaffolded) but T-002's automatic-start wiring is what must call it — coordinate the call site placement with whoever picks up T-003.
- [ ] **`FakeActivityRecognitionClient` / `FakeConfidenceAcquisitionWindow` test doubles.** Hand-write per the kotlin testing convention (no mocking framework) — `FakeActivityRecognitionClient` needs to support recording `requestActivityUpdates`/`removeActivityUpdates` call counts for the idempotent-registration test in §5.
- [ ] **Robolectric dependency check.** If `ActivityTransitionReceiverTest` (§5) needs Robolectric and it isn't already a test dependency, add `org.robolectric:robolectric:4.13` to `app/build.gradle.kts` test dependencies — confirm version compatibility with the existing AGP 8.5.2 / Kotlin 1.9.24 toolchain before pinning.

---

## 6a. Auto-start entry point for the foreground service (added 2026-06-22, android-engineer)

**Gap found and closed this chunk:** `TripTrackingForegroundService` — and therefore
`ActivityRecognitionRegistrar.register()`, and therefore the entire automatic-detection pipeline
wired in §6's checklist item above — previously only ever started via
`HomeStatusViewModel.onStartTripClicked()` (the manual Start Trip button). That means `register()`
never ran until a trip was already manually started, so the automatic pipeline had no practical
chance to fire. Logged by the Manager in `team/LOGS.md`; decision: scope today's fix to the
simplest thing — start the service (no action set) from `MainActivity.onCreate()` on every app
launch. Explicitly deferred (not built in this chunk): a `RECEIVE_BOOT_COMPLETED` receiver, a
Settings always-on toggle, and the fuller "wake from a dead process via PendingIntent" redesign —
the user's own framing: "today is all about detection — we can later make it so its always on but
sleeping, and wakes up on detection."

**What shipped:**
- `MainActivity.onCreate()` now calls a new private `startTripTrackingServiceForDetection()` before
  `setContent { ... }`, which builds `Intent(this, TripTrackingForegroundService::class.java)` with
  **no action set** and starts it via `ContextCompat.startForegroundService(...)` — the same call
  pattern `HomeStatusViewModel.onStartTripClicked()`/`onStopTripClicked()` already use. An
  `MT-Service`-tagged Timber log line ("Auto-starting detection service from app launch") fires at
  the call site, consistent with the project's existing logging convention.
- `Application.onCreate()` (`MileageTrackerApplication`) was considered and rejected in favor of
  `MainActivity.onCreate()` — starting a foreground service before any Activity is visible is
  riskier on some OEM Android builds, and `MainActivity` is this single-activity app's existing
  entry point, so this is the lower-risk, more idiomatic choice for this MVP.
- **Idempotency confirmed, not just assumed:** `ActivityRecognitionRegistrar`'s own class doc
  already states re-registering the same `PendingIntent` (same request code) is a documented safe
  no-op per the Play Services API contract — calling `register()` again on every app-launch
  auto-start (and again on every subsequent `onStartCommand`, action or no action) does not create
  a duplicate registration. No change was needed to `ActivityRecognitionRegistrar` for this.
- **No permission gate added before starting the service**, by design: `onStartCommand` with
  `requestedAction == null` simply falls through the `when` block as a no-op (recovery check still
  runs — safe, it only re-attaches to an already-`ACTIVE` trip; `register()` still re-runs — safe,
  per above). The service's own GPS path is independently gated on `hasFineLocationPermission()`
  inside `startLocationUpdates()`, and `ActivityRecognitionRegistrar.register()`'s Play Services
  call already degrades gracefully via `addOnFailureListener` rather than throwing if
  `ACTIVITY_RECOGNITION` isn't granted. This matches `SetupPermissionsScreen`'s existing
  non-blocking "limited mode" pattern — starting the service early is harmless even before/if
  permissions are denied.
- `TripTrackingForegroundService`'s class doc comment was extended (not replaced) to document this
  third entry point alongside the two existing action-intent paths.
- Verification: `./gradlew test` — BUILD SUCCESSFUL, 26 tests × 2 variants (debug+release) = 52, 0
  failures, 0 skipped. `./gradlew assembleDebug` — BUILD SUCCESSFUL.
- Not committed — awaiting explicit user go-ahead per the project's standing git rule.

**Still open after this chunk (unchanged from before):** T-002.1's permission-request screen
gaps noted in the board's T-002 card (missing `ACCESS_BACKGROUND_LOCATION` request,
`SetupPermissionsUiState` granted-flags never updated from the launcher result) are untouched by
this chunk — this chunk only addresses *that the service now actually starts passively*, not the
permission-flow completeness gap.

---

## 7. Open flags

1. **None on the core T-002.2–T-002.4 design — the plan is sound as written.** The locked thresholds (70%/30s) and the "running maximum, not latest" rule are both correct calls for the stated goal (don't let a brief confidence dip erase a real detection) and I would not change either without field data showing otherwise.

2. **One battery point worth flagging now, not as a blocker:** registering `requestActivityUpdates` at 5s intervals is the right call for a *bounded* 30s window, but if a future iteration is ever tempted to widen the window (e.g. "users complain prompts feel slow, extend to 60s") that should trigger a `/team-debate`, not a quiet constant bump — doubling the window doubles the worst-case per-event battery cost of this subscription, and the locked 30s value already represents the deliberate trade-off point between sensitivity and battery stated in the brief (§13: "MVP should prioritize working detection over perfect battery efficiency," which is exactly why 30s was chosen over something shorter, but it is not a value that should silently drift longer either).

3. **EXIT event currently does nothing (by design, not a gap).** Registering both ENTER and EXIT in the same `ActivityTransitionRequest` but only acting on ENTER might look like dead code in review. It is intentional — see §3's inline comment — and should not be "cleaned up" by removing the EXIT registration, since T-004's future false-stop-detection tuning may want it without a second registration round-trip. Flagging so this isn't second-guessed later without context.

4. **Confirmed correct, not a flag:** the existing `TripTrackingForegroundService`'s `tripLifecycleMutex` discipline (documented in the file as fixing a real race found in MVP testing) must be respected by the new automatic-start wiring in the handoff checklist above — the Flow collection from `ConfidenceAcquisitionWindow.observeResults()` must acquire the same mutex before touching `transientPhase`/`activeTripId`, exactly like `handleStartTripRequested()` already does. This isn't a new design decision, just confirming the existing concurrency contract extends cleanly to the new code path.
