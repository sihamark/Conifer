package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.app_name
import conifer.shared.generated.resources.bits_label_counter
import conifer.shared.generated.resources.shortcuts_content_show
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.SyncPane
import eu.heha.conifer.ui.SyncPaneActions
import eu.heha.conifer.ui.SyncPresentation
import eu.heha.conifer.ui.SyncStatusIcon
import eu.heha.conifer.ui.SyncUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitsPane(
    state: BitsPaneState = BitsPaneState(),
    actions: BitsPaneActions = BitsPaneActions(),
    syncState: SyncUiState = SyncUiState(),
    syncActions: SyncPaneActions = SyncPaneActions(),
    // From the mockup, decided by Material's window size classes; see BitsLayout for what each one
    // arranges. Overridable so previews and tests can pick a layout instead of a window size.
    layout: BitsLayout = currentBitsLayout(),
    // Deliberately not part of BitsLayout: the sync pane does not rearrange the days, the bits
    // and the composer, it only takes a slice of the window away from them. Overridable like the
    // layout.
    syncPresentation: SyncPresentation = currentSyncPresentation(),
    // Only compact-height windows (phones in landscape, small foldable covers) are cramped enough
    // by the IME to be worth giving up the top bar for; from the medium height class up there is
    // room for both, and a bar that comes and goes with every keystroke is just noise.
    // Overridable for the same reason as the layout.
    doesImeHideTopBar: Boolean = !currentWindowAdaptiveInfoV2().windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
    // How tall the text field may grow; also not part of BitsLayout, since it is the height left
    // over that decides it and not how the panes are arranged. Overridable like the layout.
    composerMaxLines: Int = currentComposerMaxLines(),
    // Whether this device is one that comes with a keyboard, which decides whether the shortcuts are
    // advertised — see Platform.hasHardwareKeyboard, which is what BitsRoute passes. Defaulted rather
    // than injected here so that a preview or a test is a plain composable with nothing behind it,
    // and defaulted to the quieter answer: a screen with no keyboard is the one with nothing extra
    // on it.
    hasHardwareKeyboard: Boolean = false,
    // What the shortcuts are held down with, which is a property of the platform's text editing
    // rather than of this screen — see ShortcutChord, and BitsRoute for who decides it. Defaulted to
    // the answer that holds everywhere but Apple's platforms, for the same reason as above: a
    // preview or a test is a plain composable with nothing behind it.
    shortcutChord: ShortcutChord = ShortcutChord.Alt
) {
    Scaffold(contentWindowInsets = WindowInsets()) { innerPadding ->
        // Somewhere for the key events to go when the text field hasn't got them. A key event is
        // only offered to the focused node and the nodes above it — and with *nothing* focused it is
        // offered to key input above the root focus node, which the screen's own handler is below,
        // so it would then be offered to nothing at all. Without a focus target here, clicking a bit
        // or the background would quietly turn the shortcuts off until the field was clicked again.
        //
        // It is only ever a fallback, never taken from anything: focus is asked for here first, and
        // the field's own request — below, and so after it, and repeated whenever the field is empty
        // or an edit begins — takes it straight back off. What is left is a screen that holds focus
        // exactly when nothing on it does.
        val paneFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { paneFocusRequester.requestFocus() }
        var isShortcutsOverlayOpen by remember { mutableStateOf(false) }
        // The platform's answer is only a guess about the device (see Platform.hasHardwareKeyboard),
        // and a modifier arriving is proof: nothing on a touch keyboard sends Alt. So a tablet with a
        // keyboard plugged in earns the icon as soon as it is used, without asking the platform
        // anything it cannot answer.
        var hasSeenModifier by remember { mutableStateOf(false) }
        val isKeyboardPresent = hasHardwareKeyboard || hasSeenModifier
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(state.newBitText) {
            if (state.newBitText.isBlank()) focusRequester.requestFocus()
        }
        LaunchedEffect(state.editingBitId) {
            if (state.editingBitId != null) focusRequester.requestFocus()
        }
        // On a short window the IME already claims a large share of the screen, so the top bar is
        // hidden while typing to leave as much room as possible for reading existing bits.
        val isTopBarVisible = !doesImeHideTopBar ||
                WindowInsets.ime.getBottom(LocalDensity.current) == 0
        Row(
            modifier = Modifier
                .fillMaxSize()
                // Previewed at the top of the screen rather than on the text field, where the rest
                // of the shortcuts live: these are the screen's, not the field's, and a day is
                // switched just as often with a bit half-written and the mouse in the list.
                .onPreviewKeyEvent { event ->
                    if (event.isAltPressed) hasSeenModifier = true
                    handleShortcut(
                        event = event,
                        state = state,
                        actions = actions,
                        chord = shortcutChord,
                        isShortcutsOverlayOpen = isShortcutsOverlayOpen,
                        onShortcutsOverlayChange = { isShortcutsOverlayOpen = it }
                    )
                }
                .focusRequester(paneFocusRequester)
                .focusTarget()
                .padding(innerPadding)
                // The status bar is always padded here so showing/hiding the top bar only animates
                // the bar's own height and nothing else jumps.
                .statusBarsPadding()
        ) {
            // Resizing a desktop window across the breakpoint would otherwise make the sidebar
            // pop in and out. RowScope's defaults (expand/shrink horizontally from the end, plus
            // a fade) slide it out from under the divider, which stays flush with the main pane
            // and moves along with it. The layout the app starts in is not animated: an initially
            // visible AnimatedVisibility has nothing to animate from.
            AnimatedVisibility(visible = layout == BitsLayout.DaySidebar) {
                Row {
                    DaySidebar(
                        bitsByDate = state.bitsByDate,
                        selectedDate = state.filterDate,
                        currentDate = state.today,
                        isTopBarVisible = isTopBarVisible,
                        onClickDate = actions.onClickDate,
                        onClickAllDays = actions.onClickAllDays,
                        // The sidebar spans the whole pane height, so its content has to stay clear
                        // of the keyboard itself — the composer's own inset only shifts the main
                        // pane.
                        modifier = Modifier.imePadding()
                    )
                    VerticalDivider()
                }
            }
            MainPane(
                state = state,
                actions = actions,
                syncState = syncState,
                syncActions = syncActions,
                layout = layout,
                syncPresentation = syncPresentation,
                isTopBarVisible = isTopBarVisible,
                composerMaxLines = composerMaxLines,
                focusRequester = focusRequester,
                isKeyboardPresent = isKeyboardPresent,
                onClickShortcuts = { isShortcutsOverlayOpen = true },
                modifier = Modifier.weight(1f)
            )
            // The mirror image of the sidebar, on the other side of the main pane and shown only
            // while the user has sync open — where the smaller layouts put a sheet or a popover
            // over the bits, a window this wide can simply hand sync a pane of its own instead. The
            // animation is anchored to the start rather than RowScope's default end, so it is the
            // divider — flush with the main pane — that travels, as the sidebar's does.
            AnimatedVisibility(
                visible = syncPresentation == SyncPresentation.Pane && syncState.isSyncOpen,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
            ) {
                Row {
                    VerticalDivider()
                    SyncPane(
                        state = syncState,
                        actions = syncActions,
                        isTopBarVisible = isTopBarVisible,
                        // Like the sidebar, the pane spans the whole height, so it has to keep its
                        // own content — text fields included — clear of the keyboard and the
                        // navigation bar.
                        modifier = Modifier.windowInsetsPadding(bottomInsets)
                    )
                }
            }
        }
        if (isShortcutsOverlayOpen) {
            ShortcutsOverlay(
                chord = shortcutChord,
                onDismiss = { isShortcutsOverlayOpen = false }
            )
        }
    }
}

