package eu.heha.conifer.ui.bits

import eu.heha.conifer.PermissionRationale
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.now
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class BitsPaneState(
    val permissionRationale: PermissionRationale? = null,
    val isCopyPossible: Boolean = true,
    val newBitText: String = "",
    val selectedDate: LocalDate? = null,
    val selectedTime: LocalTime? = null,
    val today: LocalDate = now().date,
    val currentTime: LocalTime = now().time,
    val bitsByDate: List<DatedBits> = emptyList(),
    val editingBitId: String? = null,
    /** One-shot request to scroll the list to this bit after it was added or edited. */
    val scrollToBitId: String? = null
)

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
