import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.zipTo
import java.io.File
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