/** The bits themselves, with the composer either under them or — in landscape — beside them. */
@Composable
private fun MainPane(
    state: BitsPaneState,
    actions: BitsPaneActions,
    syncState: SyncUiState,
    syncActions: SyncPaneActions,
    layout: BitsLayout,
    syncPresentation: SyncPresentation,
    isTopBarVisible: Boolean,
    composerMaxLines: Int,
    focusRequester: FocusRequester,
    isKeyboardPresent: Boolean,
    onClickShortcuts: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The state always holds all days; the day filter only decides what the list (and the counter)
    // shows, so the day chips keep their indicators while filtering.
    val visibleBitsByDate = state.filterDate?.let { selected ->
        state.bitsByDate.filter { it.date == selected }
    } ?: state.bitsByDate
    // From the mockup: once the pane shares the window with something else, the bits and the
    // composer get a little more room to breathe than the phone layout's 16.dp. Animated so it
    // travels with the sidebar instead of snapping while the sidebar is still sliding.
    val paneInset by animateDpAsState(
        targetValue = if (layout == BitsLayout.Stacked) 0.dp else 8.dp,
        label = "paneInset"
    )
    val isSideBySide = layout == BitsLayout.SideComposer
    BitsAndComposer(
        isSideBySide = isSideBySide,
        bits = {
            Bits(
                state = state,
                visibleBitsByDate = visibleBitsByDate,
                actions = actions,
                syncState = syncState,
                syncActions = syncActions,
                isKeyboardPresent = isKeyboardPresent,
                onClickShortcuts = onClickShortcuts,
                syncPresentation = syncPresentation,
                isTopBarVisible = isTopBarVisible,
                paneInset = paneInset,
                // Beside the composer the bits reach the bottom of the window themselves, so they
                // have to keep clear of the keyboard; stacked, the composer under them does it.
                modifier = if (isSideBySide) {
                    Modifier.windowInsetsPadding(bottomInsets)
                } else {
                    Modifier
                }
            )
        },
        composer = {
            Composer(
                state = state,
                actions = actions,
                layout = layout,
                paneInset = paneInset,
                maxLines = composerMaxLines,
                focusRequester = focusRequester,
                isKeyboardPresent = isKeyboardPresent
            )
        },
        modifier = modifier
    )
}

