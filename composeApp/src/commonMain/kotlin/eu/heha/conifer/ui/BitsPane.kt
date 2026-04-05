package eu.heha.conifer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import conifer.composeapp.generated.resources.Res
import conifer.composeapp.generated.resources.bits_title
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.theme.ConiferTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

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
                onNewBitTextChange = actions.onNewBitTextChange,
                onClickAdd = actions.onClickAdd,
                focusRequester = focusRequester
            )
            DaySelection(
                dates = state.dates,
                selectedDate = state.selectedDate,
                currentDate = state.today,
                onClickDate = actions.onClickDate,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                    items(datedBits.bits, key = { it.id }) {
                        BitItem(bit = it, modifier = Modifier.animateItem())
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
            val day = date.day
            val month = date.month.number
            Surface(
                color = MaterialTheme.colorScheme.let {
                    if (isCurrent) it.surfaceVariant else it.surface
                },
                onClick = { onClickDate(date) },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)
                    .takeIf { isSelected },
                shape = CircleShape,
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
                        text = day.toString(),
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
                        text = month.toString(),
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
    onNewBitTextChange: (String) -> Unit,
    onClickAdd: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = newBitText,
            onValueChange = onNewBitTextChange,
            label = { Text("New Bit") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions { onClickAdd() },
            singleLine = true,
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .weight(1f)
        )
        AnimatedVisibility(newBitText.isNotBlank()) {
            Row {
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onClickAdd) {
                    Icon(Icons.Default.Check, contentDescription = "Add Bit")
                }
            }
        }
    }
}

@Composable
private fun BitItem(bit: Bit, modifier: Modifier = Modifier) {
    Card(
        modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(8.dp)) {
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
    }
}


data class BitsPaneState(
    val permissionRationale: String? = null,
    val isCopyPossible: Boolean = true,
    val newBitText: String = "",
    val selectedDate: LocalDate? = null,
    val today: LocalDate = now().date,
    val dates: List<LocalDate> = emptyList(),
    val bitsByDate: List<DatedBits> = emptyList()
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
    val onClickCopyBitsOfDateToClipboard: (LocalDate) -> Unit = {}
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