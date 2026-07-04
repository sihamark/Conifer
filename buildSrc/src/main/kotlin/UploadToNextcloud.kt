import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Uploads the contents of [sourceFolder] (recursively) to a Nextcloud instance over WebDAV.
 *
 * Credentials and the target location are supplied via a `nextcloud.properties` file (see
 * `nextcloud.properties.example`) and wired into the task's properties, so no secrets live in the
 * build scripts. Using a Nextcloud *app password* rather than the account password is strongly
 * recommended.
 */
abstract class UploadToNextcloud : DefaultTask() {

    /** Base URL of the Nextcloud server, e.g. `https://cloud.example.com`. */
    @get:Input
    abstract val serverUrl: Property<String>

    @get:Input
    abstract val username: Property<String>

    /** App password (preferred) or account password. Kept out of the up-to-date inputs. */
    @get:Internal
    abstract val password: Property<String>

    /** Target folder on Nextcloud (relative to the user's files root), e.g. `Conifer/1.0.0`. */
    @get:Input
    abstract val remoteFolder: Property<String>

    /** Local folder whose contents are uploaded, mirroring its directory structure. */
    @get:InputDirectory
    abstract val sourceFolder: DirectoryProperty

    init {
        // An upload always has to run; there is no meaningful local output to compare against.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun action() {
        val source = sourceFolder.get().asFile
        val files = source.walkTopDown()
            // Skip hidden files (e.g. .DS_Store) and anything inside a hidden directory.
            .onEnter { dir -> !dir.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") }
            .toList()
        if (files.isEmpty()) {
            logger.lifecycle("No artifacts found in ${source.absolutePath}; nothing to upload.")
            return
        }

        val client = HttpClient.newHttpClient()
        val filesRoot = "${serverUrl.get().trimEnd('/')}/remote.php/dav/files/${username.get()}"
        val basePath = remoteFolder.get().trim('/')

        // Nextcloud requires every parent collection to exist before a PUT, so create the target
        // folder and any subfolders the artifacts live in first.
        val remoteDirs = buildSet {
            addAll(basePath.split('/').filter { it.isNotEmpty() }
                .runningReduce { acc, s -> "$acc/$s" })
            files.forEach { file ->
                val relativeDir = file.parentFile.relativeTo(source).invariantSeparatorsPath
                if (relativeDir.isNotEmpty() && relativeDir != ".") {
                    "$basePath/$relativeDir".split('/')
                        .filter { it.isNotEmpty() }
                        .runningReduce { acc, s -> "$acc/$s" }
                        .forEach { add(it) }
                }
            }
        }.sortedBy { it.count { c -> c == '/' } }

        remoteDirs.forEach { dir -> createCollection(client, "$filesRoot/${dir.encodePath()}") }

        files.forEach { file ->
            // Android browsers refuse to download raw .apk files, so wrap them in a .zip that the
            // user can download and extract on-device.
            val upload =
                if (file.extension.equals("apk", ignoreCase = true)) zipFile(file) else file
            val relative = file.relativeTo(source).invariantSeparatorsPath.let { rel ->
                if (upload === file) rel else "$rel.zip"
            }
            val target = "$filesRoot/${"$basePath/$relative".encodePath()}"
            uploadFile(client, target, upload)
        }

        logger.lifecycle("Uploaded ${files.size} artifact(s) to ${serverUrl.get()} at /$basePath")
    }

    /** Wraps [file] in a `<name>.zip` inside the task's temp dir and returns the archive. */
    private fun zipFile(file: File): File {
        val zip = File(temporaryDir, "${file.name}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry(file.name))
            file.inputStream().buffered().use { it.copyTo(out) }
            out.closeEntry()
        }
        return zip
    }

    private fun createCollection(client: HttpClient, url: String) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", authHeader())
            .method("MKCOL", BodyPublishers.noBody())
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        // 201 = created, 405 = already exists. Anything else is a real failure.
        if (response.statusCode() != 201 && response.statusCode() != 405) {
            throw GradleException("Failed to create Nextcloud folder $url (HTTP ${response.statusCode()})")
        }
    }

    private fun uploadFile(client: HttpClient, url: String, file: File) {
        logger.lifecycle("Uploading ${file.name} …")
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", authHeader())
            .PUT(BodyPublishers.ofFile(file.toPath()))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        // 201 = created, 204 = overwritten existing file.
        if (response.statusCode() != 201 && response.statusCode() != 204) {
            throw GradleException(
                "Failed to upload ${file.name} to $url (HTTP ${response.statusCode()}): ${response.body()}"
            )
        }
    }

    private fun authHeader(): String {
        val credentials = "${username.get()}:${password.get()}"
        return "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray())}"
    }

    /** Percent-encodes each path segment while keeping the `/` separators intact. */
    private fun String.encodePath(): String =
        split('/').joinToString("/") { segment ->
            URI(null, null, segment, null).rawPath
        }
}