/**
 * Places the bits and the composer either stacked — composer under the bits, spanning the pane —
 * or side by side with the composer at the bottom of its own column, [SIDE_COMPOSER_WIDTH] wide.
 *
 * The arrangement is chosen when measuring rather than when composing, so that both regions keep
 * the same single place in the composition either way. That matters because the arrangement now
 * changes as the keyboard opens: composing them under a Row in one case and a Column in the other
 * would rebuild them on the way over, and rebuilding the text field drops the focus that summoned
 * the keyboard — which closes it, which switches the arrangement straight back. (Moving them with
 * movableContentOf is not enough: it carries composition state across, but the focus still goes.)
 */
@Composable
private fun BitsAndComposer(
    isSideBySide: Boolean,
    bits: @Composable () -> Unit,
    composer: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        contents = listOf(bits, { VerticalDivider() }, composer),
        modifier = modifier
    ) { (bitsMeasurables, dividerMeasurables, composerMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val bitsMeasurable = bitsMeasurables.first()
        val dividerMeasurable = dividerMeasurables.first()
        val composerMeasurable = composerMeasurables.first()
        if (isSideBySide) {
            val divider = dividerMeasurable.measure(
                Constraints(minHeight = height, maxHeight = height)
            )
            val composerWidth = SIDE_COMPOSER_WIDTH.roundToPx()
                .coerceAtMost(width - divider.width)
            // The composer's own keyboard inset is part of its height, so placing it flush with
            // the bottom lands its content right above the keyboard.
            val composerPlaceable = composerMeasurable.measure(
                Constraints(
                    minWidth = composerWidth,
                    maxWidth = composerWidth,
                    maxHeight = height
                )
            )
            val bitsWidth = (width - divider.width - composerWidth).coerceAtLeast(0)
            val bitsPlaceable = bitsMeasurable.measure(Constraints.fixed(bitsWidth, height))
            layout(width, height) {
                bitsPlaceable.place(0, 0)
                divider.place(bitsWidth, 0)
                composerPlaceable.place(
                    bitsWidth + divider.width,
                    height - composerPlaceable.height
                )
            }
        } else {
            val composerPlaceable = composerMeasurable.measure(
                Constraints(minWidth = width, maxWidth = width, maxHeight = height)
            )
            // Measured away to nothing: the divider only separates the side-by-side arrangement.
            dividerMeasurable.measure(Constraints.fixed(0, 0))
            val bitsPlaceable = bitsMeasurable.measure(
                Constraints.fixed(width, (height - composerPlaceable.height).coerceAtLeast(0))
            )
            layout(width, height) {
                bitsPlaceable.place(0, 0)
                composerPlaceable.place(0, height - composerPlaceable.height)
            }
        }
    }
}

/**
 * The bits list with the top bar floating above it. The two share a Box so the bar does not push
 * the list down (and the bottom bits under the input) when it reappears. The list may start
 * underneath the bar, but its leading spacer keeps the content clear of it.
 */
