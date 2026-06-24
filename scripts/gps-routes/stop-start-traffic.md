# stop-start-traffic.json — timing rationale

## What this fixture is testing

Brief §10 scenario #2: a stop-start traffic / red-light pause must **not** trigger
`TripTrackingForegroundService.CONFIRMED_INACTIVITY_TIMEOUT_MILLIS` (locked at 3 minutes /
180 seconds), because real movement resumes before 180 seconds of true inactivity elapses.

This is the highest-risk false-positive case for trip-stop detection: if the inactivity timer
fires while the driver is just waiting at a red light or stuck in slow traffic, the app will
incorrectly end the trip mid-journey.

## Why the timing is deliberate

| Waypoint | Coordinate change vs. previous | Gap before this point | Cumulative time since last accepted movement |
|---|---|---|---|
| wp01-start | first fix (anchors) | 0s | — |
| wp02-approach | ~31m | 15s | 0s (just accepted) |
| wp03-approach | ~31m | 15s | 0s (just accepted) |
| wp04-redlight-stop | ~31m (still moving, this is the last fix before the stop) | 15s | 0s (just accepted) |
| wp05-still-stopped | **0m — identical lat/lng to wp04** | **130s** | 130s of true inactivity |
| wp06-resume | ~31m (movement resumes) | 15s | resets to 0s at 130s + 15s = 145s total elapsed since wp04 |

The critical gap is **wp04 → wp05 → wp06**: the car sits at the exact same coordinate for
130 seconds (wp05's `delaySecondsAfterPrevious`), then moves again 15 seconds later. The total
elapsed wall-clock time between the last *accepted* movement (wp04) and the next *accepted*
movement (wp06) is 130 + 15 = **145 seconds — under the 180-second confirmed-inactivity
threshold**, with a 35-second margin to absorb adb injection latency and emulator/scheduler
jitter without crossing the line by accident.

`GpsAnchorTracker.evaluateFix()` will classify wp05 as `Outcome.Discarded` (delta = 0.0m,
below the 8.0m `GPS_NOISE_FLOOR_METERS`), so it does **not** reset
`resetInactivityTimer()` — only `onAcceptedMovement` does that, per
`TripTrackingForegroundService.onAcceptedMovement`. The inactivity timer that was last reset by
wp04's accepted fix is therefore still running through the entire 130s stop and is still inside
its 180s window when wp06 arrives and resets it again.

It is NOT being tested against `UNSTABLE_SIGNAL_TIMEOUT_MILLIS` (120s) deliberately: that timer
is reset by `onAnyLocationCallbackReceived()`, which fires on *every* location callback
regardless of whether the fix is discarded as noise — so as long as fixes keep arriving on
schedule (which this fixture does, every 15s except the one intentional 130s gap), the
unstable-signal timer is never at risk here. This fixture isolates the inactivity-timer risk
only.

## Expected result

No stop event fires. The trip continues uninterrupted through wp10-end. If a
`ConfirmedInactivity` stop notification appears in `adb logcat` (`MT-Trip`/`MT-Service` tags)
during or shortly after this fixture's replay, that is a regression — the false-stop problem
this harness exists to catch.
