package com.driveperf.di

import com.driveperf.data.local.LocalScoreReader
import com.driveperf.data.local.LocalTripVehicles
import com.driveperf.presentation.drives.DrivesFormatter
import com.driveperf.presentation.drives.DrivesPresenter
import com.driveperf.presentation.drives.TripEditPorts
import kotlinx.coroutines.CoroutineScope

/**
 * The shared Drives presenter (S-HOME-01, S-HIS-01, S-TRP-01, S-SCO-01, S-SCO-02, S-DQ-01), running in a child of
 * [scope]. Android passes `viewModelScope`. [format] is the app's date and time formatting; [region] picks the default
 * units when the user has not chosen (storefront country or locale region).
 */
public fun CoreServices.drivesPresenter(format: DrivesFormatter, region: String, scope: CoroutineScope): DrivesPresenter =
    DrivesPresenter(trips.observeTrips(), corrections, units(region), format, clock, scope, LocalScoreReader(db, io), analytics, tripEdits())

/** [drivesPresenter] on the main thread with its own scope, for Swift. Call `close()` on deinit. */
public fun CoreServices.drivesPresenter(format: DrivesFormatter, region: String): DrivesPresenter =
    DrivesPresenter.onMain(trips.observeTrips(), corrections, units(region), format, clock, LocalScoreReader(db, io), analytics, tripEdits())

/**
 * S-TRP-02 and S-TRP-03's ports (DRIVE-131): the trip's car, and [CoreServices.trips], whose `delete` deletes the files,
 * then the rows, then recomputes the later trips (`RecomputingTripRepository`).
 */
internal fun CoreServices.tripEdits(): TripEditPorts = TripEditPorts(LocalTripVehicles(db, io, clock), trips)
