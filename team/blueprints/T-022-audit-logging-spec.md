# T-022 — Full interaction + persistence audit logging — Implementation Spec

> **Status:** Design complete and reference-implemented in `HomeStatusViewModel`. Ready for
> `android-coder` to replicate mechanically into `TripClassificationViewModel` and
> `OdometerCaptureViewModel` (and, for completeness, audit `SettingsViewModel`/`ExportViewModel`).
> **Owner of this document:** android-engineer. **Builds on:** T-018's `MT-*` Timber/file-logging
> convention (`FileLoggingTree`, `MileageTrackerApplication.onCreate`) — read that first if you
> haven't; this spec only adds two new tags on top of it.
> No-delete rule applies — strike through, don't remove, if a value changes later.

---

## 1. Why this exists

T-018 gave every **service/data**-layer class (`TripTrackingForegroundService`, the
ActivityRecognition pipeline, OCR, CSV export, `TripRepositoryImpl`) a Timber tag and a rotating
on-device log file. It has **zero coverage of screen-level user actions** — no record of which
screen was shown, what button was pressed, what was typed into a field, or (at the ViewModel
layer) what data was read from / about to be written to the database as a consequence.

During the user's first real sideload test, this blind spot meant the debug log could not answer
"what did the user actually do" — it could only show the service stayed healthy underneath. The
back-loop bug (see `team/TASKS.md` T-022 card) was diagnosed by reading source, not by reading
the log, which is exactly backwards for a field-debuggability tool.

**Definition of done:** from the exported log file alone (no source-reading), you can reconstruct
the full sequence of screen actions and the DB reads/writes they triggered.

---

## 2. Tag scheme (final — use exactly these two new tags)

| Tag | Layer | What goes under it |
|---|---|---|
| `MT-UI` | ViewModel, on every user-initiated action | Button clicks, field-text changes, screen-level navigation decisions made by the ViewModel (e.g. "auto-navigated because X"). One line per discrete user action. |
| `MT-Trip` | ViewModel, immediately after an `MT-UI` line, when that action implies a DB read/write | What was read from `TripRepository`/`TripPhotoRepository` to decide what to do, and/or what write call is about to be dispatched. If the actual write happens in a different layer (e.g. inside the foreground service, not in this ViewModel), say so explicitly and point to which tag picks up the trail there (`MT-Service`/`MT-Repository`). |

Do **not** invent a third tag per screen (no `MT-Home`, `MT-Classification`, etc.) — `MT-UI`/
`MT-Trip` apply uniformly across every ViewModel; the *message text* names the screen and the
specific field/button, not the tag.

This keeps the scheme consistent with T-018's existing tags (`MT-Service`, `MT-ActivityRecognition`,
`MT-Location`, `MT-OCR`, `MT-Export`, `MT-Repository`) — same `Timber.tag("MT-Xxx")` call shape,
same file sink, same rotation, nothing new to wire up.

---

## 3. Log line shape (copy this exactly)

```kotlin
Timber.tag("MT-UI").i("<ScreenName>: <action> <relevant identifiers>")
Timber.tag("MT-Trip").i("<what was read/about to be written>; <where the actual write happens, if not here>")
```

Rules:
- Always name the screen by its Compose screen class name (e.g. `HomeStatusScreen`,
  `TripClassificationScreen`, `OdometerCaptureScreen`) at the start of the `MT-UI` message — this
  is what makes the log greppable per-screen without reading source.
- Always include the `tripId` when one is in scope (use `%s` placeholders, not string
  interpolation, matching the existing T-018 style — see `TripRepositoryImpl`'s
  `Timber.tag("MT-Repository").e("... tripId=%s", tripId)` calls).
- Field-text changes (e.g. business-reason typing) are logged at a coarser grain than "every
  keystroke" — log the **final value at save time**, not every `onValueChange` call, to avoid
  flooding the 2 MB rotating log file with per-keystroke noise. (Exception: if a screen has no
  explicit "Save" step and the value commits on every change, log it on change — use judgment,
  but default to logging at the commit point.)
