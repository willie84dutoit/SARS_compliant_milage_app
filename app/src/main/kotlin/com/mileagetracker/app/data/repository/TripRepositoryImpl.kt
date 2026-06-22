package com.mileagetracker.app.data.repository

import com.mileagetracker.app.data.local.TripDao
import com.mileagetracker.app.data.local.TripEntity
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.repository.TripRepository
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

    override suspend fun updateClassification(
        tripId: String,
        classification: TripClassification,
        businessReason: String?,
    ) {
        val existingTrip = tripDao.getTripById(tripId) ?: run {
            Timber.tag("MT-Repository").e("updateClassification called for unknown tripId=%s", tripId)
            throw IllegalStateException("updateClassification called for unknown tripId=$tripId")
        }
        val updatedAt = System.currentTimeMillis()
        tripDao.updateTrip(
            existingTrip.copy(
                classification = classification,
                businessReason = businessReason,
                updatedAt = updatedAt,
            ),
        )
    }

    override suspend fun updateBusinessReason(tripId: String, businessReason: String) {
        tripDao.updateBusinessReason(tripId, businessReason, updatedAt = System.currentTimeMillis())
    }

    override suspend fun updateVerifiedOdometer(tripId: String, verifiedOdometerKm: Double) {
        val existingTrip = tripDao.getTripById(tripId) ?: run {
            Timber.tag("MT-Repository").e("updateVerifiedOdometer called for unknown tripId=%s", tripId)
            throw IllegalStateException("updateVerifiedOdometer called for unknown tripId=$tripId")
        }
        tripDao.updateTrip(
            existingTrip.copy(
                verifiedOdometerKm = verifiedOdometerKm,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun markTripCompleted(tripId: String, signatureBase64: String, signingKeyId: String) {
        val existingTrip = tripDao.getTripById(tripId) ?: run {
            Timber.tag("MT-Repository").e("markTripCompleted called for unknown tripId=%s", tripId)
            throw IllegalStateException("markTripCompleted called for unknown tripId=$tripId")
        }
        tripDao.updateTrip(
            existingTrip.copy(
                status = TripStatus.COMPLETED,
                signatureBase64 = signatureBase64,
                signingKeyId = signingKeyId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateStartLocationIfUnset(tripId: String, latitude: Double, longitude: Double) {
        tripDao.updateStartLocationIfUnset(tripId, latitude, longitude, updatedAt = System.currentTimeMillis())
    }

    override suspend fun updateEndLocation(tripId: String, latitude: Double, longitude: Double) {
        tripDao.updateEndLocation(tripId, latitude, longitude, updatedAt = System.currentTimeMillis())
    }

    override suspend fun updateDistanceKm(tripId: String, distanceKm: Double) {
        tripDao.updateDistanceKm(tripId, distanceKm, updatedAt = System.currentTimeMillis())
    }

    override suspend fun updateStatus(tripId: String, status: TripStatus) {
        tripDao.updateTripStatus(tripId, status, updatedAt = System.currentTimeMillis())
    }

    override suspend fun updateEndTimestamp(tripId: String, endTimestampEpochMillis: Long) {
        tripDao.updateEndTimestamp(tripId, endTimestampEpochMillis, updatedAt = System.currentTimeMillis())
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
    )
}
