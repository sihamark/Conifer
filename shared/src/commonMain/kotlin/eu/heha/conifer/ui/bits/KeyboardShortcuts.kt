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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.input.key.isCtrlPressed
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
import conifer.shared.generated.resources.shortcuts_note_mac
import conifer.shared.generated.resources.shortcuts_now
import conifer.shared.generated.resources.shortcuts_save
import conifer.shared.generated.resources.shortcuts_time
import conifer.shared.generated.resources.shortcuts_title
import conifer.shared.generated.resources.shortcuts_today
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Which modifier the screen's shortcuts are held down with — a question about the platform's *text
 * editing*, not about its keyboard, which is why it is decided per platform rather than per key.
 *
 * Everywhere except Apple's platforms that modifier is Alt, which no text field wants. On macOS and
 * iPadOS Alt is ⌥, and ⌥ is what moves and selects by word — so a screen that claimed ⌥←/→ for its
 * days would take word-jump away from every field on it, and taking a system-wide editing key away
 * from someone who has used it for years is worse than any shortcut is good. There the shortcuts add
 * Ctrl (⌃), which the text system has no use for and macOS itself only binds with the bare arrows
 * (⌃←/→ switches spaces, and never reaches the app).
 *
 * The keys themselves are the same on both, so this only says what has to be held while pressing
 * them — see [handleShortcut] for the keys and [shortcutGroups] for how the pair is written out.
 */
enum class ShortcutChord(internal val label: String) {
    /** Alt alone: Windows, Linux, Android, and any web browser not on a Mac. */
    Alt("Alt"),

    /** Ctrl and Alt together (⌃⌥), leaving ⌥ on its own to the words: macOS and iPadOS. */
    CtrlAlt("Ctrl+Alt");

    /**
     * Whether this is the chord being held. [Alt] does not mind Ctrl also being down — it never had
     * a meaning of its own there — but [CtrlAlt] insists on it, since that is the whole point.
     */
    internal fun isHeld(event: KeyEvent): Boolean = when (this) {
        Alt -> event.isAltPressed
        CtrlAlt -> event.isAltPressed && event.isCtrlPressed
    }
}

/**
 * The screen's keyboard shortcuts: adjusting what the composer will stamp on the bit, and getting
 * out of things.
 *
 * Esc is the getting out, and it takes one thing at a time — the list of shortcuts, then whatever
 * [waysOut] holds, and only with nothing left to be in does it mean "every day and now", which it
 * then means unconditionally. See [waysOut] for what does and does not belong in that order.
 *
 * The [chord] and the arrows are one idea, which is why they are in one place: they change the
 * stamp, vertically the time by a slider slot, horizontally the day. They take a modifier because the
 * text field usually has focus and has a claim on every unmodified key — ↑/↓ and ←/→ are all the
 * caret's, in a field that can hold several lines now, and how many it holds depends on the window,
 * so bare arrows would mean one thing on a tall window and another on a short one. With the chord
 * they mean the same everywhere, at any place in the text, whether the field has the cursor or not.
 * ←/→ are the way round they are because that is the way the day strip runs: today at the right, and
 * back through the month to the left.
 *
 * PageUp/PageDown do the same as ←/→, for a keyboard whose arrows are awkward to reach in
 * combination and for anyone who reads days as pages; they follow the "previous/next" reading of
 * those keys rather than the list's scroll direction, since ←/→ is what they stand in for.
 *
 * The day keys go through actions of their own ([BitsPaneActions.onShiftDate] and the rest) because
 * they carry a policy the day lists don't — what happens to the filter. The time nudge has no such
 * thing to say and so just picks a time, exactly as the slider does.
 *
 * **Every key handled here is listed in [shortcutGroups] below**, which is what the overlay shows;
 * they are in one file so that a key added to one and not the other is visible in a single screen.
 *
 * Returns whether the event was the screen's, which is what stops it reaching the field.
 */
