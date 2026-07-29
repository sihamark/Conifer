package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.app_name
import conifer.shared.generated.resources.bits_label_counter
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
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
) {
    Scaffold(contentWindowInsets = WindowInsets()) { innerPadding ->
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
                        selectedDate = state.selectedDate,
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
                focusRequester = focusRequester,
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
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // The state always holds all days; a selected date only filters what the list (and
    // the counter) shows, so the day chips keep their indicators while filtering.
    val visibleBitsByDate = state.selectedDate?.let { selected ->
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
                focusRequester = focusRequester
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
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        BitsList(
            state = state,
            visibleBitsByDate = visibleBitsByDate,
            actions = actions,
            isTopBarVisible = isTopBarVisible,
            // Only the list is inset; the top bar floating above it keeps spanning the whole pane.
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = paneInset)
        )
        val bitsCount = visibleBitsByDate.sumOf { it.bits.size }
        Topbar(bitsCount, isTopBarVisible, syncState, syncActions, syncPresentation)
    }
}

/** The date/time picker and the text field, on their own surface. */
@Composable
private fun Composer(
    state: BitsPaneState,
    actions: BitsPaneActions,
    layout: BitsLayout,
    paneInset: Dp,
    focusRequester: FocusRequester,
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
                .padding(horizontal = paneInset)
                // Collapsed, the composer fits the height a landscape keyboard leaves exactly;
                // expanding the picker does not, so there it scrolls instead of squeezing the
                // text field. Nothing to scroll — and so nothing to notice — while collapsed.
                .then(if (isShort) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        ) {
            DateTimeSelector(
                bitsByDate = state.bitsByDate,
                selectedDate = state.selectedDate,
                selectedTime = state.selectedTime,
                currentDate = state.today,
                currentTime = state.currentTime,
                isEditing = state.editingBitId != null,
                // Only the sidebar layout takes the day off the picker's hands.
                isDaySelectionVisible = layout != BitsLayout.DaySidebar,
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
                bottomPadding = if (isShort) 8.dp else 16.dp
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
    syncPresentation: SyncPresentation
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
