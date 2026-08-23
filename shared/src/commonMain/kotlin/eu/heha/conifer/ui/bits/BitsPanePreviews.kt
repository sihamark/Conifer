package eu.heha.conifer.ui.bits

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.AndroidUiModes
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.heha.conifer.PermissionRationale
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.sync.SyncConnectionState
import eu.heha.conifer.sync.SyncDebugInfo
import eu.heha.conifer.sync.SyncStats
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.SyncPresentation
import eu.heha.conifer.ui.SyncUiState
import eu.heha.conifer.ui.theme.ConiferTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** Editing a bit, with the notification permission prompt and a custom time selection showing. */
@PreviewLightDark
@Composable
private fun BitsPaneEditingWithPermissionPromptPreview() {
    ConiferTheme {
        BitsPane(
            state = BitsPaneState(
                newBitText = "This is a new bit",
                permissionRationale = PermissionRationale(
                    "Add Bits from anywhere.",
                    "Allow notifications and reply to the Conifer conversation to capture a bit without opening the app."
                ),
                editingBitId = "1",
                composerTime = LocalTime(1, 0, 0),
                bitsByDate = listOf(
                    DatedBits(
                        date = LocalDate(2024, 6, 1),
                        bits = listOf(
                            Bit(id = "1", text = "First bit"),
                            Bit(text = "Second bit"),
                            Bit(text = (0..10).joinToString { "This is a new bit" })
                        )
                    )
                )
            )
        )
    }
}

/** A normal day-to-day view: several days, several bits, nothing being edited. */
@PreviewLightDark
@Composable
private fun BitsPaneTypicalPreview() {
    ConiferTheme {
        BitsPane(
            state = BitsPaneState(
                bitsByDate = listOf(
                    DatedBits(
                        date = LocalDate(2024, 5, 31),
                        bits = listOf(
                            Bit(
                                text = "Finished the quarterly report",
                                date = LocalDateTime(2024, 5, 31, 9, 15)
                            ),
                            Bit(
                                text = "Lunch with the team",
                                date = LocalDateTime(2024, 5, 31, 12, 30)
                            )
                        )
                    ),
                    DatedBits(
                        date = LocalDate(2024, 6, 1),
                        bits = listOf(
                            Bit(
                                text = "Started reading a new book",
                                date = LocalDateTime(2024, 6, 1, 8, 0)
                            ),
                            Bit(
                                text = "Fixed the flaky CI test",
                                date = LocalDateTime(2024, 6, 1, 14, 45)
                            ),
                            Bit(text = "Evening walk", date = LocalDateTime(2024, 6, 1, 19, 30))
                        )
                    )
                )
            )
        )
    }
}

/** No bits exist anywhere yet. */
@PreviewLightDark
@Composable
private fun BitsPaneEmptyPreview() {
    ConiferTheme {
        BitsPane(state = BitsPaneState())
    }
}

/** The layout wide windows get: the day sidebar beside the bits, no day strip in the composer. */
@Preview(
    uiMode = AndroidUiModes.UI_MODE_NIGHT_YES,
    group = "BitsPaneTwoPanePreview",
    widthDp = 960,
    heightDp = 560
)
@Preview(
    uiMode = AndroidUiModes.UI_MODE_NIGHT_NO,
    group = "BitsPaneTwoPanePreview",
    widthDp = 960,
    heightDp = 560
)
@Composable
private fun BitsPaneTwoPanePreview() {
    ConiferTheme {
        BitsPane(
            layout = BitsLayout.DaySidebar,
            state = BitsPaneState(
                today = LocalDate(2024, 6, 1),
                bitsByDate = listOf(
                    DatedBits(
                        date = LocalDate(2024, 5, 31),
                        bits = listOf(
                            Bit(
                                text = "Finished the quarterly report",
                                date = LocalDateTime(2024, 5, 31, 9, 15)
                            ),
                            Bit(
                                text = "Lunch with the team",
                                date = LocalDateTime(2024, 5, 31, 12, 30)
                            )
                        )
                    ),
                    DatedBits(
                        date = LocalDate(2024, 6, 1),
                        bits = listOf(
                            Bit(
                                text = "Started reading a new book",
                                date = LocalDateTime(2024, 6, 1, 8, 0)
                            ),
                            Bit(
                                text = "Fixed the flaky CI test",
                                date = LocalDateTime(2024, 6, 1, 14, 45)
                            )
                        )
                    )
                )
            )
        )
    }
}

