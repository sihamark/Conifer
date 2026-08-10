package eu.heha.conifer.ui.bits

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.bits_action_cancel
import conifer.shared.generated.resources.bits_action_set_time
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource

/**
 * Material's own time picker, for the minute-exact time the slider is not meant for. It is the
 * platform's picker in every way that shows: whether it is a 12- or a 24-hour clock is
 * `rememberTimePickerState`'s own answer, taken from the same locale
 * [eu.heha.conifer.DateTimeFormats] spells the time with, so the dialog and the chip that opened it
 * never disagree about how a time is written.
 *
 * Nothing is committed until "Set time" — an accidental drag across the dial should be as free to
 * back out of as opening the dialog was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeOfDayPickerDialog(
    time: LocalTime,
    /**
     * Which of the two halves to open on. Only the first press is decided here — the toggle beside
     * the buttons switches from then on — so the caller is free to guess.
     */
    initialDisplayMode: TimePickerDisplayMode,
    onSelectTime: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    // Seeded from whatever the chip is showing — the clock's own time while nothing has been picked
    // — down to the minute, which is as fine as anything here reads.
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
    var displayMode by remember { mutableStateOf(initialDisplayMode) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { TimePickerDialogDefaults.Title(displayMode) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    displayMode = if (displayMode == TimePickerDisplayMode.Picker) {
                        TimePickerDisplayMode.Input
                    } else {
                        TimePickerDisplayMode.Picker
                    }
                },
                displayMode = displayMode
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.bits_action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelectTime(LocalTime(state.hour, state.minute))
                    onDismiss()
                }
            ) {
                Text(stringResource(Res.string.bits_action_set_time))
            }
        }
    ) {
        if (displayMode == TimePickerDisplayMode.Input) {
            TimeInput(state)
        } else {
            TimePicker(state)
        }
    }
}
