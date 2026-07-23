package eu.heha.conifer.sync

import kotlinx.coroutines.test.runTest
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [KtorWebDavStore] against a real, containerized Nextcloud (Nextcloud sync spec §10
 * stage ②: "KtorWebDavStore against a test Nextcloud (Docker)"). Scoped to the transport itself —
 * full roundtrip/conflict/new-device scenarios return once the sync engine (stage ③) exists.
 *
 * Requires a reachable Docker daemon. If none is reachable, every test logs a notice and returns
 * early instead of failing, so a plain `./gradlew :shared:jvmTest` still passes on machines
 * without Docker running.
 */
class KtorWebDavStoreIntegrationTest {

    @Test
    fun mkdirsCreatesAFolderAndToleratesBeingCalledAgain() = runTest {
        val store = store() ?: return@runTest
        val folder = uniqueFolder("mkdirs")

        store.mkdirs(folder)
        store.mkdirs(folder) // must not throw: the folder already existing is tolerated (405)

        assertTrue(store.list(folder).isEmpty())
    }

    @Test
    fun putWithIfNoneMatchAllCreatesAFileWhoseEtagCanBeRead() = runTest {
        val store = store() ?: return@runTest
        val folder = uniqueFolder("put-create")
        store.mkdirs(folder)
        val path = "$folder/note.json"

        val etag = store.put(path, "hello".encodeToByteArray(), ifNoneMatchAll = true)

        assertEquals(etag, store.etag(path))
    }

    @Test
    fun putWithAStaleIfMatchThrowsConflict() = runTest {
        val store = store() ?: return@runTest
        val folder = uniqueFolder("put-conflict")
        store.mkdirs(folder)
        val path = "$folder/note.json"
        store.put(path, "v1".encodeToByteArray(), ifNoneMatchAll = true)

        assertFailsWith<WebDavConflictException> {
            store.put(path, "v2".encodeToByteArray(), ifMatch = "not-the-real-etag")
        }
    }

    @Test
    fun putWithIfNoneMatchAllOnAnExistingFileThrowsConflict() = runTest {
        val store = store() ?: return@runTest
        val folder = uniqueFolder("put-exists")
        store.mkdirs(folder)
        val path = "$folder/note.json"
        store.put(path, "v1".encodeToByteArray(), ifNoneMatchAll = true)

        assertFailsWith<WebDavConflictException> {
            store.put(path, "v2".encodeToByteArray(), ifNoneMatchAll = true)
        }
    }

    @Test
    fun getRoundTripsTheBytesWrittenByPut() = runTest {
        val store = store() ?: return@runTest
        val folder = uniqueFolder("get")
        store.mkdirs(folder)
        val path = "$folder/note.json"
        val body = "some post content éü".encodeToByteArray()
        store.put(path, body, ifNoneMatchAll = true)

        assertContentEquals(body, store.get(path))
    }

    @Test
    fun listReturnsTheEntriesOfAPopulatedFolder() = runTest {
        val store = store() ?: return@runTest
        val folder = uniqueFolder("list")
        store.mkdirs(folder)
        store.put("$folder/a.json", "a".encodeToByteArray(), ifNoneMatchAll = true)
        store.put("$folder/b.json", "b".encodeToByteArray(), ifNoneMatchAll = true)
        store.mkdirs("$folder/sub")

        val entries = store.list(folder)

        assertEquals(setOf("a.json", "b.json", "sub"), entries.map { it.name }.toSet())
        assertTrue(entries.single { it.name == "sub" }.isDirectory)
        assertTrue(entries.none { it.name == "a.json" && it.isDirectory })
    }

    @Test
    fun bulkPutUploadsSeveralFilesThatAreThenIndividuallyRetrievable() = runTest {
        val store = store() ?: return@runTest
        val folder = uniqueFolder("bulk")
        store.mkdirs(folder)
        val files = listOf(
            RemoteStore.BulkFile(
                "$folder/1.json",
                "one".encodeToByteArray(),
                mtime = 1_752_000_000
            ),
            RemoteStore.BulkFile(
                "$folder/2.json",
                "two".encodeToByteArray(),
                mtime = 1_752_000_000
            ),
        )

        val etags = store.bulkPut(files)

        assertEquals(2, etags.size)
        assertEquals("one", store.get("$folder/1.json").decodeToString())
        assertEquals("two", store.get("$folder/2.json").decodeToString())
    }

    @Test
    fun etagOfAMissingPathIsNull() = runTest {
        val store = store() ?: return@runTest

        assertNull(store.etag("${uniqueFolder("missing")}/nope.json"))
    }

    @Test
    fun listOfAMissingPathThrowsNotFound() = runTest {
        val store = store() ?: return@runTest

        assertFailsWith<WebDavNotFoundException> { store.list(uniqueFolder("missing")) }
    }

    private fun uniqueFolder(label: String) = "it-$label-${kotlin.uuid.Uuid.random()}"

    private fun store(): KtorWebDavStore? {
        val container = Nextcloud.container ?: run {
            println("${this::class.simpleName}: no Docker daemon reachable, skipping.")
            return null
        }
        return KtorWebDavStore(
            serverUrl = "http://${container.host}:${container.getMappedPort(80)}",
            username = Nextcloud.ADMIN_USER,
            password = Nextcloud.ADMIN_PASSWORD,
        )
    }

    // A tiny concrete subclass so Testcontainers' fluent with*()/waitingFor() builders resolve to
    // this type instead of the raw self-referencing GenericContainer<SELF> type parameter.
    private class NextcloudContainer(image: DockerImageName) :
        GenericContainer<NextcloudContainer>(image)

    private object Nextcloud {
        const val ADMIN_USER = "admin"
        const val ADMIN_PASSWORD = "conifer-test-admin"

        val container: NextcloudContainer? by lazy {
            runCatching {
                NextcloudContainer(DockerImageName.parse("nextcloud:29-apache"))
                    .withEnv("NEXTCLOUD_ADMIN_USER", ADMIN_USER)
                    .withEnv("NEXTCLOUD_ADMIN_PASSWORD", ADMIN_PASSWORD)
                    // Without an explicit database, the entrypoint leaves Nextcloud in its
                    // "finish setup in the browser" state (status.php still 200, but
                    // "installed":false) instead of running the unattended `occ
                    // maintenance:install` - SQLITE_DATABASE is what actually triggers it.
                    .withEnv("SQLITE_DATABASE", "conifer_sync_test")
                    .withExposedPorts(80)
                    .waitingFor(
                        Wait.forHttp("/status.php")
                            .forStatusCode(200)
                            .forResponsePredicate { it.contains("\"installed\":true") }
                            .withStartupTimeout(Duration.ofMinutes(3))
                    )
                    .apply { start() }
            }.onFailure {
                println(
                    "KtorWebDavStoreIntegrationTest: could not start the Nextcloud test " +
                            "container (${it.message}), skipping."
                )
            }.getOrNull()
        }
    }
}
