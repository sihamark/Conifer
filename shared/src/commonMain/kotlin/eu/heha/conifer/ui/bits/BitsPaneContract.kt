package eu.heha.conifer.ui.bits

import eu.heha.conifer.PermissionRationale
import eu.heha.conifer.log.CrashBreadcrumb
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
 * How many days back the day lists reach — the day strip and the [DaySidebar] both offer this many,
 * today included, and the day hotkeys stay inside the same window: a day neither list can show is
 * one the user would be filtered to with nothing on screen saying so.
 */
// TODO(no fixed day window): both day lists should instead grow as they are scrolled, loading older
//  days on demand and reaching as far back as there are bits. This number is the only thing making
//  that a limit rather than a starting point, and three things lean on it being one: the two lists
//  count items from it, and the day hotkeys clamp to it (dateShiftedBy, nearestDateWithBits) so a
//  selected day is always one a list can mark. Once the lists reach everywhere, the clamp on the
//  plain step should become "as far back as there are bits" and the clamp on the skip should go
//  altogether — the day it lands on is by definition a day with bits, so a list will have it.
internal const val DAY_LIST_DAYS = 30

data class BitsPaneState(
    val permissionRationale: PermissionRationale? = null,
    val isCopyPossible: Boolean = true,
    /**
     * The crash the previous run left behind, which the screen offers to report ([CrashReportPrompt]);
     * null when the last run ended the way runs are supposed to. Read once at startup - see
     * [eu.heha.conifer.log.CrashReports].
     */
    val lastCrash: CrashBreadcrumb? = null,
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
    val today: LocalDate = now().date,
    val currentTime: LocalTime = now().time,
    val bitsByDate: List<DatedBits> = emptyList(),
    val editingBitId: String? = null,
    /** One-shot request to scroll the list to this bit after it was added or edited. */
    val scrollToBitId: String? = null
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

    /** The oldest day either day list offers, and so the oldest the day hotkeys reach. */
    val oldestListedDate: LocalDate
        get() = LocalDate.fromEpochDays(today.toEpochDays() - (DAY_LIST_DAYS - 1))

    /**
     * Whether the screen is pointed at anything other than every day and now — a day being looked
     * at, or a date or time the composer will use in place of the clock. This is what Esc backs out
     * of, and a time nudged on its own counts: it is as much a thing to be stuck with as a day is.
     */
    val hasSelection: Boolean
        get() = filterDate != null || composerDate != null || composerTime != null
}

/**
 * The day [days] steps away from the one being written to, clamped to the days the day lists offer:
 * the arithmetic behind Alt+←/→, kept out of the view model for the same reason
 * [shiftedByTimeSlots] is — it is the part worth testing on its own.
 */
internal fun BitsPaneState.dateShiftedBy(days: Int): LocalDate =
    LocalDate.fromEpochDays(effectiveDate.toEpochDays() + days)
        .coerceIn(oldestListedDate, today)

/**
 * The nearest day in that direction that has bits, or null when there is none within reach — the
 * arithmetic behind Shift+Alt+←/→. Clamped to the same days as [dateShiftedBy], so a day with bits
 * older than the lists go is left to the unfiltered list to show.
 */
internal fun BitsPaneState.nearestDateWithBits(days: Int): LocalDate? {
    val dates = bitsByDate.map { it.date }
    return if (days < 0) {
        dates.filter { it < effectiveDate && it >= oldestListedDate }.maxOrNull()
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
     * Steps the day being written to by days, clamped to what the day lists show. Unlike
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
     * once. What Esc does, and the one thing that clears the time as well — [onClickAllDays] is the
     * day lists' "All days" and leaves a chosen time alone, since picking days is not picking times.
     */
    val onResetSelection: () -> Unit = {},
    val onSelectTime: (LocalTime) -> Unit = {},
    val onResetToNow: () -> Unit = {},
    val onClickCopyBitsOfDateToClipboard: (LocalDate) -> Unit = {},
    /** Copies the previous run's crash to the clipboard, and leaves the banner up. */
    val onClickCopyCrashReport: () -> Unit = {},
    /** Takes the crash banner down and forgets the crash it reported. */
    val onDismissCrashReport: () -> Unit = {},
    val onClickEditBit: (Bit) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onDeleteBit: (Bit) -> Unit = {},
    val onScrolledToBit: () -> Unit = {}
)
