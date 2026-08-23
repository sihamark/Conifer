package eu.heha.conifer.ui.bits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.shortcuts_action_close
import conifer.shared.generated.resources.shortcuts_all_days
import conifer.shared.generated.resources.shortcuts_day
import conifer.shared.generated.resources.shortcuts_day_with_bits
import conifer.shared.generated.resources.shortcuts_escape
import conifer.shared.generated.resources.shortcuts_group_composing
import conifer.shared.generated.resources.shortcuts_group_days
import conifer.shared.generated.resources.shortcuts_group_leaving
import conifer.shared.generated.resources.shortcuts_help
import conifer.shared.generated.resources.shortcuts_line_break
import conifer.shared.generated.resources.shortcuts_note
import conifer.shared.generated.resources.shortcuts_save
import conifer.shared.generated.resources.shortcuts_time
import conifer.shared.generated.resources.shortcuts_title
import conifer.shared.generated.resources.shortcuts_today
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The screen's keyboard shortcuts: adjusting what the composer will stamp on the bit, and getting
 * out of things.
 *
 * Alt and the arrows are one idea, which is why they are in one place: they change the stamp,
 * vertically the time by a slider slot, horizontally the day. They take Alt because the text field
 * usually has focus and has a claim on every unmodified key — ↑/↓ and ←/→ are all the caret's, in a
 * field that can hold several lines now, and how many it holds depends on the window, so bare arrows
 * would mean one thing on a tall window and another on a short one. With Alt they mean the same
 * everywhere, at any place in the text, whether the field has the cursor or not. ←/→ are the way
 * round they are because that is the way the day strip runs: today at the right, and back through the
 * month to the left.
 *
 * PageUp/PageDown do the same as ←/→. They are here because Alt+←/→ is word-jump on macOS, so anyone
 * who wants that back needs somewhere else to switch days from; they follow the "previous/next"
 * reading of those keys rather than the list's scroll direction, since Alt+← is what they stand in
 * for.
 *
 * The day keys go through actions of their own ([BitsPaneActions.onShiftDate] and the rest) because
 * they carry a policy the day lists don't — what happens to the filter. The time nudge has no such
 * thing to say and so just picks a time, exactly as the slider does.
 *
 * **Every key handled here is listed in [SHORTCUT_GROUPS] below**, which is what the overlay shows;
 * they are in one file so that a key added to one and not the other is visible in a single screen.
 *
 * Returns whether the event was the screen's, which is what stops it reaching the field.
 */
internal fun handleShortcut(
    event: KeyEvent,
    state: BitsPaneState,
    actions: BitsPaneActions,
    isShortcutsOverlayOpen: Boolean,
    onShortcutsOverlayChange: (Boolean) -> Unit
): Boolean {
    // Only the presses; taking the releases as well would switch two days per key.
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.Escape) {
        return when {
            // The list of shortcuts is the outermost thing to be in and the first to be dismissed:
            // it is in the way of everything else, so it is never what the user means to keep.
            isShortcutsOverlayOpen -> {
                onShortcutsOverlayChange(false)
                true
            }

            // Whichever of the two the user is in, innermost first: an edit is the more recent
            // thing to have got into, and the more surprising one to be left in.
            state.editingBitId != null -> {
                actions.onCancelEdit()
                true
            }

            // Every day and now again — the time included, unlike the day lists' "All days".
            state.hasSelection -> {
                actions.onResetSelection()
                true
            }

            // Nothing to get out of. Left unhandled rather than swallowed, in case anything below
            // has its own use for it — a dialog's dismiss, say.
            else -> false
        }
    }
    // F1 without a modifier, since that is what F1 is everywhere and it is no use to a text field.
    // It is not the only way in, because macOS keeps F1 for itself unless the keyboard is set to
    // send function keys — which is what Alt+H is for.
    if (event.key == Key.F1 && !event.isAltPressed) {
        onShortcutsOverlayChange(!isShortcutsOverlayOpen)
        return true
    }
    if (isShortcutsOverlayOpen) {
        // While the list is up the keyboard is its own. It is drawn over the text field, which still
        // has the cursor, and letters going into a field nobody can see is the one outcome to rule
        // out — so everything is swallowed here except the two keys above, which put it away.
        if (event.isAltPressed && event.key == Key.H) onShortcutsOverlayChange(false)
        return true
    }
    if (!event.isAltPressed) return false
    val days = when (event.key) {
        // The time: off the same effective value the slider and the chip show, and
        // [shiftedByTimeSlots] takes care of a time that isn't on a slot to begin with.
        Key.DirectionUp, Key.DirectionDown -> {
            val slots = if (event.key == Key.DirectionUp) 1 else -1
            actions.onSelectTime(state.effectiveTime.shiftedByTimeSlots(slots))
            return true
        }

        Key.DirectionLeft, Key.PageUp -> -1
        Key.DirectionRight, Key.PageDown -> 1
        // MoveHome, not Home: the latter is Android's system home key, which never reaches an app.
        Key.MoveHome -> {
            actions.onSelectToday()
            return true
        }
        // The keyboard's "All days", which the stacked layout otherwise only offers as tapping the
        // selected day a second time.
        Key.Zero, Key.NumPad0 -> {
            actions.onClickAllDays()
            return true
        }

        // H for help, and a letter because it has to be reachable on any layout — Alt+/ would be
        // Alt+Shift+7 on a German keyboard. Closing again is handled above, where the list has the
        // keyboard to itself.
        Key.H -> {
            onShortcutsOverlayChange(true)
            return true
        }

        else -> return false
    }
    // Most of the month is empty days, so Shift steps over them to the writing.
    if (event.isShiftPressed) actions.onSkipToDateWithBits(days) else actions.onShiftDate(days)
    return true
}

