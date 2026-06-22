package com.mileagetracker.app.domain.model

/**
 * Trip classification per brief §5.2/§6. Persisted in Room as the lowercase string via
 * [com.mileagetracker.app.data.local.Converters] — never reorder these constants without
 * checking the converter's mapping.
 */
enum class TripClassification {
    WORK,
    PRIVATE,
}
