package com.driveperf.domain.trip

import kotlinx.coroutines.flow.Flow

/**
 * One of the user's active cars, as the S-TRP-02 car picker and the S-HIS-02 car filter show it (F-HIS-02, F-GAR-02).
 * [nickname], [make], [model] and [year] are what the user entered in the Garage; any of them may be missing.
 */
public data class TripVehicle(
    val id: String,
    val nickname: String?,
    val make: String?,
    val model: String?,
    val year: Int?,
    val isDefault: Boolean,
) {
    /** "GTI", else "Volkswagen Golf", else "Car". */
    val name: String
        get() = nickname?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(make, model).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { DEFAULT_NAME }

    /** "2019 Volkswagen Golf"; null when nothing beyond the nickname is known. */
    val description: String?
        get() = listOfNotNull(year?.toString(), make, model).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { null }

    private companion object {
        const val DEFAULT_NAME = "Car"
    }
}

/**
 * Which car a trip was in (F-HIS-02 AC2, `trip.vehicle_id`). Implemented in `:shared:data:local`. Per-car numbers are
 * read from `vehicle_id` when they are shown, so a change needs no recompute: baselines are not per car.
 */
public interface TripVehicles {
    /** The user's active cars, oldest first; it emits again when the Garage changes. */
    public fun observeVehicles(): Flow<List<TripVehicle>>

    /**
     * Puts the finished trip [tripId] in car [vehicleId], or in no car when it is null. Returns false, and changes
     * nothing, when there is no such finished trip or [vehicleId] is not an active car.
     */
    public suspend fun setVehicle(tripId: String, vehicleId: String?): Boolean
}
