package eu.heha.conifer.ui.bits

import eu.heha.conifer.PermissionRationale
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

data class BitsPaneState(
    val permissionRationale: PermissionRationale? = null,
    val isCopyPossible: Boolean = true,
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
}

class BitsPaneActions(
    val onClickAdd: () -> Unit = {},
    val onNewBitTextChange: (String) -> Unit = {},
    val onClickRequestPermission: () -> Unit = {},
    val onClickDate: (LocalDate) -> Unit = {},
    val onClickAllDays: () -> Unit = {},
    val onSelectTime: (LocalTime) -> Unit = {},
    val onResetToNow: () -> Unit = {},
    val onClickCopyBitsOfDateToClipboard: (LocalDate) -> Unit = {},
    val onClickEditBit: (Bit) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onDeleteBit: (Bit) -> Unit = {},
    val onScrolledToBit: () -> Unit = {}
)
