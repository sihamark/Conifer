package eu.heha.conifer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_title
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.theme.ConiferTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitsPane(
    state: BitsPaneState = BitsPaneState(),
    actions: BitsPaneActions = BitsPaneActions()
) {
    Scaffold(
        contentWindowInsets = WindowInsets(),
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(Res.string.bits_title)) })
        }
    ) { innerPadding ->
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(state.newBitText) {
            if (state.newBitText.isBlank()) focusRequester.requestFocus()
        }
        Column(modifier = Modifier.padding(innerPadding).imePadding()) {
            val permissionRationale = state.permissionRationale
            AnimatedVisibility(permissionRationale != null) {
                PermissionPrompt(permissionRationale ?: "", actions)
            }
            NewBitText(
                newBitText = state.newBitText,
                isEditing = state.editingBitId != null,
                onNewBitTextChange = actions.onNewBitTextChange,
                onClickAdd = actions.onClickAdd,
                onCancelEdit = actions.onCancelEdit,
                focusRequester = focusRequester
            )
            DateTimeSelector(
                dates = state.dates,
                selectedDate = state.selectedDate,
                selectedTime = state.selectedTime,
                currentDate = state.today,
                currentTime = state.currentTime,
                onClickDate = actions.onClickDate,
                onSelectTime = actions.onSelectTime,
                onResetToNow = actions.onResetToNow
            )
            LazyColumn {
                state.bitsByDate.forEach { datedBits ->
                    stickyHeader(key = datedBits.date.toEpochDays()) {
                        DateHeader(
                            date = datedBits.date,
                            onClickCopy = {
                                actions.onClickCopyBitsOfDateToClipboard(datedBits.date)
                            }
                        )
                    }
                    items(datedBits.bits, key = { it.id }) { bit ->
                        BitItem(
                            bit = bit,
                            isEditing = state.editingBitId == bit.id,
                            onClickStartEdit = { actions.onClickEditBit(bit) },
                            onClickCancelEdit = actions.onCancelEdit,
                            onClickDelete = { actions.onDeleteBit(bit) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Spacer(
                        Modifier.windowInsetsBottomHeight(WindowInsets.systemBars)
                    )
                }
            }
        }
    }
}

/**
 * Compact date & time control. Collapsed it only shows the current date and time; expanding it
 * reveals the day picker and a slim time slider. While collapsed the current date and time are
 * always used, so closing the picker reverts any custom selection back to "now".
 */
@Composable
private fun DateTimeSelector(
    dates: List<LocalDate>,
    selectedDate: LocalDate?,
    selectedTime: LocalTime?,
    currentDate: LocalDate,
    currentTime: LocalTime,
    onClickDate: (LocalDate) -> Unit,
    onSelectTime: (LocalTime) -> Unit,
    onResetToNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val effectiveDate = selectedDate ?: currentDate
    val effectiveTime = selectedTime ?: currentTime

    fun toggle() {
        isExpanded = !isExpanded
        // Closing the picker means "use now" again, so drop any custom date/time.
        if (!isExpanded) onResetToNow()
    }

    Column(modifier) {
        Surface(
            onClick = ::toggle,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${effectiveDate.label(currentDate)} · ${effectiveTime.print()}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.weight(1f))
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "chevronRotation"
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Hide date picker" else "Show date picker",
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation)
                )
            }
        }
        AnimatedVisibility(isExpanded) {
            Column {
                DaySelection(
                    dates = dates,
                    selectedDate = selectedDate,
                    currentDate = currentDate,
                    onClickDate = onClickDate,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                TimeSlider(
                    time = effectiveTime,
                    onSelectTime = onSelectTime,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeSlider(
    time: LocalTime,
    onSelectTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        // The slider works in 15-minute slots (0 = 00:00 … LAST = 23:45) so the discrete
        // steps line up exactly and draw a tick mark at every quarter hour.
        Slider(
            value = ((time.hour * 60 + time.minute) / MINUTES_PER_STEP).toFloat(),
            onValueChange = { slotIndex ->
                val minutes = slotIndex.roundToInt() * MINUTES_PER_STEP
                onSelectTime(LocalTime(minutes / 60, minutes % 60))
            },
            valueRange = 0f..LAST_STEP_SLOT.toFloat(),
            steps = INTERMEDIATE_STEPS,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = time.print(),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private const val MINUTES_PER_STEP = 15

// Number of 15-minute slots in a day (96: 00:00 … 23:45).
private const val STEP_SLOTS = 24 * 60 / MINUTES_PER_STEP

// Highest slider value (slot 95 == 23:45).
private const val LAST_STEP_SLOT = STEP_SLOTS - 1

// Slider `steps` counts the marks strictly between the endpoints.
private const val INTERMEDIATE_STEPS = STEP_SLOTS - 2

private fun LocalDate.label(today: LocalDate): String =
    if (this == today) "Today" else print()

@Composable
fun DaySelection(
    dates: List<LocalDate>,
    selectedDate: LocalDate?,
    currentDate: LocalDate,
    onClickDate: (LocalDate) -> Unit,
    modifier: Modifier
) {
    LazyRow(
        reverseLayout = true,
        modifier = modifier
    ) {
        items(30, key = { it }) { dayIndex ->
            val date = LocalDate.fromEpochDays(currentDate.toEpochDays() - dayIndex)
            val isSelected = date == selectedDate
            val isCurrent = date == currentDate
            val hasEntries = date in dates
            val dayOfWeek = date.dayOfWeek.name.take(3).lowercase()
            val day = date.day
            val month = date.month.number
            Surface(
                color = MaterialTheme.colorScheme.let {
                    if (isCurrent) it.surfaceVariant else it.surface
                },
                onClick = { onClickDate(date) },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)
                    .takeIf { isSelected },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .size(48.dp)
                    .padding(2.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val indicatorColor =
                        if (hasEntries) MaterialTheme.colorScheme.primary else Color.Transparent

                    @Composable
                    fun IndicatorDot() = Box(Modifier.size(2.dp).background(indicatorColor))
                    Text(
                        text = dayOfWeek,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(0.3f)
                    ) {
                        repeat(3) { IndicatorDot() }
                    }
                    Text(
                        text = "$day.$month",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, onClickCopy: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date.print(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClickCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy bits of this date to clipboard",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    permissionRationale: String,
    actions: BitsPaneActions
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = permissionRationale,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(actions.onClickRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun NewBitText(
    newBitText: String,
    isEditing: Boolean,
    onNewBitTextChange: (String) -> Unit,
    onClickAdd: () -> Unit,
    onCancelEdit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = newBitText,
            onValueChange = onNewBitTextChange,
            label = { Text(if (isEditing) "Edit Bit" else "New Bit") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions { onClickAdd() },
            singleLine = true,
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .weight(1f)
        )
        AnimatedVisibility(isEditing) {
            Row {
                Spacer(Modifier.width(8.dp))
                OutlinedIconButton(onClick = onCancelEdit) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel editing")
                }
            }
        }
        AnimatedVisibility(newBitText.isNotBlank()) {
            Row {
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onClickAdd) {
                    if (isEditing) {
                        Icon(Icons.Default.Edit, contentDescription = "Save Bit")
                    } else {
                        Icon(Icons.Default.Check, contentDescription = "Add Bit")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BitItem(
    bit: Bit,
    isEditing: Boolean,
    onClickStartEdit: () -> Unit,
    onClickCancelEdit: () -> Unit,
    onClickDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            .takeIf { isEditing },
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(
                onClick = {},
                // Double-clicking a bit starts editing it, mirroring the "Edit" menu action.
                onDoubleClick = onClickStartEdit
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    bit.text,
                    style = MaterialTheme.typography.bodyLarge
                )
                val date = bit.date.toLocalDateTime(TimeZone.currentSystemDefault())
                Text(
                    date.time.print(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Bit options"
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isEditing) "Cancel Edit" else "Edit") },
                        leadingIcon = {
                            Icon(
                                if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            if (isEditing) onClickCancelEdit() else onClickStartEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete bit?") },
            text = { Text("This bit will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onClickDelete()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


data class BitsPaneState(
    val permissionRationale: String? = null,
    val isCopyPossible: Boolean = true,
    val newBitText: String = "",
    val selectedDate: LocalDate? = null,
    val selectedTime: LocalTime? = null,
    val today: LocalDate = now().date,
    val currentTime: LocalTime = now().time,
    val dates: List<LocalDate> = emptyList(),
    val bitsByDate: List<DatedBits> = emptyList(),
    val editingBitId: String? = null
)

data class DatedBits(
    val date: LocalDate,
    val bits: List<Bit>
)

class BitsPaneActions(
    val onClickAdd: () -> Unit = {},
    val onNewBitTextChange: (String) -> Unit = {},
    val onClickRequestPermission: () -> Unit = {},
    val onClickDate: (LocalDate) -> Unit = {},
    val onSelectTime: (LocalTime) -> Unit = {},
    val onResetToNow: () -> Unit = {},
    val onClickCopyBitsOfDateToClipboard: (LocalDate) -> Unit = {},
    val onClickEditBit: (Bit) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onDeleteBit: (Bit) -> Unit = {}
)

@Preview
@Composable
private fun BitsPanePreview() {
    ConiferTheme {
        BitsPane(
            state = BitsPaneState(
                newBitText = "This is a new bit",
                bitsByDate = listOf(
                    DatedBits(
                        date = LocalDate(2024, 6, 1),
                        bits = listOf(
                            Bit(text = "First bit"),
                            Bit(text = "Second bit"),
                            Bit(text = "Third bit")
                        )
                    )
                )
            )
        )
    }
}