package com.driveperf.data.local

import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.route.RecordEntry
import com.driveperf.domain.score.Band
import com.driveperf.domain.score.Contributor
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.Efficiency
import com.driveperf.domain.score.EfficiencyRating
import com.driveperf.domain.score.EventLedgerRow
import com.driveperf.domain.score.Ledger
import com.driveperf.domain.score.LedgerRow
import com.driveperf.domain.score.RecordState
import com.driveperf.domain.score.Tip
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.CaptureMeta
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.Interruption
import com.driveperf.domain.trip.InterruptionReason
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.Stop
import com.driveperf.domain.trip.Stretch
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSegment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// JSON value objects stored in TEXT columns (data-model.md §2.2). They are never queried by field. Domain entities
// carry no serialization annotations (data-model.md P2), so each column has a private mirror here.

internal val columnJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun Boolean.toInt(): Long = if (this) 1L else 0L

internal inline fun <reified T> decode(text: String?): T? = text?.let { columnJson.decodeFromString<T>(it) }

internal fun decodeStops(text: String?): List<Stop> = decode<List<StopJson>>(text)?.map(StopJson::toDomain).orEmpty()

internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = name?.let { enumValueOf<E>(it) }

internal inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E = enumOrNull<E>(name) ?: default

@Serializable
internal data class QualityJson(
    val badge: QualityBadge,
    val reason: QualityReason? = null,
    val placement: Placement,
    val imuHz: Double,
    val gnssCoveragePct: Double,
    val alignmentSettledS: Double? = null,
    val estimatedStartM: Double? = null,
    val scoredS: Double,
    val excludedS: Double,
    val totalS: Double,
) {
    fun toDomain(spans: List<TripSegment>): TripQuality = TripQuality(
        badge, reason, placement, imuHz, gnssCoveragePct, alignmentSettledS, estimatedStartM, scoredS, excludedS,
        totalS, spans,
    )

    companion object {
        fun of(q: TripQuality): QualityJson = QualityJson(
            q.badge, q.reason, q.placement, q.imuHz, q.gnssCoveragePct, q.alignmentSettledS, q.estimatedStartM,
            q.scoredS, q.excludedS, q.totalS,
        )
    }
}

@Serializable
internal data class StopJson(val atS: Double, val durationS: Double, val lat: Double? = null, val lng: Double? = null) {
    fun toDomain(): Stop = Stop(atS, durationS, lat, lng)

    companion object {
        fun of(s: Stop): StopJson = StopJson(s.atS, s.durationS, s.lat, s.lng)
    }
}

@Serializable
internal data class StretchJson(val distanceM: Double, val t0S: Double, val t1S: Double) {
    fun toDomain(): Stretch = Stretch(distanceM, t0S, t1S)

    companion object {
        fun of(s: Stretch): StretchJson = StretchJson(s.distanceM, s.t0S, s.t1S)
    }
}

@Serializable
internal data class InterruptionJson(val atS: Double, val reason: InterruptionReason) {
    fun toDomain(): Interruption = Interruption(atS, reason)

    companion object {
        fun of(i: Interruption): InterruptionJson = InterruptionJson(i.atS, i.reason)
    }
}

@Serializable
internal data class CaptureMetaJson(
    val wakeKind: WakeKind,
    val startLatencyS: Double? = null,
    val imuDeliveryPct: Double? = null,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
) {
    fun toDomain(): CaptureMeta = CaptureMeta(wakeKind, startLatencyS, imuDeliveryPct, batteryStartPct, batteryEndPct)

    companion object {
        fun of(m: CaptureMeta): CaptureMetaJson =
            CaptureMetaJson(m.wakeKind, m.startLatencyS, m.imuDeliveryPct, m.batteryStartPct, m.batteryEndPct)
    }
}

/** `[minLat, minLng, maxLat, maxLng]`, as data-model.md §2.2 stores it. */
internal object BboxJson {
    fun encode(b: BoundingBox): String = columnJson.encodeToString(listOf(b.minLat, b.minLng, b.maxLat, b.maxLng))

    fun decode(text: String): BoundingBox {
        val v = columnJson.decodeFromString<List<Double>>(text)
        return BoundingBox(v[0], v[1], v[2], v[3])
    }
}

