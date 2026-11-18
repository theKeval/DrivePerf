package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.settings.Appearance
import com.driveperf.domain.settings.SettingChanged
import com.driveperf.domain.settings.SettingKey
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.SettingsRepository
import com.driveperf.domain.settings.SettingsVehicle
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.foundation.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/** Uses the existing local profile; trips and vehicle history are never rewritten by a display preference. */
public class LocalSettingsRepository(
    private val db: DrivePerfDatabase,
    private val io: CoroutineContext,
    private val clock: Clock,
    private val analytics: Analytics,
) : SettingsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(read())
    override val state: StateFlow<Settings> = mutableState.asStateFlow()

    private fun read(): Settings {
        val row = db.profileQueries.localSettings().executeAsOneOrNull() ?: return Settings()
        val capture = json.decodeFromString<CaptureChoices>(row.capture_json)
        val display = json.decodeFromString<DisplayChoices>(row.units_json)
        return Settings(
            units = UnitSystem.entries.firstOrNull { it.name == display.units },
            appearance = Appearance.entries.firstOrNull { it.name.equals(row.appearance, ignoreCase = true) }
                ?: Appearance.SYSTEM,
            batterySaverRecording = capture.batterySaverRecording,
            defaultVehicleId = capture.defaultVehicleId,
            dynamicColour = display.dynamicColour,
        )
    }

    override suspend fun update(settings: Settings): Boolean = withContext(io) {
        mutex.withLock {
            val old = state.value
            if (old == settings) return@withLock true
            if (settings.defaultVehicleId != null && vehicles().none { it.id == settings.defaultVehicleId }) {
                return@withLock false
            }
            val written = write(settings)
            if (written) {
                mutableState.value = settings
                changes(old, settings).forEach { analytics.track(SettingChanged(it)) }
            }
            written
        }
    }

    override suspend fun vehicles(): List<SettingsVehicle> = withContext(io) {
        db.vehicleQueries.settingsVehicles().executeAsList().map {
            SettingsVehicle(it.id, it.nickname ?: listOfNotNull(it.make, it.model).joinToString(" ").ifBlank { "Car" })
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // SQL driver errors stay at the data boundary.
    private fun write(settings: Settings): Boolean = try {
        db.transaction {
            val now = clock.wallMs()
            db.profileQueries.ensureLocalProfile(now, now, now)
            val stored = db.profileQueries.localSettings().executeAsOne()
            val display = json.parseToJsonElement(
                json.encodeToString(DisplayChoices(settings.units?.name, settings.dynamicColour)),
            )
            val capture = json.parseToJsonElement(
                json.encodeToString(CaptureChoices(settings.batterySaverRecording, settings.defaultVehicleId)),
            )
            db.profileQueries.saveLocalSettings(
                JsonObject(json.parseToJsonElement(stored.units_json).jsonObject + display.jsonObject).toString(),
                JsonObject(json.parseToJsonElement(stored.capture_json).jsonObject + capture.jsonObject).toString(),
                settings.appearance.name.lowercase(), now,
            )
            if (settings.defaultVehicleId != state.value.defaultVehicleId) {
                db.vehicleQueries.setDefaultVehicle(settings.defaultVehicleId.orEmpty())
            }
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        false
    }

    private fun changes(old: Settings, next: Settings): List<SettingKey> = buildList {
        if (old.units != next.units) add(SettingKey.UNITS)
        if (old.appearance != next.appearance) add(SettingKey.APPEARANCE)
        if (old.batterySaverRecording != next.batterySaverRecording) add(SettingKey.BATTERY_SAVER)
        if (old.defaultVehicleId != next.defaultVehicleId) add(SettingKey.DEFAULT_CAR)
        if (old.dynamicColour != next.dynamicColour) add(SettingKey.DYNAMIC_COLOUR)
    }

    @Serializable private data class CaptureChoices(
        val batterySaverRecording: Boolean = false,
        val defaultVehicleId: String? = null,
    )
    @Serializable private data class DisplayChoices(val units: String? = null, val dynamicColour: Boolean = false)
}
