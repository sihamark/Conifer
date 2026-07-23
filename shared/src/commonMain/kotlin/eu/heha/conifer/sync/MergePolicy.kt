package eu.heha.conifer.sync

import eu.heha.conifer.model.database.Bit

/**
 * Last-write-wins reconciliation of a pulled remote bit with the local row of the same id
 * (Nextcloud sync spec §6). Pure decision logic — the sync engine applies the result inside a
 * database transaction together with its bookkeeping updates.
 *
 * Tombstones participate as equals: a later edit beats an earlier delete and vice versa,
 * because [Bit.deleted] is merged like any other content.
 */
object MergePolicy {

    enum class Winner { REMOTE, LOCAL }

    fun winner(local: Bit?, remote: Bit): Winner = when {
        // not known locally → take the remote bit
        local == null -> Winner.REMOTE
        // no unpushed local change → the remote state is simply the newer one
        !local.dirty -> Winner.REMOTE
        // conflict: locally changed AND remotely changed
        remote.modifiedAt > local.modifiedAt -> Winner.REMOTE
        remote.modifiedAt < local.modifiedAt -> Winner.LOCAL
        // deterministic tiebreaker so all devices converge on the same winner; on equal device
        // ids the local row wins, which keeps it dirty and lets the next push settle the state
        remote.modifiedBy > local.modifiedBy -> Winner.REMOTE
        else -> Winner.LOCAL
    }

    /**
     * The row to store after pulling [remote] whose file currently has [remoteEtag].
     *
     * If the remote bit wins it replaces the local row and is clean. If the local row wins it
     * stays dirty and only records the new ETag, so the next push can overwrite the server
     * file conditionally (If-Match) without running into its own conflict again.
     */
    fun merged(local: Bit?, remote: Bit, remoteEtag: String): Bit =
        when (winner(local, remote)) {
            Winner.REMOTE -> remote.copy(dirty = false, remoteEtag = remoteEtag)
            Winner.LOCAL -> checkNotNull(local).copy(remoteEtag = remoteEtag)
        }
}
