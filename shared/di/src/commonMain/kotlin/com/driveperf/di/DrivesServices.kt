package com.driveperf.di

import com.driveperf.data.local.LocalScoreReader
import com.driveperf.domain.replay.TripReplayDetails
import com.driveperf.presentation.drives.DrivesFormatter
import com.driveperf.presentation.drives.DrivesPresenter
import com.driveperf.presentation.drives.TripMaps
import kotlinx.coroutines.CoroutineScope

/**
 * The shared Drives presenter (S-HOME-01, S-HIS-01, S-TRP-01, S-SCO-01, S-SCO-02, S-DQ-01), running in a child of
 * [scope]. Android passes `viewModelScope`. [format] is the app's date and time formatting; [region] picks the default
 * units when the user has not chosen (storefront country or locale region).
 */
public fun CoreServices.drivesPresenter(format: DrivesFormatter, region: String, scope: CoroutineScope): DrivesPresenter =
    DrivesPresenter(
        trips.observeTrips(), corrections, units(region), format, clock, scope, LocalScoreReader(db, io), analytics,
        tripMaps(),
    )

/** [drivesPresenter] on the main thread with its own scope, for Swift. Call `close()` on deinit. */
public fun CoreServices.drivesPresenter(format: DrivesFormatter, region: String): DrivesPresenter =
    DrivesPresenter.onMain(
        trips.observeTrips(), corrections, units(region), format, clock, LocalScoreReader(db, io), analytics, tripMaps(),
    )

/** The trip detail's map hero (DRIVE-55): the replay track with its pins, drawn without tiles. */
private fun CoreServices.tripMaps(): TripMaps = TripMaps(replay, TripReplayDetails(trips, previews))
