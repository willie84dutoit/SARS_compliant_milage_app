package com.mileagetracker.app.domain.repository

import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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
        return MutableStateFlow(tripHistoryState.value.filter { it.status == TripStatus.PENDING_BUSINESS_REASON })
    }

    override suspend fun insertNewActiveTrip(trip: Trip) {
        inProgressTripState.value = trip
    }

    override suspend fun updateClassification(
        tripId: String,
        classification: TripClassification,
        businessReason: String?,
    ) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        inProgressTripState.value = existingTrip.copy(classification = classification, businessReason = businessReason)
    }

    override suspend fun updateBusinessReason(tripId: String, businessReason: String) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        inProgressTripState.value = existingTrip.copy(businessReason = businessReason)
    }

    override suspend fun updateVerifiedOdometer(tripId: String, verifiedOdometerKm: Double) {
        val existingTrip = inProgressTripState.value?.takeIf { it.id == tripId } ?: return
        inProgressTripState.value = existingTrip.copy(verifiedOdometerKm = verifiedOdometerKm)
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

    override suspend fun countFinalizedTrips(): Int {
        val inProgressCount = inProgressTripState.value
            ?.let { if (it.status == TripStatus.COMPLETED || it.status == TripStatus.PENDING_BUSINESS_REASON) 1 else 0 }
            ?: 0
        val historyCount = tripHistoryState.value.count {
            it.status == TripStatus.COMPLETED || it.status == TripStatus.PENDING_BUSINESS_REASON
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
