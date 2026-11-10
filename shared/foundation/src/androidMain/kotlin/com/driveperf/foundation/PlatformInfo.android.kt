package com.driveperf.foundation

import android.os.Build

public actual fun platformDescription(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
