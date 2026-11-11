package com.driveperf.feature.onboarding

import android.os.Build

/** The brand guide for this phone, from `Build.MANUFACTURER` and `Build.BRAND`; read locally, nothing is sent (F-ONB-07). */
fun currentOemGuide(): OemGuide? = OemGuides.forDevice(Build.MANUFACTURER, Build.BRAND)
