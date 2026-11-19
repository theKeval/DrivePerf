package com.driveperf.app.di

import com.driveperf.di.CoreServices
import com.driveperf.di.drivesPresenter
import com.driveperf.feature.drives.DrivesViewModel
import com.driveperf.feature.drives.JavaTimeDrivesFormatter
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.Locale

/** DRIVE-217: the shared presenter owns the Drives lists, sheets, answers and corrections (ADR-002 section 4). */
val drivesModule = module {
    viewModel {
        val core = get<CoreServices>()
        DrivesViewModel { scope -> core.drivesPresenter(JavaTimeDrivesFormatter(), Locale.getDefault().country, scope) }
    }
}
