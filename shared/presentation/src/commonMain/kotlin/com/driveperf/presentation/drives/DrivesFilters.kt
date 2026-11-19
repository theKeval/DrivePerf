package com.driveperf.presentation.drives

import com.driveperf.domain.score.ScoreLines
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripVehicle
import com.driveperf.presentation.score.ComparisonCopy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

// S-HIS-02 Filter sheet and the filter chips on S-HIS-01 All drives (DRIVE-126 AC3, F-HIS-01 AC3). Filtering runs in
// the presenter, once per state change, never per row in the UI, so the 1,000-trip list stays cheap (AC2). Copy is from
// docs/design/DRIVE-126 and prototypes/app/screens/wp-d.js.

/** A group of S-HIS-02. Several choices in one group widen the list; choices in different groups narrow it. */
public enum class FilterGroup(internal val key: String, public val title: String) {
    TYPE("type", "Trip type"),
    CAR("car", "Car"),
    ROUTE("route", "Route"),
    QUALITY("quality", "Data quality"),
}

/**
 * One filter choice: [value] is a [TripEditType] key for [FilterGroup.TYPE], a car or route ID (or [NONE]) for
 * [FilterGroup.CAR] and [FilterGroup.ROUTE], and a lower-case [QualityBadge] name for [FilterGroup.QUALITY].
 */
public data class FilterKey(val group: FilterGroup, val value: String) {
    public companion object {
        /** "No car" and "Not on a route". */
        public const val NONE: String = "none"

        public fun type(type: TripEditType): FilterKey = FilterKey(FilterGroup.TYPE, type.key)

        public fun quality(badge: QualityBadge): FilterKey = FilterKey(FilterGroup.QUALITY, badge.name.lowercase())
    }
}

/** A filter chip: in S-HIS-02 with its drive count, or on S-HIS-01 as an applied filter or a quick filter. */
public data class FilterChipUi(
    val key: FilterKey,
    /** "The Commute", "GTI", "Good data" (applied) or "Good" (in the sheet). */
    val label: String,
    val count: Int,
    val selected: Boolean,
    /** The quality glyph to draw before the label; null for the other groups. */
    val quality: QualityBadge? = null,
    /** "The Commute, 10 drives, selected", or "Remove filter The Commute" for an applied chip. */
    val accessibilityLabel: String,
)

/** One group of S-HIS-02: "Car" and its chips. */
public data class FilterGroupUi(val group: FilterGroup, val title: String, val chips: List<FilterChipUi>)

/** S-HIS-02 Filter sheet, over the choices not applied yet. */
public data class FilterSheetUi(
    /** "Filter drives" */
    val title: String,
    /** "All 30 drives" or "2 filters · 8 drives" */
    val subtitle: String,
    val groups: List<FilterGroupUi>,
    /** "Show 8 drives" or "No drives match" */
    val showLabel: String,
    val canShow: Boolean,
    /** "Reset" */
    val resetLabel: String,
    val canReset: Boolean,
)

/** The filters on S-HIS-01: the Filters button, the chip row, the subtitle and the no-match state. */
public data class HistoryFilterUi(
    /** How many filters are applied. */
    val activeCount: Int = 0,
    /** "Filters" */
    val buttonLabel: String = "Filters",
    /** "Filters, 2 applied" */
    val buttonAccessibilityLabel: String = "Filters",
    /** Applied filters as selected chips with ×. */
    val applied: List<FilterChipUi> = emptyList(),
    /** Quick filters: the cars, the most driven routes and Passenger. */
    val suggestions: List<FilterChipUi> = emptyList(),
    /** "Clear" */
    val clearLabel: String = "Clear",
    /** "8 drives · filtered" while filters are applied. */
    val subtitle: String? = null,
    /** "No drives match." and what to try, when the filters leave nothing. */
    val emptyTitle: String? = null,
    val emptyBody: String? = null,
    /** S-HIS-02, while it is open. */
    val sheet: FilterSheetUi? = null,
) {
    val active: Boolean get() = activeCount > 0
}

/** What the user does with the filters (sent as [DrivesIntent.History]). */
public sealed interface HistoryFilterAction {
    /** The Filters button or chip opens S-HIS-02 on the applied filters. */
    public data object OpenSheet : HistoryFilterAction

