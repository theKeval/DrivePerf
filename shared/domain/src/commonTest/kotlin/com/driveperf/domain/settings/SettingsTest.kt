package com.driveperf.domain.settings

import com.driveperf.domain.trip.DisplayUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {
    @Test fun regionalDefaultsAndOverride() {
        for (region in listOf("CA", "CAN", "ca", "GB", "", "FR")) {
            assertEquals(UnitSystem.METRIC, Settings().effectiveUnits(region))
        }
        for (region in listOf("US", "USA", "us", "LR", "MM")) {
            assertEquals(UnitSystem.IMPERIAL, Settings().effectiveUnits(region))
        }
        assertEquals(UnitSystem.METRIC, Settings(units = UnitSystem.METRIC).effectiveUnits("US"))
        assertEquals(UnitSystem.IMPERIAL, Settings(units = UnitSystem.IMPERIAL).effectiveUnits("CA"))
    }

    @Test fun unitsUseTheSamePrecisionAndGAlwaysStaysG() {
        val imperial = UnitSystem.IMPERIAL
        assertEquals("1.0", imperial.display(DisplayUnit.KILOMETRES).format(1609.344))
        assertEquals("60", imperial.display(DisplayUnit.KM_PER_HOUR).format(26.8224))
        assertEquals("100", imperial.display(DisplayUnit.METRES).format(30.48))
        for (system in UnitSystem.entries) {
            assertEquals(DisplayUnit.G, system.display(DisplayUnit.G))
            assertEquals("0.42", system.display(DisplayUnit.G).format(0.424))
            assertEquals(DisplayUnit.SECONDS, system.display(DisplayUnit.SECONDS))
        }
        assertEquals("−10", DisplayUnit.FEET.format(-3.048))
        assertEquals("0.0", DisplayUnit.MILES.format(0.0))
    }

    @Test fun settingsEventsContainOnlyTheKey() {
        for (key in SettingKey.entries) {
            assertEquals("setting_changed", SettingChanged(key).name)
            assertEquals(mapOf("key" to key.key), SettingChanged(key).properties)
        }
    }
}
