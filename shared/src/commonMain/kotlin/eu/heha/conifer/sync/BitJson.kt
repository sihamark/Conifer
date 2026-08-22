package eu.heha.conifer.sync

import eu.heha.conifer.model.database.Bit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Wire format of a bit's post JSON file (Nextcloud sync spec §3.1), kotlinx.serialization,
 * UTF-8, no pretty printing.
 *
 * The spec's generic schema splits a post's timing into `createdAt`/`day`/`displayDate` (Unix
 * timestamps plus a separately stored display-day string) because it targets a hypothetical app
 * that timestamps everything as an [Instant]. This app deliberately doesn't: [Bit.date] is a
 * zoneless [LocalDateTime] (its date part *is* the display day, its time part the order within
 * that day) specifically so a bit's displayed day/time never drifts when the device's time zone
 * changes. Reusing [Instant] here for [date] would reintroduce exactly that drift the moment two
 * devices sync across time zones, so the wire format mirrors the local entity instead of the
 * spec's literal field list: [date] is serialized as a zoneless ISO-8601 string, and only the
 * two audit timestamps ([createdAt], [modifiedAt] - never shown in the UI) are plain epoch
 * milliseconds.
 */
@Serializable
data class BitJson(
    val schema: Int = CURRENT_SCHEMA,
    val id: String,
    val text: String,
    /** Epoch milliseconds, UTC. Immutable - determines [Bit.bucket] (spec invariant I1). */
    val createdAt: Long,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val date: LocalDateTime,
    /** Epoch milliseconds. Basis for last-write-wins (spec §6). */
    val modifiedAt: Long,
    val modifiedBy: String,
    val deleted: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

fun Bit.toJson(): BitJson = BitJson(
    id = id,
    text = text,
    createdAt = createdAt.toEpochMilliseconds(),
    date = date,
    modifiedAt = modifiedAt.toEpochMilliseconds(),
    modifiedBy = modifiedBy,
    deleted = deleted,
)

/**
 * Converts a parsed remote post back into a [Bit], stamping [rawJson] as its preserved
 * [Bit.payload] so a future app version's unknown fields survive even though this version only
 * round-trips the fields it understands. [Bit.remoteEtag] is left `null`; the caller (the merge
 * step, which knows the file's actual ETag) stamps it.
 *
 * Returns `null` when [BitJson.schema] is newer than [BitJson.CURRENT_SCHEMA] (spec §9: "do not
 * interpret, do not overwrite" - the caller must leave the existing local/remote state alone).
 */
fun BitJson.toBitOrNull(rawJson: String): Bit? {
    if (schema > BitJson.CURRENT_SCHEMA) return null
    return Bit(
        id = id,
        text = text,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        date = date,
        modifiedAt = Instant.fromEpochMilliseconds(modifiedAt),
        modifiedBy = modifiedBy,
        deleted = deleted,
        payload = rawJson,
        dirty = false,
        remoteEtag = null,
    )
}