- Use `.i(...)` (INFO) for normal user actions; reserve `.e(...)` for actual error paths (e.g.
  validation failure), consistent with T-018's existing severity usage.
- If the ViewModel only *dispatches* an action (e.g. sends a `Intent` to a foreground service) and
  the real DB write happens elsewhere, say so in the `MT-Trip` line — don't claim a write happened
  in this layer when it didn't. This is the single most important rule: the trail must be
  *honest* about where the actual persistence boundary is, even when it's not in the ViewModel.

---

## 4. Reference implementation — `HomeStatusViewModel`

Already implemented in `app/src/main/kotlin/com/mileagetracker/app/ui/home/HomeStatusViewModel.kt`.
Read it directly for the literal pattern; summarized here:

```kotlin
fun onStartTripClicked() {
    Timber.tag("MT-UI").i("HomeStatusScreen: Start trip button clicked")
    val startIntent = Intent(appContext, TripTrackingForegroundService::class.java).apply {
        action = TripTrackingForegroundService.ACTION_START_TRIP
    }
    Timber.tag("MT-Trip").i(
        "Dispatching ACTION_START_TRIP to TripTrackingForegroundService; the trip insert " +
            "(TripRepository.insertNewActiveTrip) happens inside the service, not here — see " +
            "MT-Service/MT-Repository log lines for the resulting DB write",
    )
    ContextCompat.startForegroundService(appContext, startIntent)
}
```

Note the `MT-Trip` line is explicit that the real `insertNewActiveTrip` call happens inside
`TripTrackingForegroundService`, not in this ViewModel — this is the "honest about the boundary"
rule from §3 in action. `HomeStatusViewModel` only ever dispatches an `Intent`; it never calls
`TripRepository` write methods directly (those live in the service, per the T-001 blueprint §6.1
ViewModel/service boundary). The actual DB write for Start/Stop Trip is logged where it happens —
inside `TripTrackingForegroundService` and `TripRepositoryImpl` — under the existing `MT-Service`/
`MT-Repository` tags. Nothing new needed there; this spec does not touch the service layer.

Also reference-implemented: the T-022 back-loop fix itself
(`onTripClassificationAutoRouted`/`onResumeClassificationClicked`) logs the auto-navigation
decision and the manual resume action under `MT-UI`, which is exactly the kind of
"navigation decision made by the ViewModel" §2's table calls out.

---

## 5. Task list for `android-coder`

### 5.1 `TripClassificationViewModel` (`app/src/main/kotlin/com/mileagetracker/app/ui/classification/TripClassificationViewModel.kt`)

Add `Timber.tag(...)` calls at these exact points — do not add any others, do not change any
existing logic:

1. **`init` block**, after `tripRepository.getTripById(tripId)` resolves (success or null):
   ```kotlin
   Timber.tag("MT-Trip").i("TripClassificationScreen: loaded trip for classification tripId=%s, found=%s", tripId, trip != null)
   ```
2. **`onClassificationSelected`**, first line of the function:
   ```kotlin
   Timber.tag("MT-UI").i("TripClassificationScreen: classification selected tripId=%s, classification=%s", tripId, classification)
   ```
3. **`onSaveClassification`**, immediately after the `classification ?: return` guard (i.e. only
   log once you know there is a non-null classification to act on):
   ```kotlin
   Timber.tag("MT-UI").i("TripClassificationScreen: Save button clicked tripId=%s, classification=%s", tripId, classification)
   ```
4. **`onSaveClassification`**, inside the `if (classification == TripClassification.WORK)`
   validation branch, in the `ValidationResult.Invalid` arm (right before `return`):
   ```kotlin
   Timber.tag("MT-UI").e("TripClassificationScreen: Save blocked by validation tripId=%s, reason=%s", tripId, validationResult.reason)
   ```
