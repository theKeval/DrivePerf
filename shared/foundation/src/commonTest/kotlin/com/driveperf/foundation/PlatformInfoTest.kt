package com.driveperf.foundation

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformInfoTest {
    @Test
    fun describesThePlatform() {
        assertTrue(platformDescription().isNotBlank())
    }
}
