import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermission
import javax.inject.Inject

abstract class CopyDesktopArtifacts @Inject constructor(
    @Inject private val layout: ProjectLayout,
    @Inject private val files: FileOperations
) : DefaultTask() {

    @get:Input
    abstract val intoFolder: Property<File>

    @get:Input
    abstract val artifactName: Property<String>

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val appPackage: Property<String>

    @TaskAction
    fun action() {
        val buildArtifactFolder =
            layout.buildDirectory.get().dir("compose/binaries/main-release/app")
        val tempArtifactFolder = layout.buildDirectory.get().dir("tempBuild")

        files.delete(tempArtifactFolder)
        files.copy {
            from(buildArtifactFolder)
            into(tempArtifactFolder)
        }

        intoFolder.get().mkdirs()
        val osSlug = currentOS.id
        zipTo(
            intoFolder.get().resolve("${artifactName.get()}.${version.get()}.$osSlug.zip"),
            tempArtifactFolder.asFile
        )
    }

    /**
     * Zips the contents of [baseDir] (recursively) into [zipFile], with entries stored relative
     * to [baseDir]. Replaces `org.gradle.kotlin.dsl.support.zipTo`, which was removed in Gradle 9.6.
     *
     * Uses Ant's zip writer rather than `java.util.zip`, which cannot record Unix permissions:
     * without them the app's native launcher unzips without its executable bit and the
     * distributable can't be started at all.
     */
    private fun zipTo(zipFile: File, baseDir: File) {
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            baseDir.walkTopDown().forEach { file ->
                if (file == baseDir) return@forEach
                val relativePath = file.relativeTo(baseDir).invariantSeparatorsPath
                val isDirectory = file.isDirectory
                val entry = ZipEntry(if (isDirectory) "$relativePath/" else relativePath)
                entry.unixMode = file.unixMode(isDirectory)
                zip.putNextEntry(entry)
                if (!isDirectory) file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * The file's Unix permission bits, or a sensible default on file systems without a POSIX
     * view (Windows), where the notion doesn't exist and the launcher is an `.exe` anyway.
     */
    private fun File.unixMode(isDirectory: Boolean): Int = try {
        Files.getPosixFilePermissions(toPath(), LinkOption.NOFOLLOW_LINKS)
            .sumOf { permission ->
                when (permission) {
                    PosixFilePermission.OWNER_READ -> 0b100_000_000
                    PosixFilePermission.OWNER_WRITE -> 0b010_000_000
                    PosixFilePermission.OWNER_EXECUTE -> 0b001_000_000
                    PosixFilePermission.GROUP_READ -> 0b000_100_000
                    PosixFilePermission.GROUP_WRITE -> 0b000_010_000
                    PosixFilePermission.GROUP_EXECUTE -> 0b000_001_000
                    PosixFilePermission.OTHERS_READ -> 0b000_000_100
                    PosixFilePermission.OTHERS_WRITE -> 0b000_000_010
                    PosixFilePermission.OTHERS_EXECUTE -> 0b000_000_001
                }
            }
    } catch (_: UnsupportedOperationException) {
        if (isDirectory) 0b111_101_101 else 0b110_100_100
    }

    enum class OS(val id: String) {
        Linux("linux"),
        Windows("windows"),
        MacOS("macos")
    }

    private val currentOS: OS
        get() {
            val os = System.getProperty("os.name")
            return when {
                os.equals("Mac OS X", ignoreCase = true) -> OS.MacOS
                os.startsWith("Win", ignoreCase = true) -> OS.Windows
                os.startsWith("Linux", ignoreCase = true) -> OS.Linux
                else -> error("Unknown OS name: $os")
            }
        }
}