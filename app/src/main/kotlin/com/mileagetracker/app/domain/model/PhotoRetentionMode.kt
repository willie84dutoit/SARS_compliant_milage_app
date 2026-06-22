package com.mileagetracker.app.domain.model

/**
 * Snapshot of the `Save odometer photos` setting taken at trip-start time (T-001 blueprint §2)
 * and stored per-trip so a later settings change never reinterprets old trips' photo handling.
 */
enum class PhotoRetentionMode {
    TEMPORARY,
    SAVED,
}
