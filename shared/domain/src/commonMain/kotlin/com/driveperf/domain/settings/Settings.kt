package com.driveperf.domain.settings

import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.trip.DisplayUnit
import kotlinx.coroutines.flow.StateFlow

public enum class UnitSystem {
    METRIC, IMPERIAL;

    public fun display(unit: DisplayUnit): DisplayUnit = if (this == METRIC) unit else when (unit) {
        DisplayUnit.KILOMETRES -> DisplayUnit.MILES
        DisplayUnit.KM_PER_HOUR -> DisplayUnit.MILES_PER_HOUR
        DisplayUnit.METRES -> DisplayUnit.FEET
        else -> unit
    }

    public companion object {
        /** Storefront country when available, otherwise the locale region (ISO 3166 alpha-2 or alpha-3). */
        public fun defaultFor(region: String): UnitSystem = when (region.uppercase()) {
            "US", "USA", "LR", "LBR", "MM", "MMR" -> IMPERIAL
            else -> METRIC
        }
    }
}

public enum class Appearance { SYSTEM, LIGHT, DARK }

public data class Settings(
    val units: UnitSystem? = null,
    val appearance: Appearance = Appearance.SYSTEM,
    val batterySaverRecording: Boolean = false,
    val defaultVehicleId: String? = null,
    val dynamicColour: Boolean = false,
) {
    public fun effectiveUnits(region: String): UnitSystem = units ?: UnitSystem.defaultFor(region)
}

public data class SettingsVehicle(val id: String, val name: String)

public enum class SettingKey(public val key: String) {
    UNITS("units"), APPEARANCE("appearance"), BATTERY_SAVER("battery_saver_recording"),
    DEFAULT_CAR("default_car"), DYNAMIC_COLOUR("dynamic_colour"), AUTO_DETECT("auto_detect"),
}

public data class SettingChanged(val key: SettingKey) : AnalyticsEvent {
    override val name: String = "setting_changed"
    override val properties: Map<String, String> = mapOf("key" to key.key)
}

/** Persisted local choices. A failed write leaves [state] unchanged so the UI can report it and retry. */
public interface SettingsRepository {
    public val state: StateFlow<Settings>
    public suspend fun update(settings: Settings): Boolean
    public suspend fun vehicles(): List<SettingsVehicle>
}
