package com.mileagetracker.app.data.signing

/**
 * T-008 Chunk 3 / T-039: orchestrates the per-trip signing flow at the `completed` transition.
 *
 * This is the public contract callers depend on. The sole production implementation is
 * [TripSigningOrchestratorImpl], bound via Hilt `@Binds` in
 * `com.mileagetracker.app.data.di.RepositoryModule` — mirroring how [com.mileagetracker.app.domain.repository.TripRepository]
 * is bound to `TripRepositoryImpl`. Callers (ViewModels, [com.mileagetracker.app.MileageTrackerApplication])
 * depend on this interface only, never on the implementation class directly.
 *
 * Extracted from the formerly-`open` `TripSigningOrchestrator` class (T-039 item 7) so that
 * production code does not need to stay `open` purely to support test subclassing. Tests now use
 * hand-written fakes implementing this interface instead.
 */
interface TripSigningOrchestrator {

    /**
     * Signs [tripId] and finalizes it as COMPLETED. Safe to call from any coroutine context.
     *
     * Flow (on signing success):
     *   1. Read the current chain tail from DataStore (null for genesis).
     *   2. Count finalized trips to assign a monotonic `Trip.tripSequenceNumber`.
     *   3. Load the trip from Room to populate the canonical payload.
     *   4. Call `TripSigner.signTrip` — returns `TripSigner.SigningResult`.
     *   5. Write signing fields to Room via `TripRepository.updateSigningFields`.
     *   6. Call `TripRepository.markTripCompleted` (flips status).
     *   7. Compute SHA-256 of the Base64 signature and advance the DataStore tail.
     *
     * Flow (on signing failure or trip not found):
     *   - `TripRepository.markTripCompleted` is still called with empty string arguments
     *     representing "no signature" — the repository writes null to both signing columns.
     *     The DataStore tail is NOT advanced (a null-signed trip cannot be a valid chain link).
     */
    suspend fun signAndFinalizeTrip(tripId: String)

    /**
     * T-008 Chunk 4: cold-start self-heal. Called once from
     * [com.mileagetracker.app.MileageTrackerApplication.onCreate] before any user interaction is
     * possible.
     *
     * The DataStore tail hash is a derived, rebuildable cache — Room is the durability anchor.
     * This function queries the most-recently-signed Room trip and recomputes the tail from its
     * signature, overwriting whatever DataStore currently holds (which may be stale after a crash
     * between the Room write and the DataStore write, or after an app reinstall that cleared
     * DataStore but not the Room database).
     *
     * If no signed trip exists yet (first ever launch, or all trips predate signing), the
     * DataStore tail is left null — the next signing call will treat that as the genesis link.
     *
     * THREAT-MODEL LIMITATION (H-1, accepted by design): this self-heal rebuilds the chain tail
     * from whatever signed trips currently survive in Room. It therefore CANNOT detect local
     * truncation or back-dating: if the most recent signed trips are deleted from the device's
     * database, the next cold start simply rebuilds a valid-looking tail from the remaining
     * trips and continues. The hash chain proves that surviving trips were not individually
     * edited after signing (each link's prevTail still matches), but it does NOT prove that NO
     * trips were removed from the end of the chain. Detecting deletion/truncation requires an
     * append-only external witness that records the highest sequence number seen — i.e. the
     * Phase-2 backend gap-analysis (see T-032). On-device alone, end-of-chain truncation is
     * undetectable; do not represent the local logbook as truncation-proof.
     */
    suspend fun rebuildChainTailFromRoom()
}