/**
 * The widest windows: the same two panes with sync opened as a third one on the right, instead of
 * the sheet smaller windows put over the bits. Sized like a maximized desktop window.
 */
@Preview(
    uiMode = AndroidUiModes.UI_MODE_NIGHT_YES,
    group = "BitsPaneThreePanePreview",
    widthDp = 1280,
    heightDp = 800
)
@Preview(
    uiMode = AndroidUiModes.UI_MODE_NIGHT_NO,
    group = "BitsPaneThreePanePreview",
    widthDp = 1280,
    heightDp = 800
)
@Composable
private fun BitsPaneThreePanePreview() {
    ConiferTheme {
        BitsPane(
            layout = BitsLayout.DaySidebar,
            syncPresentation = SyncPresentation.Pane,
            state = BitsPaneState(
                today = LocalDate(2024, 6, 1),
                bitsByDate = listOf(
                    DatedBits(
                        date = LocalDate(2024, 6, 1),
                        bits = listOf(
                            Bit(
                                text = "Started reading a new book",
                                date = LocalDateTime(2024, 6, 1, 8, 0)
                            ),
                            Bit(
                                text = "Fixed the flaky CI test",
                                date = LocalDateTime(2024, 6, 1, 14, 45)
                            )
                        )
                    )
                )
            ),
            syncState = SyncUiState(
                isSyncOpen = true,
                connection = SyncConnectionState.Connected(
                    server = "https://cloud.example.org",
                    username = "alice",
                    isSyncing = false,
                    lastSyncAt = Instant.fromEpochMilliseconds(1_753_000_000_000)
                ),
                debugInfo = SyncDebugInfo(
                    deviceId = "a1b2c3d4-e5f6-7890-aaaa-bbbbccccdddd",
                    appRoot = "Conifer",
                    lastSyncAt = Instant.fromEpochMilliseconds(1_753_000_000_000),
                    rootEtag = "\"abcd1234ef56\"",
                    lastGcAt = null,
                    lastError = null,
                    lastStats = SyncStats(pushed = 3, pulled = 5, merged = 1)
                )
            )
        )
    }
}

/**
 * The layout a phone in landscape gets: the composer beside the bits rather than under them, so
 * the keyboard does not leave the list without a single visible bit. Sized like the window that is
 * left over on a 411 x 914dp phone turned sideways with the keyboard open.
 */
@Preview(
    uiMode = AndroidUiModes.UI_MODE_NIGHT_YES,
    group = "BitsPaneSideComposerPreview",
    widthDp = 914,
    heightDp = 122
)
@Preview(
    uiMode = AndroidUiModes.UI_MODE_NIGHT_NO,
    group = "BitsPaneSideComposerPreview",
    widthDp = 914,
    heightDp = 122
)
@Composable
private fun BitsPaneSideComposerPreview() {
    ConiferTheme {
        BitsPane(
            layout = BitsLayout.SideComposer,
            state = BitsPaneState(
                today = LocalDate(2024, 6, 1),
                bitsByDate = listOf(
                    DatedBits(
                        date = LocalDate(2024, 6, 1),
                        bits = listOf(
                            Bit(
                                text = "Started reading a new book",
                                date = LocalDateTime(2024, 6, 1, 8, 0)
                            ),
                            Bit(
                                text = "Fixed the flaky CI test",
                                date = LocalDateTime(2024, 6, 1, 14, 45)
                            ),
                            Bit(
                                text = "Evening walk",
                                date = LocalDateTime(2024, 6, 1, 19, 30)
                            )
                        )
                    )
                )
            )
        )
    }
}

/** Bits exist, but the selected day (via the date chip/day picker) has none of its own. */
@PreviewLightDark
@Composable
private fun BitsPaneEmptyFilteredPreview() {
    ConiferTheme {
        BitsPane(
            state = BitsPaneState(
                filterDate = LocalDate(2024, 6, 2),
                composerDate = LocalDate(2024, 6, 2),
                bitsByDate = listOf(
                    DatedBits(
                        date = LocalDate(2024, 6, 1),
                        bits = listOf(Bit(text = "A bit from another day"))
                    )
                )
            )
        )
    }
}

/**
 * The list of shortcuts over the screen. Drawn on its own rather than through [BitsPane], whose
 * overlay opens on a key press a preview has no way to send.
 */
@PreviewLightDark
@Composable
private fun ShortcutsOverlayPreview() {
    ConiferTheme {
        ShortcutsOverlay(onDismiss = {})
    }
}