5. **`onSaveClassification`**, inside the `viewModelScope.launch { ... }` block, immediately
   before the `tripRepository.updateClassification(...)` call:
   ```kotlin
   Timber.tag("MT-Trip").i(
       "TripClassificationScreen: writing classification tripId=%s, classification=%s, businessReason=%s",
       tripId, classification, businessReasonToStore,
   )
   ```
   This DB write genuinely happens in this ViewModel (`TripRepository.updateClassification` is
   called directly here, unlike Home's service-dispatch pattern) — log it as a real write, not a
   dispatch-only line.

### 5.2 `OdometerCaptureViewModel` (`app/src/main/kotlin/com/mileagetracker/app/ui/odometer/OdometerCaptureViewModel.kt`)

1. **`onManualEntryChanged`**: do **not** log every keystroke (§3's noise rule) — skip this one.
2. **`onConfirmManualOdometer`**, first line of the function, after resolving `manualValueKm`
   (only log if non-null, i.e. after the `?: return` guard):
   ```kotlin
   Timber.tag("MT-UI").i("OdometerCaptureScreen: manual odometer confirmed tripId=%s, valueKm=%s", tripId, manualValueKm)
   ```
3. **`onConfirmManualOdometer`**, inside the `viewModelScope.launch { ... }` block, immediately
   before `tripRepository.updateVerifiedOdometer(...)`:
   ```kotlin
   Timber.tag("MT-Trip").i("OdometerCaptureScreen: writing verifiedOdometerKm=%s for tripId=%s (manual entry)", manualValueKm, tripId)
   ```
4. **`onConfirmOcrResult`**, first line of the function:
   ```kotlin
   Timber.tag("MT-UI").i("OdometerCaptureScreen: OCR result confirmed tripId=%s, valueKm=%s", tripId, valueKm)
   ```
5. **`onConfirmOcrResult`**, inside `viewModelScope.launch { ... }`, immediately before
   `tripRepository.updateVerifiedOdometer(...)`:
   ```kotlin
   Timber.tag("MT-Trip").i("OdometerCaptureScreen: writing verifiedOdometerKm=%s for tripId=%s (OCR-confirmed)", valueKm, tripId)
   ```
6. **`resolvePendingOcrForTrip`** (private helper, called from both confirm paths), after
   `tripRepository.getTripById(tripId)` resolves and after computing `resolvedStatus`, immediately
   before `tripRepository.updateStatus(...)`:
   ```kotlin
   Timber.tag("MT-Trip").i("OdometerCaptureScreen: resolving PENDING_OCR -> %s for tripId=%s", resolvedStatus, tripId)
   ```
   (Note: `OdometerOcrClient`'s own OCR success/failure logging already exists under `MT-OCR` per
   T-018 — do not duplicate that here; this spec's lines are about the screen/ViewModel action and
   the DB write, not the OCR engine call itself.)

### 5.3 `SettingsViewModel` / `ExportViewModel` — audit for completeness

These are **already partially covered** by T-018/T-019 (`MT-Export` tag exists on the CSV writer
and debug-log exporter). Read both ViewModels, list every user-initiated click/toggle that does
**not** yet have an `MT-UI` line in front of it, and add one per the same pattern as §5.1/§5.2.
Do not duplicate or relocate the existing `MT-Export` lines in `CsvFileWriter`/
`DebugLogFileProvider` — those stay exactly where they are; you are only adding the missing
`MT-UI` "user clicked X" lines at the ViewModel layer above them.

### 5.4 What NOT to do

- Do not add `MT-UI`/`MT-Trip` lines inside Compose screens (`*.kt` files under `ui/.../*Screen.kt`)
  — all of this logging belongs in the ViewModel, never in `@Composable` functions. This keeps the
  UI layer free of logging side effects on every recomposition (a `Timber` call inside a
  `@Composable` body would otherwise fire on every recomposition, not just on the actual user
  action).
- Do not change any non-logging logic. If you think a behavior is also a bug, stop and flag it —
  do not fix it silently while doing the logging pass.
- Do not invent additional tags. If you find a genuine gap this spec doesn't cover, stop and ask
  rather than inventing a new tag name.

---

## 6. Verification

After adding the lines above, `./gradlew test` must stay green (these are pure additive
`Timber.tag(...)` calls with no behavior change, so no existing test should need updating). No new
test is required for the logging lines themselves (T-018 already covers `FileLoggingTree`'s
correctness); this spec is about call-site placement, not new infrastructure.