internal fun handleShortcut(
    event: KeyEvent,
    state: BitsPaneState,
    actions: BitsPaneActions,
    chord: ShortcutChord,
    isShortcutsOverlayOpen: Boolean,
    onShortcutsOverlayChange: (Boolean) -> Unit,
    /**
     * What Esc closes before it means anything else, innermost first: one press closes one thing,
     * and this list is where that order is written down (see [BitsPane], which builds it).
     *
     * Only what the screen draws inside itself goes in here. A dialog, a menu and sync's popover are
     * each a window of their own: they take focus with them and keep their own Esc, so a press meant
     * for one never reaches this function and needs no place in the order.
     */
    waysOut: List<() -> Unit> = emptyList()
): Boolean {
    // Only the presses; taking the releases as well would switch two days per key.
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.Escape) {
        // The list of shortcuts is the outermost thing to be in and the first to be dismissed: it is
        // in the way of everything else, so it is never what the user means to keep. Not part of
        // [waysOut] because it also has the keyboard to itself while it is up, below.
        if (isShortcutsOverlayOpen) {
            onShortcutsOverlayChange(false)
            return true
        }
        // Then whatever else the screen has open over itself, in the order it was given.
        waysOut.firstOrNull()?.let { closeIt ->
            closeIt()
            return true
        }
        // Nothing left to be in: every day and now again — the time included, unlike the day lists'
        // "All days" — and with it the day lists home from wherever they were scrolled.
        //
        // Unconditional, which is the point of the key. With nothing selected there is no other
        // field to change, and that is exactly the state a list scrolled a year into the past is in:
        // making the press conditional on something else moving would leave it doing nothing in the
        // one case that most wants a way back. Nothing is left to fall through to either, now that
        // everything the screen can be in is either handled above or a window of its own.
        actions.onResetSelection()
        return true
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
        if (chord.isHeld(event) && event.key == Key.H) onShortcutsOverlayChange(false)
        return true
    }
    if (!chord.isHeld(event)) return false
    val days = when (event.key) {
        // The time: off the same effective value the slider and the chip show, and
        // [shiftedByTimeSlots] takes care of a time that isn't on a slot to begin with.
        Key.DirectionUp, Key.DirectionDown -> {
            val slots = if (event.key == Key.DirectionUp) 1 else -1
            actions.onSelectTime(state.effectiveTime.shiftedByTimeSlots(slots))
            return true
        }

        // The time's way back to the clock, the pair of the day's way back to today: the two ends of
        // the stamp, on the two keys that sit next to each other, and on N for "now" beside T for
        // "today" — see the letters below. Only the time; the day it goes with is the other key's to
        // change, so someone writing yesterday's bits at the right time can fix the time without
        // being sent back to today.
        Key.MoveEnd, Key.N -> {
            actions.onResetTime()
            return true
        }

        Key.DirectionLeft, Key.PageUp -> -1
        Key.DirectionRight, Key.PageDown -> 1
        // MoveHome, not Home: the latter is Android's system home key, which never reaches an app.
        Key.MoveHome, Key.T -> {
            actions.onSelectToday()
            return true
        }
        // The keyboard's "All days", which the stacked layout otherwise only offers as tapping the
        // selected day a second time.
        Key.Zero, Key.NumPad0 -> {
            actions.onClickAllDays()
            return true
        }

        // H for help, and a letter for the same reason T and N are letters: it has to be reachable
        // on every keyboard, whatever it has and whatever is printed on it. A chord with / would
        // need Shift+7 as well on a German layout, and Home/End are missing altogether from every
        // laptop Apple makes — there they are fn+←/→, which with the chord on top is no shortcut at
        // all. So each of those three keys has a letter, and the letter is the one that is always
        // there. Closing the list again is handled above, where it has the keyboard to itself.
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
internal fun ShortcutsOverlay(
    chord: ShortcutChord,
    onDismiss: () -> Unit,
    /**
     * Whether this is a build being worked on (`ConiferApp.isDebug`, which `BitsRoute` reads). The
     * card is where the tools for breaking the app on purpose live, and they are for whoever is
     * building it - defaulted to false, so a preview or a test shows the card as a user sees it.
     */
    isDebug: Boolean = false
) {
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
                    // Only ever in a build somebody is working on: it exists to end the run on
                    // purpose, which is how the crash notice and the report it offers are tried out
                    // (see `RunEndPrompt`), and a button that crashes the app is the last thing a
                    // release should hand anybody.
                    if (isDebug) {
                        FilledTonalButton(
                            onClick = {
                                val errorText = (0..1)
                                    .joinToString { "this is a debug crash, build to test the log test\n" }
                                error(errorText)
                            }
                        ) {
                            Text("debug crash")
                        }
                    }
                    shortcutGroups(chord).forEachIndexed { index, group ->
                        if (index > 0) Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(group.title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        group.rows.forEach { row -> ShortcutRow(row, chord) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            // Only worth saying where the keys are not spelled the way they are
                            // printed, which is the one platform that writes them as symbols.
                            when (chord) {
                                ShortcutChord.Alt -> Res.string.shortcuts_note
                                ShortcutChord.CtrlAlt -> Res.string.shortcuts_note_mac
                            }
                        ),
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
private fun ShortcutRow(row: ShortcutRow, chord: ShortcutChord) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        // The keys share a column of their own so the descriptions line up down the page, and it is
        // wide enough for the longest of them ("Shift+Alt+←/→", or "Shift+Ctrl+Alt+←/→" where the
        // chord is the longer one) at this text size.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.width(keyColumnWidth(chord))
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
 * Built per [chord] rather than written out twice, because the chord is the only thing that differs
 * between platforms and a second copy of this list is a second place to forget a key in.
 *
 * The keys are spelled as plainly as they can be — "Ctrl+Alt" rather than ⌃⌥, with the one note
 * about the symbols left to [Res.string.shortcuts_note_mac] — because these strings stand for what is
 * printed on the key, and ⌃⌥ is printed nowhere. What *does* change per platform is which keys are
 * listed at all: a list naming a chord this platform doesn't answer to would be worse than any
 * spelling of it.
 *
 * Anything added to [handleShortcut] belongs here too.
 */
private fun shortcutGroups(chord: ShortcutChord): List<ShortcutGroup> {
    val held = chord.label
    return listOf(
        ShortcutGroup(
            title = Res.string.shortcuts_group_composing,
            rows = listOf(
                ShortcutRow("Enter", Res.string.shortcuts_save),
                ShortcutRow("Shift+Enter", Res.string.shortcuts_line_break),
                ShortcutRow("$held+↑/↓", Res.string.shortcuts_time),
                ShortcutRow(listOf("$held+N", "$held+End"), Res.string.shortcuts_now)
            )
        ),
        ShortcutGroup(
            title = Res.string.shortcuts_group_days,
            rows = listOf(
                ShortcutRow(listOf("$held+←/→", "$held+PgUp/PgDn"), Res.string.shortcuts_day),
                ShortcutRow("Shift+$held+←/→", Res.string.shortcuts_day_with_bits),
                ShortcutRow(listOf("$held+T", "$held+Home"), Res.string.shortcuts_today),
                ShortcutRow("$held+0", Res.string.shortcuts_all_days)
            )
        ),
        ShortcutGroup(
            title = Res.string.shortcuts_group_leaving,
            rows = listOf(
                ShortcutRow("Esc", Res.string.shortcuts_escape),
                ShortcutRow(listOf("$held+H", "F1"), Res.string.shortcuts_help)
            )
        )
    )
}

private fun keyColumnWidth(chord: ShortcutChord) = when (chord) {
    ShortcutChord.Alt -> 116.dp
    ShortcutChord.CtrlAlt -> 152.dp
}

/** How wide the card gets on a window with room to spare, and how dark the screen behind it goes. */
private val OVERLAY_MAX_WIDTH = 420.dp
private const val SCRIM_ALPHA = 0.5f