@Serializable
internal data class ContributorJson(
    val id: ContributorId,
    val subScore: Double,
    val value: Double? = null,
    val band: Band? = null,
    val reasonKey: String? = null,
    val eventIds: List<String> = emptyList(),
) {
    fun toDomain(): Contributor = Contributor(id, subScore, value, band, reasonKey, eventIds)

    companion object {
        fun of(c: Contributor): ContributorJson = ContributorJson(c.id, c.subScore, c.value, c.band, c.reasonKey, c.eventIds)
    }
}

@Serializable
internal data class LedgerRowJson(val t: Double, val brake: Double, val accel: Double, val corner: Double) {
    fun toDomain(): LedgerRow = LedgerRow(t, brake, accel, corner)

    companion object {
        fun of(r: LedgerRow): LedgerRowJson = LedgerRowJson(r.movingS, r.brake, r.accel, r.corner)
    }
}

@Serializable
internal data class EventLedgerJson(val eventId: String, val type: EventType, val row: LedgerRowJson)

@Serializable
internal data class LedgerJson(
    val base: LedgerRowJson,
    val perEvent: List<EventLedgerJson>,
    val jxMps3: Double,
    val jyMps3: Double,
    val stopStarts: Int? = null,
    val abruptAccels: Int? = null,
) {
    fun toDomain(): Ledger = Ledger(
        base.toDomain(), perEvent.map { EventLedgerRow(it.eventId, it.type, it.row.toDomain()) }, jxMps3, jyMps3,
        stopStarts, abruptAccels,
    )

    companion object {
        fun of(l: Ledger): LedgerJson = LedgerJson(
            LedgerRowJson.of(l.base),
            l.perEvent.map { EventLedgerJson(it.eventId, it.type, LedgerRowJson.of(it.row)) },
            l.noiseFloorLonMps3,
            l.noiseFloorLatMps3,
            l.stopStartCount,
            l.abruptAccelCount,
        )
    }
}

/** `score.contributors_json`: the contributors plus the §5.4 ledger and the raw score (§0.8). */
@Serializable
internal data class ContributorsColumn(
    val contributors: List<ContributorJson>,
    val ledger: LedgerJson? = null,
    val smoothnessRaw: Double? = null,
)

@Serializable
internal data class EfficiencyJson(
    val rating: EfficiencyRating? = null,
    val stopsPerKm: Double? = null,
    val abruptAccels: Double? = null,
    val speedWobbleMps: Double? = null,
    val speedConsistencyCv: Double? = null,
    val stopStartCount: Int,
) {
    fun toDomain(): Efficiency =
        Efficiency(rating, stopsPerKm, abruptAccels, speedWobbleMps, speedConsistencyCv, stopStartCount)

    companion object {
        fun of(e: Efficiency): EfficiencyJson = EfficiencyJson(
            e.rating, e.stopsPerKm, e.abruptAccelsPerMin, e.speedWobbleMps, e.speedConsistencyCv, e.stopStartCount,
        )
    }
}

@Serializable
internal data class TipJson(val tipId: String, val category: TipCategory, val eventId: String? = null, val placeKey: String? = null) {
    fun toDomain(): Tip = Tip(tipId, category, eventId, placeKey)

    companion object {
        fun of(t: Tip): TipJson = TipJson(t.tipId, t.category, t.eventId, t.placeKey)
    }
}

@Serializable
internal data class RecordStateJson(val isNew: Boolean, val previousScore: Int? = null, val previousTripId: String? = null) {
    fun toDomain(): RecordState = RecordState(isNew, previousScore, previousTripId)

    companion object {
        fun of(r: RecordState): RecordStateJson = RecordStateJson(r.isNew, r.previousScore, r.previousTripId)
    }
}

@Serializable
internal data class RecordEntryJson(val tripId: String, val smoothness: Int, val setAt: Long) {
    fun toDomain(): RecordEntry = RecordEntry(tripId, smoothness, setAt)

    companion object {
        fun of(r: RecordEntry): RecordEntryJson = RecordEntryJson(r.tripId, r.smoothness, r.setAt)
    }
}
