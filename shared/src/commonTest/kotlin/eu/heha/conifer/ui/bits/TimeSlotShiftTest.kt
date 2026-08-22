package eu.heha.conifer.ui.bits

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ↑/↓ time nudge behind [NewBitText]'s key handling. */
class TimeSlotShiftTest {

    @Test
    fun aTimeOnTheGridMovesOneQuarterHour() {
        assertEquals(LocalTime(12, 15), LocalTime(12, 0).shiftedByTimeSlots(1))
        assertEquals(LocalTime(11, 45), LocalTime(12, 0).shiftedByTimeSlots(-1))
    }

    @Test
    fun aTimeOffTheGridSnapsOntoItInTheDirectionPressed() {
        // The clock's "now" is almost never on a quarter hour, and the first nudge should not
        // jump past the slot the user is heading for.
        assertEquals(LocalTime(12, 15), LocalTime(12, 7).shiftedByTimeSlots(1))
        assertEquals(LocalTime(12, 0), LocalTime(12, 7).shiftedByTimeSlots(-1))
        assertEquals(LocalTime(12, 15), LocalTime(12, 1).shiftedByTimeSlots(1))
        assertEquals(LocalTime(12, 0), LocalTime(12, 14).shiftedByTimeSlots(-1))
    }

    @Test
    fun secondsAreDroppedOntoTheGridJustTheSame() {
        assertEquals(LocalTime(9, 45), LocalTime(9, 33, 21).shiftedByTimeSlots(1))
    }

    @Test
    fun theEndsOfTheDayClampInsteadOfRollingOver() {
        // Rolling over would silently move the bit to another day, which the arrow keys must not
        // be able to do - the day is a separate choice.
        assertEquals(LocalTime(0, 0), LocalTime(0, 0).shiftedByTimeSlots(-1))
        assertEquals(LocalTime(23, 45), LocalTime(23, 45).shiftedByTimeSlots(1))
        assertEquals(LocalTime(23, 45), LocalTime(23, 50).shiftedByTimeSlots(1))
        assertEquals(LocalTime(0, 0), LocalTime(0, 10).shiftedByTimeSlots(-1))
    }

    @Test
    fun theGridIsTheSliderSOwn() {
        // The nudge and the slider share one slot mapping, so walking the grid a nudge at a time
        // has to reproduce the slider's range exactly: 96 slots of 15 minutes, 00:00 to 23:45.
        // Anything else means the thumb and the arrow keys have drifted apart.
        var time = LocalTime(0, 0)
        var slots = 1
        while (time != LocalTime(23, 45)) {
            val next = time.shiftedByTimeSlots(1)
            val minutes = (next.hour * 60 + next.minute) - (time.hour * 60 + time.minute)
            assertEquals(15, minutes, "step $slots landed $minutes minutes on from $time")
            time = next
            slots++
            if (slots > 200) break // never reached; keeps a broken step from looping forever
        }
        assertEquals(96, slots)
    }

    @Test
    fun repeatedNudgesWalkTheGridInStep() {
        var time = LocalTime(8, 3)
        repeat(4) { time = time.shiftedByTimeSlots(1) }
        assertEquals(LocalTime(9, 0), time)
        repeat(4) { time = time.shiftedByTimeSlots(-1) }
        assertEquals(LocalTime(8, 0), time)
    }
}
