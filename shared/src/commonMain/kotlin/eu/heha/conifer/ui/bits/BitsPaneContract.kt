package eu.heha.conifer.ui.bits

import eu.heha.conifer.PermissionRationale
import eu.heha.conifer.ReportShareController
import eu.heha.conifer.log.LastRunEnd
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.now
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * How the screen arranges its three regions — the days, the bits and the composer. Picked from the
 * window size classes in [BitsPane].
 */
enum class BitsLayout {
    /** Narrow windows: a single column with the composer under the bits, days in its picker. */
    Stacked,

    /** Roomy in both directions: the days move out of the picker into a sidebar beside the bits. */
    DaySidebar,

    /**
     * Wide but short *and* the keyboard is up — a phone in landscape being typed on. The keyboard
     * eats most of the height there, so the composer moves beside the bits instead of under them;
     * that way it no longer takes the little vertical room that is left, and the list keeps
     * showing its newest bits. Too short for the sidebar as well, so the days go back into the
     * composer's picker. Without a keyboard on screen the same window uses [DaySidebar]: it has
     * the room, and moving the composer aside would only look out of place.
     */
    SideComposer
}

/**
 * How many days a page of either day list holds — the day strip and the [DaySidebar] both start
 * with this many, today included, and both ask for another page of the same size as they are
 * scrolled towards their oldest day (see [BitsPaneState.listedDayCount]). It is a step, not a
 * limit: the lists reach as far back as they are scrolled.
 */
internal const val DAY_LIST_PAGE = 30

data class BitsPaneState(
    val permissionRationale: PermissionRationale? = null,
    val isCopyPossible: Boolean = true,
    /** Whether this platform has somewhere to send a crash report - see [ReportShareController]. */
    val isSharePossible: Boolean = false,
    /**
     * How the previous run ended, where that is worth reporting ([RunEndPrompt]); null when it ended
     * the way runs are supposed to. Worked out once at startup - see
     * [eu.heha.conifer.log.RunEndReports].
     */
    val lastRunEnd: LastRunEnd? = null,
    val newBitText: String = "",
    /**
     * The day the list is filtered to; null shows every day. Set by the day lists — the composer's
     * day strip and the [DaySidebar] — and by nothing else, so what the list shows only ever
     * changes because the user asked for another day. Editing a bit in particular leaves it alone:
     * rebuilding the list around an edit is what threw its scroll position away.
     */
    val filterDate: LocalDate? = null,
    /**
     * The date the composer will stamp on the bit being written; null uses [today]. Picking a day
     * sets this along with [filterDate], and an edit loads it from the bit it is editing.
     */
    val composerDate: LocalDate? = null,
    /** The time the composer will stamp on the bit being written; null uses [currentTime]. */
    val composerTime: LocalTime? = null,
    /**
     * How many days back the day lists currently reach, today included — a whole number of
     * [DAY_LIST_PAGE] pages, grown by [BitsPaneActions.onLoadOlderDays] as either list is scrolled
     * towards its oldest day, and by the day hotkeys when they land past it.
     *
     * The days themselves are not loaded from anywhere: every bit is in [bitsByDate] already, and
     * a day is a date and whatever of those bits falls on it. This is only how far the lists have
     * been asked to count back, which is what keeps a list that nobody scrolled from composing a
     * decade of empty days.
     */
    val listedDayCount: Int = DAY_LIST_PAGE,
    val today: LocalDate = now().date,
    val currentTime: LocalTime = now().time,
    val bitsByDate: List<DatedBits> = emptyList(),
    val editingBitId: String? = null,
    /** One-shot request to scroll the list to this bit after it was added or edited. */
    val scrollToBitId: String? = null,
    /**
     * One-shot request to take the day lists back to today — bumped by the actions that mean "out
     * of wherever I was": Esc, "All days", the key for today.
     *
     * A counter rather than a flag or a date, because the request has to survive being made twice
     * in a row and, more to the point, being made when it changes nothing else: Esc pressed with
     * nothing selected leaves every other field exactly as it was, and a day list scrolled a year
     * into the past is precisely the state in which that press has something to do. Nothing
     * acknowledges it; each new number is a new request, and a list that was already home simply
     * scrolls nowhere.
     */
    val scrollDaysHomeRequest: Int = 0
) {
    /**
     * The time a bit added right now would carry: the user's pick, or the clock while they haven't
     * made one. [DateTimeSelector] derives the same value from the two fields it is handed.
     */
    val effectiveTime: LocalTime get() = composerTime ?: currentTime

    /**
     * The day the composer would stamp on a bit added right now, and with that the day the day
     * hotkeys count from: whichever day is being written to is the one it makes sense to step off.
     */
    val effectiveDate: LocalDate get() = composerDate ?: today

    /**
     * The oldest day a plain day step can reach: as far back as there is writing, and never less
     * than the first page, so the step still walks a fresh install's month of empty days.
     *
     * The lists themselves stop nowhere — they grow for as long as they are scrolled — but a key
     * held down is not scrolling, and the days before the first bit are days there is nothing to
     * say about. A skip has no such bound at all ([nearestDateWithBits]): it only ever lands on a
     * day that has bits.
     */
    val oldestReachableDate: LocalDate
        get() = minOf(
            bitsByDate.minOfOrNull { it.date } ?: today,
            LocalDate.fromEpochDays(today.toEpochDays() - (DAY_LIST_PAGE - 1))
        )

    /**
     * Whether the screen is pointed at anything other than every day and now — a day being looked
     * at, or a date or time the composer will use in place of the clock. This is what Esc backs out
     * of, and a time nudged on its own counts: it is as much a thing to be stuck with as a day is.
     */
    val hasSelection: Boolean
        get() = filterDate != null || composerDate != null || composerTime != null
}

