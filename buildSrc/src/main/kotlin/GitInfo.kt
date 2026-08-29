import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/** Where to ask git - the repository root, whichever module is doing the asking. */
interface GitParameters : ValueSourceParameters {
    val workingDirectory: DirectoryProperty
}

/**
 * The commit the app is being built from, shortened, or `""` where git has no answer.
 *
 * A [ValueSource] rather than a `providers.exec` at configuration time, and rather than a plain git
 * call in a task action, because those get the two ends of this wrong: a value baked into the
 * configuration cache would keep naming the commit that was checked out when that cache was written,
 * while a value read only inside a task action is invisible to the up-to-date check and would let a
 * stale `BuildInfo` survive a `git commit`. Gradle re-obtains a value source on every build to decide
 * whether the configuration cache still holds, which is exactly the question being asked here.
 */
abstract class GitCommit : ValueSource<String, GitParameters> {

    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): String =
        exec.git(parameters.workingDirectory.get().asFile, "rev-parse", "--short=8", "HEAD")
            .orEmpty()
}

/**
 * Whether anything in the working tree differs from that commit - the difference between a build
 * whose source can be looked up by its hash and one whose can't.
 *
 * Deliberately boils the answer down to yes/no here rather than in the consuming task: the
 * configuration cache is invalidated whenever an obtained value changes, so handing out the list of
 * changed files would re-run configuration on every edit, while the flag only ever flips once.
 */
abstract class GitIsModified : ValueSource<Boolean, GitParameters> {

    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): Boolean =
        !exec.git(parameters.workingDirectory.get().asFile, "status", "--porcelain")
            .isNullOrEmpty()
}

/**
 * Runs git in [workingDirectory] and returns its trimmed output, or null if it had nothing to say -
 * no git on this machine, no repository (a build from an exported source tree), a command that
 * failed. A build must not depend on git being there; not knowing the commit is a worse `BuildInfo`,
 * not a broken build.
 */
internal fun ExecOperations.git(workingDirectory: File, vararg arguments: String): String? {
    val output = ByteArrayOutputStream()
    val result = runCatching {
        exec {
            commandLine(listOf("git") + arguments)
            workingDir = workingDirectory
            standardOutput = output
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
    }.getOrNull()
    if (result?.exitValue != 0) return null
    // Not toString(Charsets.UTF_8): that overload is API 33 on Android, and while this code only
    // ever runs on Gradle's JVM, the IDE checks the whole project against the app's minSdk.
    return output.toByteArray().decodeToString().trim().ifEmpty { null }
}