    /** A chip in S-HIS-02. */
    public data class ToggleDraft(val key: FilterKey) : HistoryFilterAction

    /** "Reset" in S-HIS-02. */
    public data object ResetDraft : HistoryFilterAction

    /** "Show n drives": applies S-HIS-02's choices and closes it. */
    public data object Apply : HistoryFilterAction

    /** Close, drag down or scrim: closes S-HIS-02 without applying. */
    public data object CloseSheet : HistoryFilterAction

    /** A quick chip on S-HIS-01 adds its filter; an applied chip's × removes it. */
    public data class Toggle(val key: FilterKey) : HistoryFilterAction

    /** "Clear" on S-HIS-01. */
    public data object Clear : HistoryFilterAction
}

/** Everything the filters are worked out from: the listed trips newest first, their score lines and the user's cars. */
internal class HistoryData(
    val trips: List<Trip>,
    val notDrives: List<Trip>,
    val lines: ScoreLines,
    val vehicles: List<TripVehicle>,
)

/** All drives after the filters: the trips and their cards, newest first, and what the filter controls draw. */
internal class HistoryResult(val trips: List<Trip>, val rows: List<TripRowUi>, val ui: HistoryFilterUi)

/**
 * The filters of S-HIS-01 and S-HIS-02. [data] reads the presenter's current trips; [onChange] asks it to render
 * again; [track] records `history_filter_opened`, `history_filtered {filter, count_bucket}` and
 * `history_filter_reset`, with fixed values only (never a car or route name).
 */
