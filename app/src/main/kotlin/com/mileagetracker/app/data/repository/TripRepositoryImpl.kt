package com.mileagetracker.app.data.repository

import com.mileagetracker.app.data.local.TripDao
import com.mileagetracker.app.data.local.TripEntity
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.repository.TripRepository
import com.mileagetracker.app.domain.repository.TripWriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Room-backed implementation of [TripRepository]. This is the only place that converts between
 * [TripEntity] (Room) and [Trip] (domain) — ViewModels never see [TripEntity] directly (T-001
 * blueprint §5 hard rule).
 *
 * [updatedAt] is set explicitly on every write here, per the blueprint §2 note that Room does
 * not do this automatically.
 */
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao,
) : TripRepository {

    override suspend fun getTripById(tripId: String): Trip? {
        return tripDao.getTripById(tripId)?.toDomain()
    }

    override suspend fun getInProgressTrip(): Trip? {
        return tripDao.getInProgressTrip()?.toDomain()
    }

    override fun observeInProgressTrip(): Flow<Trip?> {
        return tripDao.observeInProgressTrip().map { it?.toDomain() }
    }

    override fun observeTripHistory(): Flow<List<Trip>> {
        return tripDao.observeTripHistory().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getCompletedTripsForExport(): List<Trip> {
        return tripDao.getCompletedTripsForExport().map { it.toDomain() }
    }

    override fun observePendingBusinessReasonTrips(): Flow<List<Trip>> {
        return tripDao.observePendingBusinessReasonTrips().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun insertNewActiveTrip(trip: Trip) {
        tripDao.insertTrip(trip.toEntity())
    }

    /**
     * T-033: guarded classification write. The atomic DAO UPDATE only fires when
     * signature_base64 IS NULL — a signed row is left untouched and [TripWriteResult.RejectedSignedRow]
     * is returned. Prefer this guarded @Query over the whole-entity @Update to avoid TOCTOU between
     * a read and write and to prevent accidentally overwriting signing fields.
     */
    override suspend fun updateClassification(
        tripId: String,
        classification: TripClassification,
        businessReason: String?,
    ): TripWriteResult {
        val rowsAffected = tripDao.updateClassificationGuarded(
            tripId = tripId,
            classification = classification,
            businessReason = businessReason,
            updatedAt = System.currentTimeMillis(),
        )
        return interpretGuardedWriteResult(tripId = tripId, rowsAffected = rowsAffected, caller = "updateClassification")
    }

    /**
     * T-033: guarded business-reason write — blocked on signed rows.
     */
    override suspend fun updateBusinessReason(tripId: String, businessReason: String): TripWriteResult {
        val rowsAffected = tripDao.updateBusinessReasonGuarded(
            tripId = tripId,
            businessReason = businessReason,
            updatedAt = System.currentTimeMillis(),
        )
        return interpretGuardedWriteResult(tripId = tripId, rowsAffected = rowsAffected, caller = "updateBusinessReason")
    }

    /**
     * T-033: guarded verified-odometer write — blocked on signed rows. Uses a dedicated atomic
     * @Query UPDATE rather than a read-modify-write @Update to avoid TOCTOU and prevent
     * overwriting signing fields on a row that got signed between the read and the write.
     */
    override suspend fun updateVerifiedOdometer(tripId: String, verifiedOdometerKm: Double): TripWriteResult {
        val rowsAffected = tripDao.updateVerifiedOdometerGuarded(
            tripId = tripId,
            verifiedOdometerKm = verifiedOdometerKm,
            updatedAt = System.currentTimeMillis(),
        )
        return interpretGuardedWriteResult(tripId = tripId, rowsAffected = rowsAffected, caller = "updateVerifiedOdometer")
    }

    /**
     * Resolves the result of a guarded (signature_base64 IS NULL) UPDATE that returned [rowsAffected].
     * When zero rows were affected the trip is fetched once to distinguish "not found" from "signed".
     * Logs at the appropriate level and returns the correct [TripWriteResult] — never swallows.
     */
    private suspend fun interpretGuardedWriteResult(tripId: String, rowsAffected: Int, caller: String): TripWriteResult {
        if (rowsAffected > 0) return TripWriteResult.Success
        val tripRow = tripDao.getTripById(tripId)
        return if (tripRow == null) {
            Timber.tag("MT-Repository").e("%s: tripId=%s not found in Room", caller, tripId)
            TripWriteResult.TripNotFound
        } else {
            Timber.tag("MT-Trip").w(
                "%s: REJECTED — row is already signed (tripId=%s sequenceNumber=%d)",
                caller,
                tripId,
                tripRow.tripSequenceNumber,
            )
            TripWriteResult.RejectedSignedRow
        }
    }

    override suspend fun markTripCompleted(tripId: String, signatureBase64: String, signingKeyId: String) {
        val existingTrip = tripDao.getTripById(tripId) ?: run {
            Timber.tag("MT-Repository").e("markTripCompleted called for unknown tripId=%s", tripId)
            throw IllegalStateException("markTripCompleted called for unknown tripId=$tripId")
        }
        // Empty strings from TripSigningOrchestrator.finalizeWithoutSignature() map to null
        // in the DB — preserves a uniform interface while keeping null semantics in the schema.
        val storedSignatureBase64 = signatureBase64.ifEmpty { null }
        val storedSigningKeyId = signingKeyId.ifEmpty { null }
        tripDao.updateTrip(
            existingTrip.copy(
                status = TripStatus.COMPLETED,
                signatureBase64 = storedSignatureBase64,
                signingKeyId = storedSigningKeyId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateSigningFields(
        tripId: String,
        signatureBase64: String,
        signingKeyId: String,
        tripSequenceNumber: Int,
    ) {
        tripDao.updateSigningFields(
            tripId = tripId,
            signatureBase64 = signatureBase64,
            signingKeyId = signingKeyId,
            tripSequenceNumber = tripSequenceNumber,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun countAssignedSequenceNumbers(excludeTripId: String): Int =
        tripDao.countAssignedSequenceNumbers(excludeTripId)

    override suspend fun getMostRecentlySignedTrip(): Trip? =
        tripDao.getMostRecentlySignedTrip()?.toDomain()

    override suspend fun updateStartLocationIfUnset(tripId: String, latitude: Double, longitude: Double) {
        tripDao.updateStartLocationIfUnset(tripId, latitude, longitude, updatedAt = System.currentTimeMillis())
    }

    /**
     * T-033: guarded — end-location is a content field; blocked on signed rows.
     * Result is intentionally dropped here: the foreground service is the sole caller, and a
     * signed row at this point means the trip completed concurrently (harmless race; no UI action needed).
     */
    override suspend fun updateEndLocation(tripId: String, latitude: Double, longitude: Double) {
        val rowsAffected = tripDao.updateEndLocationGuarded(tripId, latitude, longitude, updatedAt = System.currentTimeMillis())
        if (rowsAffected == 0) {
            Timber.tag("MT-Trip").w("updateEndLocation: 0 rows affected for tripId=%s (signed or not found)", tripId)
        }
    }

    /**
     * T-033: guarded — distance is a content field; blocked on signed rows.
     */
    override suspend fun updateDistanceKm(tripId: String, distanceKm: Double) {
        val rowsAffected = tripDao.updateDistanceKmGuarded(tripId, distanceKm, updatedAt = System.currentTimeMillis())
        if (rowsAffected == 0) {
            Timber.tag("MT-Trip").w("updateDistanceKm: 0 rows affected for tripId=%s (signed or not found)", tripId)
        }
    }

    override suspend fun updateStatus(tripId: String, status: TripStatus) {
        tripDao.updateTripStatus(tripId, status, updatedAt = System.currentTimeMillis())
    }

    /**
     * T-033: guarded — end-timestamp is a content field; blocked on signed rows.
     */
    override suspend fun updateEndTimestamp(tripId: String, endTimestampEpochMillis: Long) {
        val rowsAffected = tripDao.updateEndTimestampGuarded(tripId, endTimestampEpochMillis, updatedAt = System.currentTimeMillis())
        if (rowsAffected == 0) {
            Timber.tag("MT-Trip").w("updateEndTimestamp: 0 rows affected for tripId=%s (signed or not found)", tripId)
        }
    }

    private fun TripEntity.toDomain(): Trip = Trip(
        id = id,
        classification = classification,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        startOdometerKm = startOdometerKm,
        endOdometerKm = endOdometerKm,
        verifiedOdometerKm = verifiedOdometerKm,
        distanceKm = distanceKm,
        businessReason = businessReason,
        startLatitude = startLatitude,
        startLongitude = startLongitude,
        endLatitude = endLatitude,
        endLongitude = endLongitude,
        status = status,
        photoRetention = photoRetention,
        createdAt = createdAt,
        updatedAt = updatedAt,
        signatureBase64 = signatureBase64,
        signingKeyId = signingKeyId,
        tripSequenceNumber = tripSequenceNumber,
        isManualStart = isManualStart,
    )

    private fun Trip.toEntity(): TripEntity = TripEntity(
        id = id,
        classification = classification,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        startOdometerKm = startOdometerKm,
        endOdometerKm = endOdometerKm,
        verifiedOdometerKm = verifiedOdometerKm,
        distanceKm = distanceKm,
        businessReason = businessReason,
        startLatitude = startLatitude,
        startLongitude = startLongitude,
        endLatitude = endLatitude,
        endLongitude = endLongitude,
        status = status,
        photoRetention = photoRetention,
        createdAt = createdAt,
        updatedAt = updatedAt,
        signatureBase64 = signatureBase64,
        signingKeyId = signingKeyId,
        tripSequenceNumber = tripSequenceNumber,
        isManualStart = isManualStart,
    )
}