/**
 * The day [days] steps away from the one being written to, clamped to [oldestReachableDate] and
 * today: the arithmetic behind Alt+←/→, kept out of the view model for the same reason
 * [shiftedByTimeSlots] is — it is the part worth testing on its own.
 *
 * Landing past the oldest day the lists show is fine — the view model has them count back far
 * enough to mark it (see [eu.heha.conifer.ui.BitsViewModel.shiftDate]) — so what is clamped here
 * is how far a held key walks, not what a list can show.
 */
internal fun BitsPaneState.dateShiftedBy(days: Int): LocalDate =
    LocalDate.fromEpochDays(effectiveDate.toEpochDays() + days)
        .coerceIn(oldestReachableDate, today)

/**
 * The nearest day in that direction that has bits, or null when there is none — the arithmetic
 * behind Shift+Alt+←/→. Unbounded into the past: the day it lands on has bits by definition, and
 * the day lists grow to reach whichever day that is.
 */
internal fun BitsPaneState.nearestDateWithBits(days: Int): LocalDate? {
    val dates = bitsByDate.map { it.date }
    return if (days < 0) {
        dates.filter { it < effectiveDate }.maxOrNull()
    } else {
        dates.filter { it > effectiveDate && it <= today }.minOrNull()
    }
}

class BitsPaneActions(
    val onClickAdd: () -> Unit = {},
    val onNewBitTextChange: (String) -> Unit = {},
    val onClickRequestPermission: () -> Unit = {},
    val onClickDate: (LocalDate) -> Unit = {},
    val onClickAllDays: () -> Unit = {},
    /**
     * Asks for another [DAY_LIST_PAGE] days at the old end of the day lists — what either list
     * calls as it is scrolled within sight of its oldest day, and it may call it again as soon as
     * the days it got are on screen too.
     */
    val onLoadOlderDays: () -> Unit = {},
    /**
     * Steps the day being written to by days, as far back as there is writing. Unlike
     * [onClickDate] — which is the day lists', and says "look at this day *and* write to it" — this
     * is the keyboard's, and only pulls the list along if it was already filtered to a day.
     */
    val onShiftDate: (days: Int) -> Unit = {},
    /** As [onShiftDate], but to the nearest day in that direction that has bits at all. */
    val onSkipToDateWithBits: (days: Int) -> Unit = {},
    /** Back to today, on the same terms as [onShiftDate]. */
    val onSelectToday: () -> Unit = {},
    /**
     * Back to every day and now: the day filter, the composer's date and its time all dropped at
     * once. What Esc does — [onClickAllDays] is the day lists' "All days" and leaves a chosen time
     * alone, since picking days is not picking times, and [onResetTime] is the other way round.
     */
    val onResetSelection: () -> Unit = {},
    val onSelectTime: (LocalTime) -> Unit = {},
    /**
     * Hands the time back to the clock, leaving both days as they are — the time's counterpart of
     * [onSelectToday]. [onResetToNow] is the chip's and does this and the date together.
     */
    val onResetTime: () -> Unit = {},
    val onResetToNow: () -> Unit = {},
    val onClickCopyBitsOfDateToClipboard: (LocalDate) -> Unit = {},
    /** Copies the previous run's crash report to the clipboard, and leaves the banner up. */
    val onClickCopyRunEndReport: () -> Unit = {},
    /** Hands that report to the platform's share sheet (or file manager), banner still up. */
    val onClickShareRunEndReport: () -> Unit = {},
    /** Takes the crash banner down and forgets the crash it reported. */
    val onDismissRunEndReport: () -> Unit = {},
    val onClickEditBit: (Bit) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onDeleteBit: (Bit) -> Unit = {},
    val onScrolledToBit: () -> Unit = {}
)
