package eu.heha.conifer.sync

/**
 * In-memory [RemoteStore] standing in for a Nextcloud server in tests. Deliberately mimics real
 * WebDAV semantics that the sync engine actually depends on, rather than just being permissive:
 * [mkdirs] and [put] both require their parent collection to already exist (WebDAV 409), and a
 * directory's ETag changes whenever anything changes anywhere underneath it (mirroring real
 * folder ETags), so a test that regresses [SyncEngine]'s bucket bootstrapping or bucket-skip
 * logic fails loudly instead of silently passing against an unrealistically lenient fake.
 */
class FakeRemoteStore : RemoteStore {

    private class StoredFile(var body: ByteArray, var etag: String)

    private val files = mutableMapOf<String, StoredFile>()
    private val directories = mutableSetOf("")
    private val directoryVersions = mutableMapOf<String, Int>()
    private var nextEtagId = 0

    override suspend fun etag(path: String): String? {
        val normalized = normalize(path)
        files[normalized]?.let { return it.etag }
        return if (normalized in directories) directoryEtag(normalized) else null
    }

    override suspend fun list(path: String): List<RemoteStore.Entry> {
        val normalized = normalize(path)
        if (normalized !in directories) throw WebDavNotFoundException(path)
        val childDirectories = directories.filter { it != normalized && parentOf(it) == normalized }
        val childFiles = files.keys.filter { parentOf(it) == normalized }
        return childDirectories.map { entryName ->
            RemoteStore.Entry(
                name = entryName.substringAfterLast('/'),
                etag = directoryEtag(entryName),
                isDirectory = true
            )
        } + childFiles.map { entryName ->
            RemoteStore.Entry(
                name = entryName.substringAfterLast('/'),
                etag = files.getValue(entryName).etag,
                isDirectory = false
            )
        }
    }

    override suspend fun get(path: String): ByteArray =
        files[normalize(path)]?.body?.copyOf() ?: throw WebDavNotFoundException(path)

    override suspend fun put(
        path: String,
        body: ByteArray,
        ifMatch: String?,
        ifNoneMatchAll: Boolean
    ): String {
        val normalized = normalize(path)
        requireParentExists(normalized, path)
        val existing = files[normalized]
        if (ifNoneMatchAll && existing != null) throw WebDavConflictException(path)
        if (ifMatch != null && existing?.etag != ifMatch) throw WebDavConflictException(path)
        val etag = freshEtag()
        files[normalized] = StoredFile(body.copyOf(), etag)
        bumpAncestors(normalized)
        return etag
    }

    override suspend fun mkdirs(path: String) {
        val normalized = normalize(path)
        if (normalized in directories) return
        requireParentExists(normalized, path)
        directories.add(normalized)
        bumpAncestors(normalized)
    }

    override suspend fun bulkPut(files: List<RemoteStore.BulkFile>): List<String> =
        files.map { file -> put(file.path, file.body, ifNoneMatchAll = true) }

    private fun requireParentExists(normalized: String, originalPath: String) {
        if (parentOf(normalized) !in directories) throw WebDavException(originalPath, 409)
    }

    private fun freshEtag(): String = "etag-${nextEtagId++}"

    private fun directoryEtag(path: String): String = "dir-${directoryVersions[path] ?: 0}"

    private fun bumpAncestors(path: String) {
        var current = parentOf(path)
        while (true) {
            directoryVersions[current] = (directoryVersions[current] ?: 0) + 1
            if (current.isEmpty()) break
            current = parentOf(current)
        }
    }

    private fun normalize(path: String) = path.trim('/')

    private fun parentOf(path: String): String {
        val index = path.lastIndexOf('/')
        return if (index < 0) "" else path.substring(0, index)
    }
}
