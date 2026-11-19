package com.driveperf.data.local

import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.domain.analysis.AnalysisAxis
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.analysis.AnalysisChoices
import com.driveperf.domain.analysis.AnalysisPreferences
import com.driveperf.foundation.Clock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * [AnalysisPreferences] in `profile.analysis_channels` (data-model.md §2.2), as `{"channels":[…],"axis":"time"}`. A
 * plain array (the column's `'[]'` default) is read as channels with the time axis; unknown channel keys are skipped,
 * and an empty, unknown or damaged value reads as the defaults.
 */
public class LocalAnalysisPreferences(
    private val db: DrivePerfDatabase,
    private val io: CoroutineContext,
    private val clock: Clock,
) : AnalysisPreferences {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(): AnalysisChoices = withContext(io) {
        db.analysisChannelsQueries.analysisChannels().executeAsOneOrNull()?.let(::decode) ?: AnalysisChoices()
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // SQL driver errors stay at the data boundary.
    override suspend fun save(choices: AnalysisChoices): Boolean = withContext(io) {
        val stored = Stored(AnalysisChannel.entries.filter { it in choices.channels }.map { it.key }, choices.axis.key)
        try {
            db.transaction {
                val now = clock.wallMs()
                db.profileQueries.ensureLocalProfile(now, now, now)
                db.analysisChannelsQueries.saveAnalysisChannels(json.encodeToString(stored), now)
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            false
        }
    }

    @Suppress("SwallowedException") // a damaged value falls back to the defaults
    private fun decode(raw: String): AnalysisChoices = try {
        val legacy = raw.trimStart().startsWith("[")
        val stored = if (legacy) Stored(json.decodeFromString<List<String>>(raw)) else json.decodeFromString<Stored>(raw)
        AnalysisChoices(
            channels = stored.channels.mapNotNull(AnalysisChannel::fromKey).toSet().ifEmpty { AnalysisChannel.DEFAULTS },
            axis = AnalysisAxis.fromKey(stored.axis) ?: AnalysisAxis.TIME,
        )
    } catch (bad: SerializationException) {
        AnalysisChoices()
    } catch (bad: IllegalArgumentException) {
        AnalysisChoices()
    }

    @Serializable private data class Stored(val channels: List<String> = emptyList(), val axis: String? = null)
}
