package com.driveperf.foundation

import platform.UIKit.UIDevice

public actual fun platformDescription(): String = UIDevice.currentDevice.let { "${it.systemName} ${it.systemVersion}" }
