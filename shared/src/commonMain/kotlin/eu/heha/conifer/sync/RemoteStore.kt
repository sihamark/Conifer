package eu.heha.conifer.sync

/**
 * WebDAV transport abstraction the sync engine is written against (Nextcloud sync spec §4).
 * Keeping this as an interface (rather than calling [KtorWebDavStore] directly) leaves room for
 * a Nextcloud SSO transport on Android later (spec §10) without touching the sync algorithm.
 */
interface RemoteStore {

    /** PROPFIND Depth:0 → normalized ETag of the resource, `null` on 404. */
    suspend fun etag(path: String): String?

    /** PROPFIND Depth:1 → child entries (name, ETag, isDirectory). Throws [WebDavNotFoundException] on 404. */
    suspend fun list(path: String): List<Entry>

    suspend fun get(path: String): ByteArray

    /**
     * PUT. [ifNoneMatchAll] `true` → create only (throws [WebDavConflictException] if it already
     * exists). [ifMatch] set → overwrite only if the ETag matches (throws [WebDavConflictException]
     * otherwise). Both unset → unconditional overwrite.
     *
     * @return the new ETag (from the response header; falls back to a PROPFIND Depth:0 if missing).
     */
    suspend fun put(
        path: String,
        body: ByteArray,
        ifMatch: String? = null,
        ifNoneMatchAll: Boolean = false,
    ): String

    /** MKCOL; tolerates the folder already existing (405). */
    suspend fun mkdirs(path: String)

    /**
     * Bulk upload of multiple small files via `remote.php/dav/bulk` (multipart/related). For new
     * files only (no conditional semantics) — falls back to sequential [put] if the server
     * doesn't support the bulk endpoint.
     *
     * @return the new ETag of each file, in the same order as [files].
     */
    suspend fun bulkPut(files: List<BulkFile>): List<String>

    data class Entry(val name: String, val etag: String, val isDirectory: Boolean)

    data class BulkFile(val path: String, val body: ByteArray, val mtime: Long)
}

/** [RemoteStore.list] found no resource at the requested path. */
class WebDavNotFoundException(val path: String) : Exception("Not found: $path")

/**
 * [RemoteStore.put] refused the write: the resource's current ETag didn't match ifMatch, or
 * [RemoteStore.put] was called with `ifNoneMatchAll = true` but the resource already exists
 * (WebDAV status 412).
 */
class WebDavConflictException(val path: String) : Exception("Conflict: $path")

/** Any other non-2xx WebDAV response. */
class WebDavException(val path: String, val status: Int) :
    Exception("WebDAV request to $path failed with status $status")