/**
 * The whole of [handleShortcut], written out for the user.
 *
 * Shown over the screen rather than beside it: it is read once or twice and then never again, so it
 * gets no permanent room, and it is dismissed the way it is opened, by the keyboard — the one thing
 * everybody reading it is holding.
 *
 * Part of the screen rather than a dialog of its own, which is what makes that last part true: a
 * dialog is a separate window and takes focus with it, so the key that opened this would no longer
 * reach the screen that handles it, and Esc would be somebody else's. In the tree, the screen keeps
 * hold of the keyboard — and [handleShortcut] keeps the whole of it in one place, which is also what
 * lets it swallow the keys that would otherwise land in the text field behind this.
 */
@Composable
internal fun ShortcutsOverlay(onDismiss: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            // Anywhere off the card puts it away, as anywhere off a dialog would.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                // Clear of the status bar and the keyboard, whatever the window is doing.
                .safeDrawingPadding()
                .padding(24.dp)
                .widthIn(max = OVERLAY_MAX_WIDTH)
                // The card is not the scrim: a press meant for the card must not also dismiss it.
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(Res.string.shortcuts_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))
                // Scrolls because the list is as long as it is and the window may be a phone in
                // landscape, which has less height than this needs.
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    SHORTCUT_GROUPS.forEachIndexed { index, group ->
                        if (index > 0) Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(group.title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        group.rows.forEach { row -> ShortcutRow(row) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.shortcuts_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(Res.string.shortcuts_action_close))
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(row: ShortcutRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        // The keys share a column of their own so the descriptions line up down the page, and it is
        // wide enough for the longest of them ("Shift+Alt+←/→") at this text size.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.width(KEY_COLUMN_WIDTH)
        ) {
            row.keys.forEach { keys -> KeyCap(keys) }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(row.description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

/** One key or combination, drawn as the thing you press. */
@Composable
private fun KeyCap(keys: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = keys,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

/** One line of the overlay: what to press, and what it does. */
private class ShortcutRow(val keys: List<String>, val description: StringResource) {
    constructor(keys: String, description: StringResource) : this(listOf(keys), description)
}

private class ShortcutGroup(val title: StringResource, val rows: List<ShortcutRow>)

/**
 * What the overlay lists, in the order it lists it: the composer's own keys first, since they are the
 * ones used while writing, then the days, then the ways out.
 *
 * The keys are spelled here as plainly as they can be — "Alt" rather than a platform's symbol for it,
 * with the one note about ⌥ left to [Res.string.shortcuts_note] — because these strings stand for
 * what is printed on the key, and a list that renamed them per platform would be harder to match
 * against the keyboard in front of the reader, not easier.
 *
 * Anything added to [handleShortcut] belongs here too.
 */
private val SHORTCUT_GROUPS = listOf(
    ShortcutGroup(
        title = Res.string.shortcuts_group_composing,
        rows = listOf(
            ShortcutRow("Enter", Res.string.shortcuts_save),
            ShortcutRow("Shift+Enter", Res.string.shortcuts_line_break),
            ShortcutRow("Alt+↑/↓", Res.string.shortcuts_time)
        )
    ),
    ShortcutGroup(
        title = Res.string.shortcuts_group_days,
        rows = listOf(
            ShortcutRow(listOf("Alt+←/→", "Alt+PgUp/PgDn"), Res.string.shortcuts_day),
            ShortcutRow("Shift+Alt+←/→", Res.string.shortcuts_day_with_bits),
            ShortcutRow("Alt+Home", Res.string.shortcuts_today),
            ShortcutRow("Alt+0", Res.string.shortcuts_all_days)
        )
    ),
    ShortcutGroup(
        title = Res.string.shortcuts_group_leaving,
        rows = listOf(
            ShortcutRow("Esc", Res.string.shortcuts_escape),
            ShortcutRow(listOf("Alt+H", "F1"), Res.string.shortcuts_help)
        )
    )
)

private val KEY_COLUMN_WIDTH = 116.dp

/** How wide the card gets on a window with room to spare, and how dark the screen behind it goes. */
private val OVERLAY_MAX_WIDTH = 420.dp
private const val SCRIM_ALPHA = 0.5f