@Composable
private fun Bits(
    state: BitsPaneState,
    visibleBitsByDate: List<DatedBits>,
    actions: BitsPaneActions,
    syncState: SyncUiState,
    syncActions: SyncPaneActions,
    syncPresentation: SyncPresentation,
    isTopBarVisible: Boolean,
    paneInset: Dp,
    isKeyboardPresent: Boolean,
    onClickShortcuts: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        // The list spans the whole pane so that a drag anywhere in it scrolls — including the
        // empty margins beside the bits on a wide window. Narrowing the list itself instead would
        // leave those margins outside the scrollable area and dead to the touch, so the width
        // limit lives in the content padding.
        val sideInset =
            ((maxWidth - CONTENT_MAX_WIDTH) / 2).coerceAtLeast(0.dp) + paneInset
        BitsList(
            state = state,
            visibleBitsByDate = visibleBitsByDate,
            actions = actions,
            isTopBarVisible = isTopBarVisible,
            // Only the list's content is inset; the top bar floating above it keeps spanning the
            // whole pane.
            contentPadding = PaddingValues(horizontal = sideInset),
            modifier = Modifier.fillMaxSize()
        )
        // Under the floating bar, and following it out of the way when it goes — the same inset the
        // list's own leading spacer keeps.
        val crashPromptTopInset by animateDpAsState(
            targetValue = if (isTopBarVisible) TopAppBarDefaults.TopAppBarExpandedHeight else 0.dp,
            label = "crashPromptTopInset"
        )
        // The crash notice floats over the top of the list, like the bar above it, rather than
        // scrolling with the bits: the list opens anchored to its newest bit, so a banner among the
        // items would sit far above the fold and never be seen by the one person it is for. It
        // travels with the bar as that comes and goes, and is gone for good once dismissed.
        RunEndPromptItem(
            state = state,
            actions = actions,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = crashPromptTopInset, start = paneInset, end = paneInset)
        )
        val bitsCount = visibleBitsByDate.sumOf { it.bits.size }
        Topbar(
            bitsCount = bitsCount,
            isVisible = isTopBarVisible,
            syncState = syncState,
            syncActions = syncActions,
            syncPresentation = syncPresentation,
            isKeyboardPresent = isKeyboardPresent,
            onClickShortcuts = onClickShortcuts
        )
    }
}

/** The date/time picker and the text field, on their own surface. */
@Composable
private fun Composer(
    state: BitsPaneState,
    actions: BitsPaneActions,
    layout: BitsLayout,
    paneInset: Dp,
    maxLines: Int,
    focusRequester: FocusRequester,
    isKeyboardPresent: Boolean,
    modifier: Modifier = Modifier
) {
    val isShort = layout == BitsLayout.SideComposer
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // Padding the insets inside the colored column extends its background behind the
            // navigation bar instead of leaving a differently colored strip below the input.
            .windowInsetsPadding(bottomInsets)
    ) {
        // The inset goes on the content, not on the colored column, so the composer's surface
        // still reaches the sidebar's divider.
        Column(
            Modifier
                // Kept to the same measure as the bits above it, so the text field and the day
                // strip line up with the cards instead of running off past them.
                .align(Alignment.CenterHorizontally)
                .widthIn(max = CONTENT_MAX_WIDTH)
                .padding(horizontal = paneInset)
                // Collapsed, the composer fits the height a landscape keyboard leaves exactly;
                // expanding the picker does not, so there it scrolls instead of squeezing the
                // text field. Nothing to scroll — and so nothing to notice — while collapsed.
                .then(if (isShort) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        ) {
            DateTimeSelector(
                bitsByDate = state.bitsByDate,
                composerDate = state.composerDate,
                composerTime = state.composerTime,
                filterDate = state.filterDate,
                currentDate = state.today,
                currentTime = state.currentTime,
                isEditing = state.editingBitId != null,
                // Only the sidebar layout takes the day off the picker's hands.
                isDaySelectionVisible = layout != BitsLayout.DaySidebar,
                isKeyboardPresent = isKeyboardPresent,
                onClickDate = actions.onClickDate,
                onSelectTime = actions.onSelectTime,
                onResetToNow = actions.onResetToNow,
                onCancelEdit = actions.onCancelEdit
            )
            NewBitText(
                newBitText = state.newBitText,
                isEditing = state.editingBitId != null,
                onNewBitTextChange = actions.onNewBitTextChange,
                onClickAdd = actions.onClickAdd,
                focusRequester = focusRequester,
                bottomPadding = if (isShort) 8.dp else 16.dp,
                maxLines = maxLines
            )
        }
    }
}

