package eu.heha.conifer.sync

import eu.heha.conifer.model.database.Bit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MergePolicyTest {

    private val baseTime = Instant.fromEpochMilliseconds(1_752_408_000_000)

    private fun bit(
        modifiedAt: Instant = baseTime,
        modifiedBy: String = "device-a",
        dirty: Boolean = false,
        deleted: Boolean = false,
        text: String = "text"
    ) = Bit(
        id = "bit-1",
        text = text,
        createdAt = baseTime,
        modifiedAt = modifiedAt,
        modifiedBy = modifiedBy,
        dirty = dirty,
        deleted = deleted
    )

    @Test
    fun remoteWinsWhenBitIsUnknownLocally() {
        assertEquals(MergePolicy.Winner.REMOTE, MergePolicy.winner(local = null, remote = bit()))
    }

    @Test
    fun remoteWinsWhenLocalRowIsClean() {
        val local = bit(dirty = false, modifiedAt = baseTime + FIVE_MINUTES)
        val remote = bit(modifiedAt = baseTime)
        assertEquals(MergePolicy.Winner.REMOTE, MergePolicy.winner(local, remote))
    }

    @Test
    fun laterModificationWinsAConflict() {
        val local = bit(dirty = true, modifiedAt = baseTime)
        val remote = bit(modifiedAt = baseTime + FIVE_MINUTES)
        assertEquals(MergePolicy.Winner.REMOTE, MergePolicy.winner(local, remote))

        val newerLocal = bit(dirty = true, modifiedAt = baseTime + FIVE_MINUTES)
        val olderRemote = bit(modifiedAt = baseTime)
        assertEquals(MergePolicy.Winner.LOCAL, MergePolicy.winner(newerLocal, olderRemote))
    }

    @Test
    fun equalModificationTimeIsBrokenByTheGreaterDeviceId() {
        val local = bit(dirty = true, modifiedBy = "device-a")
        val remote = bit(modifiedBy = "device-b")
        assertEquals(MergePolicy.Winner.REMOTE, MergePolicy.winner(local, remote))
        assertEquals(
            MergePolicy.Winner.LOCAL,
            MergePolicy.winner(local.copy(modifiedBy = "device-c"), remote)
        )
    }

    @Test
    fun tombstonesParticipateAsEquals() {
        // a later edit beats an earlier delete
        val localEdit = bit(dirty = true, modifiedAt = baseTime + FIVE_MINUTES)
        val remoteDelete = bit(deleted = true, modifiedAt = baseTime)
        assertEquals(MergePolicy.Winner.LOCAL, MergePolicy.winner(localEdit, remoteDelete))

        // and a later delete beats an earlier edit
        val laterRemoteDelete = bit(deleted = true, modifiedAt = baseTime + FIVE_MINUTES * 2)
        assertEquals(MergePolicy.Winner.REMOTE, MergePolicy.winner(localEdit, laterRemoteDelete))
    }

    @Test
    fun mergedRemoteWinnerReplacesTheRowAndIsClean() {
        val local = bit(dirty = true, modifiedAt = baseTime, text = "local")
        val remote = bit(modifiedAt = baseTime + FIVE_MINUTES, text = "remote", dirty = true)

        val merged = MergePolicy.merged(local, remote, remoteEtag = "etag-2")

        assertEquals("remote", merged.text)
        assertEquals(false, merged.dirty)
        assertEquals("etag-2", merged.remoteEtag)
    }

    @Test
    fun mergedLocalWinnerStaysDirtyAndOnlyAdvancesTheEtag() {
        val local = bit(dirty = true, modifiedAt = baseTime + FIVE_MINUTES, text = "local")
        val remote = bit(modifiedAt = baseTime, text = "remote")

        val merged = MergePolicy.merged(local, remote, remoteEtag = "etag-2")

        assertEquals("local", merged.text)
        assertEquals(true, merged.dirty)
        assertEquals("etag-2", merged.remoteEtag)
    }
}

private val FIVE_MINUTES = 5.minutes
