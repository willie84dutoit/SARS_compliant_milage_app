package com.mileagetracker.app.domain.repository

import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.repository.TripWriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written fake (no mocking framework, per this project's testing convention) for
 * [TripRepository], used by ViewModel tests that need to drive `observeInProgressTrip()` through
 * a sequence of states (e.g. ACTIVE -> PENDING_OCR) without a real Room database.
 */
class FakeTripRepository : TripRepository {

    private val inProgressTripState = MutableStateFlow<Trip?>(null)
    private val tripHistoryState = MutableStateFlow<List<Trip>>(emptyList())

    /** Test-only setter — pushes a new in-progress-trip value through [observeInProgressTrip]. */
    fun setInProgressTrip(trip: Trip?) {
        inProgressTripState.value = trip
    }

    /** Test-only setter — pushes a new trip-history list through [observeTripHistory]. */
    fun setTripHistory(history: List<Trip>) {
        tripHistoryState.value = history
    }

    override suspend fun getTripById(tripId: String): Trip? {
        return inProgressTripState.value?.takeIf { it.id == tripId }
            ?: tripHistoryState.value.firstOrNull { it.id == tripId }
    }

    override suspend fun getInProgressTrip(): Trip? = inProgressTripState.value

    override fun observeInProgressTrip(): Flow<Trip?> = inProgressTripState

    override fun observeTripHistory(): Flow<List<Trip>> = tripHistoryState

    override suspend fun getCompletedTripsForExport(): List<Trip> {
        return tripHistoryState.value.filter { it.status == TripStatus.COMPLETED }
    }

    override fun observePendingBusinessReasonTrips(): Flow<List<Trip>> {
        return tripHistoryState.map { trips ->
            trips.filter { it.status == TripStatus.PENDING_BUSINESS_REASON }
        }
    }

    override suspend fun insertNewActiveTrip(trip: Trip) {
        inProgressTripState.value = trip
    }

    override suspend fun updateClassification(
        tripId: String,
        classification: TripClassification,
        businessReason: String?,
    ): TripWriteResult {
        val existingTrip = findTrip(tripId) ?: return TripWriteResult.TripNotFound
        if (existingTrip.isSigned) return TripWriteResult.RejectedSignedRow
        updateInPlace(existingTrip.copy(classification = classification, businessReason = businessReason))
        return TripWriteResult.Success
    }

    override suspend fun updateBusinessReason(tripId: String, businessReason: String): TripWriteResult {
        val existingTrip = findTrip(tripId) ?: return TripWriteResult.TripNotFound
        if (existingTrip.isSigned) return TripWriteResult.RejectedSignedRow
        updateInPlace(existingTrip.copy(businessReason = businessReason))
        return TripWriteResult.Success
    }

    override suspend fun updateVerifiedOdometer(tripId: String, verifiedOdometerKm: Double): TripWriteResult {
        val existingTrip = findTrip(tripId) ?: return TripWriteResult.TripNotFound
        if (existingTrip.isSigned) return TripWriteResult.RejectedSignedRow
        updateInPlace(existingTrip.copy(verifiedOdometerKm = verifiedOdometerKm))
        return TripWriteResult.Success
    }

    private fun findTrip(tripId: String): Trip? =
        inProgressTripState.value?.takeIf { it.id == tripId }
            ?: tripHistoryState.value.firstOrNull { it.id == tripId }

    private fun updateInPlace(updatedTrip: Trip) {
        if (inProgressTripState.value?.id == updatedTrip.id) {
            inProgressTripState.value = updatedTrip
        } else {
            tripHistoryState.value = tripHistoryState.value.map {
                if (it.id == updatedTrip.id) updatedTrip else it
            }
        }
    }

    override suspend fun markTripCompleted(tripId: String, signatureBase64: String, signingKeyId: String) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        inProgressTripState.value = null
        tripHistoryState.value = listOf(
            existingTrip.copy(
                status = TripStatus.COMPLETED,
                signatureBase64 = signatureBase64,
                signingKeyId = signingKeyId,
            ),
        ) + tripHistoryState.value
    }

    override suspend fun updateStartLocationIfUnset(tripId: String, latitude: Double, longitude: Double) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        if (existingTrip.startLatitude == null && existingTrip.startLongitude == null) {
            inProgressTripState.value = existingTrip.copy(startLatitude = latitude, startLongitude = longitude)
        }
    }

    override suspend fun updateEndLocation(tripId: String, latitude: Double, longitude: Double) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        inProgressTripState.value = existingTrip.copy(endLatitude = latitude, endLongitude = longitude)
    }

    override suspend fun updateDistanceKm(tripId: String, distanceKm: Double) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        inProgressTripState.value = existingTrip.copy(distanceKm = distanceKm)
    }

    override suspend fun updateStatus(tripId: String, status: TripStatus) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        inProgressTripState.value = existingTrip.copy(status = status)
    }

    override suspend fun updateEndTimestamp(tripId: String, endTimestampEpochMillis: Long) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        inProgressTripState.value = existingTrip.copy(endTimestamp = endTimestampEpochMillis)
    }

    override suspend fun updateSigningFields(
        tripId: String,
        signatureBase64: String,
        signingKeyId: String,
        tripSequenceNumber: Int,
    ) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        inProgressTripState.value = existingTrip.copy(
            signatureBase64 = signatureBase64,
            signingKeyId = signingKeyId,
            tripSequenceNumber = tripSequenceNumber,
        )
    }

    override suspend fun countAssignedSequenceNumbers(excludeTripId: String): Int {
        // Mirrors the DAO query: trip_sequence_number > 0 AND id != excludeTripId.
        // A PENDING_BUSINESS_REASON trip has tripSequenceNumber == 0 until it is signed,
        // so it is correctly excluded without any special-casing on status.
        val inProgressCount = inProgressTripState.value
            ?.let { if (it.tripSequenceNumber > 0 && it.id != excludeTripId) 1 else 0 }
            ?: 0
        val historyCount = tripHistoryState.value.count {
            it.tripSequenceNumber > 0 && it.id != excludeTripId
        }
        return inProgressCount + historyCount
    }

    override suspend fun getMostRecentlySignedTrip(): Trip? {
        val allTrips = listOfNotNull(inProgressTripState.value) + tripHistoryState.value
        return allTrips
            .filter { it.signatureBase64 != null }
            .maxByOrNull { it.tripSequenceNumber }
    }
}
