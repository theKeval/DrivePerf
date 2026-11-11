package com.driveperf.platform.location

import com.driveperf.domain.capture.FixProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** DRIVE-167 (F-DQ-03): the provider and age stored with every fix. */
class LocationMapperTest {
    @Test
    fun providerNamesMapToTheirSource() {
        assertEquals(FixProvider.GNSS, fixProvider("gps", mock = false))
        assertEquals(FixProvider.NETWORK, fixProvider("network", mock = false))
        assertEquals(FixProvider.FUSED, fixProvider("fused", mock = false))
        assertNull(fixProvider(null, mock = false))
        assertNull(fixProvider("passive", mock = false))
    }

    @Test
    fun aMockFixIsSimulatedWhateverItsProvider() {
        assertEquals(FixProvider.SIMULATED, fixProvider("gps", mock = true))
        assertEquals(FixProvider.SIMULATED, fixProvider(null, mock = true))
    }

    @Test
    fun ageIsDeliveryTimeMinusFixTime() {
        assertEquals(1_250L, fixAgeMs(nowElapsedNs = 10_000_000_000L, fixElapsedNs = 8_750_000_000L))
        assertEquals(0L, fixAgeMs(5_000L, 5_000L))
        assertNull(fixAgeMs(nowElapsedNs = 1_000L, fixElapsedNs = 2_000L))
    }
}
