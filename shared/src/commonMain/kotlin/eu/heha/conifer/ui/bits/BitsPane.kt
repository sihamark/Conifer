package eu.heha.conifer.ui.bits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import conifer.shared.generated.resources.Res
import conifer.shared.generated.resources.app_name
import conifer.shared.generated.resources.bits_label_counter
import eu.heha.conifer.ui.SyncPaneActions
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
    // From the mockup, decided by Material's window size classes: from the medium width class up
    // (desktop and web windows, tablets, unfolded foldables) the day list moves into a sidebar next
    // to the bits; compact windows stay single-pane and keep the day strip in the composer's picker.
    // Overridable so previews and tests can pick a layout instead of a window size.
    isTwoPane: Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
) {
    Scaffold(contentWindowInsets = WindowInsets()) { innerPadding ->
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(state.newBitText) {
            if (state.newBitText.isBlank()) focusRequester.requestFocus()
        }
        LaunchedEffect(state.editingBitId) {
            if (state.editingBitId != null) focusRequester.requestFocus()
        }
        // While typing, the IME already claims a large share of the screen, so the top bar is
        // hidden to leave as much room as possible for reading existing bits.
        val isTopBarVisible = WindowInsets.ime.getBottom(LocalDensity.current) == 0
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
            AnimatedVisibility(visible = isTwoPane) {
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
                isTwoPane = isTwoPane,
                isTopBarVisible = isTopBarVisible,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


/** The bits themselves: the list with the top bar floating over it, and the composer below. */
@Composable
private fun MainPane(
    state: BitsPaneState,
    actions: BitsPaneActions,
    syncState: SyncUiState,
    syncActions: SyncPaneActions,
    isTwoPane: Boolean,
    isTopBarVisible: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        // The state always holds all days; a selected date only filters what the list (and
        // the counter) shows, so the day chips keep their indicators while filtering.
        val visibleBitsByDate = state.selectedDate?.let { selected ->
            state.bitsByDate.filter { it.date == selected }
        } ?: state.bitsByDate
        // From the mockup: with the sidebar taking the left edge, the bits and the composer get a
        // little more room to breathe than the phone layout's 16.dp. Animated so it travels with
        // the sidebar instead of snapping while the sidebar is still sliding.
        val paneInset by animateDpAsState(
            targetValue = if (isTwoPane) 8.dp else 0.dp,
            label = "paneInset"
        )
        // The list and the top bar share a Box so the bar floats above the list instead of
        // pushing it down (and the bottom bits under the input) when it reappears. The list
        // may start underneath the bar, but its first item is the BeginningNote whose top
        // padding keeps the text clear of the bar.
        Box(Modifier.weight(1f)) {
            BitsList(
                state = state,
                visibleBitsByDate = visibleBitsByDate,
                actions = actions,
                // Only the list is inset; the top bar floating above it keeps spanning the whole
                // pane.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = paneInset)
            )
            val bitsCount = visibleBitsByDate.sumOf { it.bits.size }
            Topbar(bitsCount, isTopBarVisible, syncState, syncActions)
        }
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                // Padding the navigation bar and IME inside the colored column extends its
                // background behind the navigation bar instead of leaving a differently
                // colored strip below the input.
                .navigationBarsPadding()
                .imePadding()
        ) {
            // The inset goes on the content, not on the colored column, so the composer's surface
            // still reaches the sidebar's divider.
            Column(Modifier.padding(horizontal = paneInset)) {
                DateTimeSelector(
                    bitsByDate = state.bitsByDate,
                    selectedDate = state.selectedDate,
                    selectedTime = state.selectedTime,
                    currentDate = state.today,
                    currentTime = state.currentTime,
                    isEditing = state.editingBitId != null,
                    // In the two-pane layout the sidebar owns the day, so the picker is left with
                    // just the time slider.
                    isDaySelectionVisible = !isTwoPane,
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
                    focusRequester = focusRequester
                )
            }
        }
    }
}

@Composable
private fun Topbar(
    bitsCount: Int,
    isVisible: Boolean,
    syncState: SyncUiState,
    syncActions: SyncPaneActions
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
                SyncStatusIcon(syncState, syncActions, modifier = Modifier.padding(end = 8.dp))
            },
            // The column already handles the status bar inset.
            windowInsets = WindowInsets()
        )
    }
}
