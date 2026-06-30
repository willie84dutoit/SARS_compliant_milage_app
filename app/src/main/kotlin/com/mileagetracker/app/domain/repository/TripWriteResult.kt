package com.mileagetracker.app.domain.repository

/**
 * T-033: result type returned by content-mutating [TripRepository] writes that are guarded
 * against modifying already-signed rows. Callers must handle all three cases explicitly —
 * none may be swallowed.
 *
 * [Success]            — the row was found, unsigned, and updated normally.
 * [RejectedSignedRow]  — the row exists but already has a non-null signature_base64; the write
 *                        was blocked atomically at the DAO layer. The trip is finalized and must
 *                        not be edited. Log at WARN under "MT-Trip" with tripId + sequence number
 *                        and surface a user-visible message via the screen's existing error channel.
 * [TripNotFound]       — no row matched the given tripId. Log at ERROR under "MT-Repository".
 */
sealed interface TripWriteResult {
    data object Success : TripWriteResult
    data object RejectedSignedRow : TripWriteResult
    data object TripNotFound : TripWriteResult
}
