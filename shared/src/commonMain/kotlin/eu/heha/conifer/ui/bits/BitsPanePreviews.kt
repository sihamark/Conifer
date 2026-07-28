package eu.heha.conifer.ui.bits

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.AndroidUiModes
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.heha.conifer.PermissionRationale
import eu.heha.conifer.model.database.Bit
import eu.heha.conifer.ui.DatedBits
import eu.heha.conifer.ui.theme.ConiferTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

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
                selectedTime = LocalTime(1, 0, 0),
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
            isTwoPane = true,
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

/** Bits exist, but the selected day (via the date chip/day picker) has none of its own. */
@PreviewLightDark
@Composable
private fun BitsPaneEmptyFilteredPreview() {
    ConiferTheme {
        BitsPane(
            state = BitsPaneState(
                selectedDate = LocalDate(2024, 6, 2),
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