internal class HistoryFiltering(
    private val data: () -> HistoryData,
    private val onChange: () -> Unit,
    private val track: (String, Map<String, String>) -> Unit,
) {
    /** [draft] is S-HIS-02's choices while it is open. */
    private data class State(val applied: Set<FilterKey> = emptySet(), val draft: Set<FilterKey>? = null)

    private val state = MutableStateFlow(State())

    fun dispatch(action: HistoryFilterAction) {
        when (action) {
            HistoryFilterAction.OpenSheet -> {
                track("history_filter_opened", emptyMap())
                change { it.copy(draft = it.applied) }
            }
            is HistoryFilterAction.ToggleDraft -> change { s -> s.copy(draft = s.draft?.let { toggle(it, action.key) }) }
            HistoryFilterAction.ResetDraft -> if (state.value.draft != null) {
                track("history_filter_reset", emptyMap())
                change { it.copy(draft = emptySet()) }
            }
            HistoryFilterAction.Apply -> state.value.draft?.let { draft ->
                apply(draft, added = draft.map { it.group }.toSet() - state.value.applied.map { it.group }.toSet())
            }
            HistoryFilterAction.CloseSheet -> change { it.copy(draft = null) }
            is HistoryFilterAction.Toggle -> {
                val applied = toggle(state.value.applied, action.key)
                apply(applied, added = if (action.key in applied) setOf(action.key.group) else emptySet())
            }
            HistoryFilterAction.Clear -> change { it.copy(applied = emptySet()) }
        }
    }

    /** Applies [keys] and records `history_filtered` for each group in [added]. */
    private fun apply(keys: Set<FilterKey>, added: Set<FilterGroup>) {
        change { it.copy(applied = keys, draft = null) }
        if (added.isEmpty()) return
        val bucket = countBucket(Facets(data()).matching(keys).size)
        FilterGroup.entries.filter { it in added }.forEach {
            track("history_filtered", mapOf("filter" to it.key, "count_bucket" to bucket))
        }
    }

    private fun toggle(keys: Set<FilterKey>, key: FilterKey) = if (key in keys) keys - key else keys + key

    private fun change(change: (State) -> State) {
        state.update(change)
        onChange()
    }

    /** All drives after the applied filters. [tripRows] are the cards of [HistoryData.trips], in the same order. */
    fun build(data: HistoryData, rows: TripRows, tripRows: List<TripRowUi>): HistoryResult {
        val s = state.value
        if (s.applied.isEmpty() && s.draft == null) return HistoryResult(data.trips, tripRows, HistoryFilterUi())
        val facets = Facets(data)
        val trips = facets.matching(s.applied)
        val rowById = data.trips.indices.associate { data.trips[it].id to tripRows[it] }
        val shown = trips.map { rowById[it.id] ?: rows.row(it) }
        val options = facets.options()
        val applied = s.applied.mapNotNull { key -> options.firstOrNull { it.key == key } }
        val ui = HistoryFilterUi(
            activeCount = s.applied.size,
            buttonAccessibilityLabel = if (s.applied.isEmpty()) "Filters" else "Filters, ${s.applied.size} applied",
            applied = applied.map { it.applied() },
            suggestions = suggestions(options, facets, s.applied),
            subtitle = if (s.applied.isEmpty()) null else "${drivesCount(trips.size)} · filtered",
            emptyTitle = if (s.applied.isNotEmpty() && trips.isEmpty()) "No drives match." else null,
            emptyBody = if (s.applied.isNotEmpty() && trips.isEmpty()) "Try fewer filters, or a different place or car." else null,
            sheet = s.draft?.let { sheet(it, options, facets) },
        )
        return HistoryResult(trips, shown, ui)
    }

    private fun sheet(draft: Set<FilterKey>, options: List<Option>, facets: Facets): FilterSheetUi {
        val n = facets.matching(draft).size
        return FilterSheetUi(
            title = "Filter drives",
            subtitle = if (draft.isEmpty()) "All ${drivesCount(n)}" else "${count(draft.size, "filter")} · ${drivesCount(n)}",
            groups = FilterGroup.entries.mapNotNull { group ->
                val chips = options.filter { it.key.group == group }
                if (chips.isEmpty()) null else FilterGroupUi(group, group.title, chips.map { it.inSheet(it.key in draft) })
            },
            showLabel = if (n > 0) "Show ${drivesCount(n)}" else "No drives match",
            canShow = n > 0,
            resetLabel = "Reset",
            canReset = draft.isNotEmpty(),
        )
    }

    /** Quick filters: each car (with two or more), the two most driven named routes and Passenger, unless applied. */
    private fun suggestions(options: List<Option>, facets: Facets, applied: Set<FilterKey>): List<FilterChipUi> {
        val cars = options.filter { it.key.group == FilterGroup.CAR && it.key.value != FilterKey.NONE }
        val routes = options.filter { it.key.group == FilterGroup.ROUTE && it.named && it.count > 0 }.take(QUICK_ROUTES)
        val passenger = options.filter { it.key == FilterKey.type(TripEditType.PASSENGER) && it.count > 0 }
        return (cars + routes + passenger).filter { it.key !in applied && facets.hasAny(it.key) }.map { it.quick() }
    }

    /** One chip's label and drive count; [named] marks a route the user named. */
    private class Option(val key: FilterKey, val label: String, val count: Int, val named: Boolean = false) {
        private val quality = if (key.group == FilterGroup.QUALITY) QualityBadge.valueOf(key.value.uppercase()) else null

        fun inSheet(selected: Boolean) = FilterChipUi(
            key, label, count, selected, quality,
            "$label, ${drivesCount(count)}, ${if (selected) "selected" else "not selected"}",
        )

        fun applied(): FilterChipUi {
            val text = if (quality != null) "$label data" else label
            return FilterChipUi(key, text, count, selected = true, quality, "Remove filter $text")
        }

        fun quick() = FilterChipUi(key, label, count, selected = false, quality, "$label, ${drivesCount(count)}")
    }

    /** Each trip's value per group, the chips with their counts, and the matching. */
    private class Facets(private val data: HistoryData) {
        /** Every listed trip, drives and not drives, newest first. */
        private val all = (data.trips + data.notDrives).sortedByDescending { it.startedAt }

        fun value(trip: Trip, group: FilterGroup): String? = when (group) {
            FilterGroup.TYPE -> TripEditType.of(trip).key
            FilterGroup.CAR -> trip.vehicleId ?: FilterKey.NONE
            FilterGroup.ROUTE -> data.lines.lines[trip.id]?.route?.routeId ?: FilterKey.NONE
            FilterGroup.QUALITY -> trip.quality?.badge?.name?.lowercase()
        }

        /**
         * The trips [keys] keep, newest first. Without a type filter the list holds drives only (as unfiltered); a
         * type filter for a bus or train or for not a drive brings in the trips under "Not drives".
         */
        fun matching(keys: Set<FilterKey>): List<Trip> {
            val byGroup = keys.groupBy({ it.group }, { it.value })
            val types = byGroup[FilterGroup.TYPE].orEmpty()
            val pool = if (types.any { it == TripEditType.TRANSIT.key || it == TripEditType.OTHER.key }) all else data.trips
            if (byGroup.isEmpty()) return pool
            return pool.filter { trip -> byGroup.all { (group, values) -> value(trip, group) in values } }
        }

        fun hasAny(key: FilterKey): Boolean = all.any { value(it, key.group) == key.value }

        private fun counts(group: FilterGroup): Map<String?, Int> = all.groupingBy { value(it, group) }.eachCount()

        fun options(): List<Option> = typeOptions() + carOptions() + routeOptions() + qualityOptions()

        private fun typeOptions(): List<Option> {
            val counts = counts(FilterGroup.TYPE)
            return TripEditType.entries.map { Option(FilterKey.type(it), typeLabel(it), counts[it.key] ?: 0) }
        }

        /** The user's cars, then cars no longer active that trips still name, then "No car"; only with two or more. */
        private fun carOptions(): List<Option> {
            val counts = counts(FilterGroup.CAR)
            val names = data.vehicles.associate { it.id to it.name }
            val retired = all.mapNotNull { it.vehicleId }.distinct().filter { it !in names }
            val cars = data.vehicles.map { Option(FilterKey(FilterGroup.CAR, it.id), it.name, counts[it.id] ?: 0) } +
                retired.map { id ->
                    val name = all.firstOrNull { it.vehicleId == id }?.let { data.lines.lines[it.id]?.car } ?: "Car"
                    Option(FilterKey(FilterGroup.CAR, id), name, counts[id] ?: 0)
                }
            val none = counts[FilterKey.NONE]?.takeIf { cars.isNotEmpty() }
                ?.let { listOf(Option(FilterKey(FilterGroup.CAR, FilterKey.NONE), "No car", it)) }.orEmpty()
            return (cars + none).takeIf { it.size >= 2 }.orEmpty()
        }

        /** Routes by drives, most first; unnamed ones as "Route 1", "Route 2"…; then "Not on a route". */
        private fun routeOptions(): List<Option> {
            val counts = counts(FilterGroup.ROUTE)
            val routes = data.lines.lines.values.mapNotNull { it.route }.distinctBy { it.routeId }
                .sortedWith(compareByDescending<com.driveperf.domain.score.RouteName> { counts[it.routeId] ?: 0 }.thenBy { it.routeId })
            var unnamed = 0
            val options = routes.map { route ->
                val name = ComparisonCopy.routeName(route)
                Option(FilterKey(FilterGroup.ROUTE, route.routeId), name ?: "Route ${++unnamed}", counts[route.routeId] ?: 0, name != null)
            }
            val none = counts[FilterKey.NONE]?.let { Option(FilterKey(FilterGroup.ROUTE, FilterKey.NONE), "Not on a route", it) }
            return if (options.isEmpty()) emptyList() else options + listOfNotNull(none)
        }

        private fun qualityOptions(): List<Option> {
            val counts = counts(FilterGroup.QUALITY)
            return QualityBadge.entries.map { badge ->
                val key = FilterKey.quality(badge)
                Option(key, TripCopy.badgeWord(badge), counts[key.value] ?: 0)
            }
        }
    }

    private companion object {
        const val QUICK_ROUTES = 2

        fun typeLabel(type: TripEditType): String = if (type == TripEditType.OTHER) "Not a drive" else TripEditCopy.title(type)

        fun count(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
    }
}

private const val ONE = 1
private const val THREE = 3
private const val SIX = 6
private const val TEN = 10

/** `count_bucket` (analytics-crash-privacy.md §2): `0`, `1`, `2_3`, `4_6`, `7_10`, `11_plus`. */
internal fun countBucket(n: Int): String = when {
    n <= 0 -> "0"
    n == ONE -> "1"
    n <= THREE -> "2_3"
    n <= SIX -> "4_6"
    n <= TEN -> "7_10"
    else -> "11_plus"
}
