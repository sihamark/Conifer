package eu.heha.conifer.ui.bits

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the time slider's thumb sits, which the time picker can now put between two ticks. */
class TimeSliderPositionTest {

    @Test
    fun aTimeOnTheGridSitsOnItsSlot() {
        assertEquals(0f, LocalTime(0, 0).sliderPosition)
        assertEquals(48f, LocalTime(12, 0).sliderPosition)
        assertEquals(95f, LocalTime(23, 45).sliderPosition)
    }

    @Test
    fun aTimeBetweenTwoSlotsSitsBetweenThem() {
        // What the picker is there for: 12:07 is not a slot, and the thumb should not pretend it
        // is 12:00 while the chip beside it says otherwise.
        assertEquals(48.4f, LocalTime(12, 6).sliderPosition)
        assertEquals(48.8f, LocalTime(12, 12).sliderPosition)
    }

    @Test
    fun secondsAreDroppedRatherThanNudgingTheThumb() {
        assertEquals(LocalTime(12, 7).sliderPosition, LocalTime(12, 7, 30).sliderPosition)
    }

    @Test
    fun theLastQuarterHourStaysOnTheTrack() {
        // 23:59 is 95.93 slots, past the 95 the slider's range ends at - it belongs at the end of
        // the track, not off it.
        assertEquals(95f, LocalTime(23, 59).sliderPosition)
    }
}