@Composable
private fun Topbar(
    bitsCount: Int,
    isVisible: Boolean,
    syncState: SyncUiState,
    syncActions: SyncPaneActions,
    syncPresentation: SyncPresentation,
    isKeyboardPresent: Boolean,
    onClickShortcuts: () -> Unit
) {
    AnimatedVisibility(visible = isVisible, modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.app_name)) },
            actions = {
                Text(
                    pluralStringResource(
                        Res.plurals.bits_label_counter,
                        bitsCount,
                        bitsCount
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                // Only where there is a keyboard to use them with. There is no point advertising
                // shortcuts to a phone, and nothing is lost by leaving them undiscovered on one.
                if (isKeyboardPresent) {
                    IconButton(onClick = onClickShortcuts) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = stringResource(Res.string.shortcuts_content_show)
                        )
                    }
                }
                SyncStatusIcon(
                    state = syncState,
                    actions = syncActions,
                    presentation = syncPresentation,
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            // The column already handles the status bar inset.
            windowInsets = WindowInsets()
        )
    }
}

/**
 * What has to stay clear at the bottom of the screen: the keyboard, and the navigation bar when
 * the keyboard is closed. Unioned rather than chained, because chaining navigationBarsPadding()
 * and imePadding() adds them up while an open IME already covers the navigation bar.
 */
private val bottomInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets.ime)

/**
 * Wide enough for the date chip's "back to now" button to sit next to it and for the day strip to
 * show a few days, while still leaving the bits the larger half of a landscape phone.
 */
private val SIDE_COMPOSER_WIDTH = 360.dp

/**
 * How wide the bits and the composer are allowed to grow, centred in whatever the pane leaves them.
 * A bit is a single short line of text, so on a maximized desktop or web window letting the cards
 * span the pane strands the time chip and the overflow button at opposite edges and makes the list
 * tiring to scan. Below this the pane is used in full, so it only takes effect on the widest
 * windows; the top bar and the composer's surface still span the pane either way.
 */
private val CONTENT_MAX_WIDTH = 720.dp

/**
 * Sync gets a pane of its own from the large width class (1200.dp) up — a maximized desktop or
 * web window, an unfolded foldable in landscape — where the day sidebar, the bits and a
 * [SyncPresentation.Pane] still leave the bits more room than the sidebar takes. Below that, and
 * whenever the window is too short for the sidebar as well, sync stays a sheet over the bits.
 */
@Composable
private fun currentSyncPresentation(): SyncPresentation {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isWideEnough =
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)
    val isTallEnough =
        windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    return if (isWideEnough && isTallEnough) SyncPresentation.Pane else SyncPresentation.Sheet
}

/**
 * How many lines the new-bit field may grow to before it starts scrolling instead. Every line it
 * takes is one the bits above it lose, so the budget follows the height that is actually left —
 * the window minus whatever the keyboard has taken, which is what the bits and the composer really
 * have to share:
 *
 * - under the medium breakpoint (a phone in landscape, a small foldable cover, a small phone with
 *   the keyboard up) the field keeps the single line it has always had;
 * - up to the expanded breakpoint (a phone held upright, a small desktop window) it can spare a
 *   second line. Kept deliberately short of what fits: what is left over is at its scarcest with
 *   the picker expanded, which this cannot see (the picker owns that state), so the band that a
 *   phone with an open keyboard falls into is sized for the composer at its tallest;
 * - above it — a maximized desktop or web window, a tablet, an unfolded foldable — more.
 *
 * The window's own size class is deliberately not used: it is measured edge to edge, so it reads
 * the same with the keyboard covering half the screen as without, which is the one moment the room
 * is scarce. The breakpoints behind it are still the right places to change behaviour, so they are
 * applied to the remaining height instead.
 *
 * A bit is usually one short line, so most of the time none of this is visible; it only decides how
 * much of a long one can be read back before submitting it.
 */
@Composable
private fun currentComposerMaxLines(): Int {
    val density = LocalDensity.current
    val imeHeight = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val availableHeight = LocalWindowInfo.current.containerDpSize.height - imeHeight
    return when {
        availableHeight < WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND.dp -> 1
        availableHeight < WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND.dp -> 2
        else -> 5
    }
}

@Composable
private fun currentBitsLayout(): BitsLayout {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return when {
        // Compact width (a phone held upright) has room for nothing but the bits and the composer.
        !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            BitsLayout.Stacked
        // Wide and tall — desktop and web windows, tablets, unfolded foldables.
        windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
            BitsLayout.DaySidebar
        // Wide but short, and a keyboard is actually taking up the bottom of it. Being short is
        // not enough on its own: a phone in landscape with a hardware keyboard has the same room
        // as any other wide window, and moving the composer aside there only looks odd.
        WindowInsets.ime.getBottom(LocalDensity.current) > 0 -> BitsLayout.SideComposer
        else -> BitsLayout.DaySidebar
    }
}
