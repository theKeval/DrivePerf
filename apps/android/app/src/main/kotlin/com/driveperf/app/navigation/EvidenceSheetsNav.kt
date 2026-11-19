package com.driveperf.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.driveperf.di.CoreServices
import com.driveperf.di.evidencePresenter
import com.driveperf.di.tipDetailPresenter
import com.driveperf.feature.evidence.EvidenceSheetsViewModel
import com.driveperf.feature.evidence.TripSheetFactories
import com.driveperf.presentation.replay.ReplayTimeFormat
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The S-RPL-02 and S-SCO-04 sheets of [tripId] for the destination this is called in (trip detail, the breakdown,
 * Analysis), from B's shared factories (DRIVE-87 AC2, DRIVE-55, DRIVE-102, DRIVE-39 AC1). The view model lives as long
 * as the destination's back-stack entry.
 */
@Composable
internal fun rememberEvidenceSheets(tripId: String): EvidenceSheetsViewModel {
    val core = koinInject<CoreServices>()
    return viewModel(key = "evidence-sheets-$tripId") {
        // The region picks the default units until the user chooses some, as Replay's sheets do.
        val region = Locale.getDefault().country
        val format = localTimeOfDay()
        EvidenceSheetsViewModel(
            TripSheetFactories(
                evidence = { eventId, scope -> core.evidencePresenter(tripId, eventId, region, format, scope) },
                tip = { tipId, eventId, scope -> core.tipDetailPresenter(tripId, tipId, eventId, region, scope) },
            ),
        )
    }
}

/** "5:56 PM" in the phone's time zone and locale: the only formatting the shared evidence copy leaves to the app. */
private fun localTimeOfDay(): ReplayTimeFormat {
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    return ReplayTimeFormat { ms -> formatter.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())) }
}
