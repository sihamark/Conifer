package eu.heha.conifer

import eu.heha.conifer.model.database.DATABASE_NAME
import io.github.aakira.napier.Napier
import java.io.File

/**
 * The one folder the desktop app keeps everything of its own in: the database, the preferences, this
 * run's log and the reports it writes out ([JvmDatabaseInitializer], [JvmPreferencesInitializer],
 * [JvmLogFileInitializer], [JvmReportShareController]).
 *
 * It belongs to the user and sits **outside the installed program**, which is the whole point of
 * this file. It used to be worked out from where the jar sat — `data` beside the app's own folder —
 * and that put it *inside* `Conifer.app/Contents/` on macOS. Installing a new version replaces the
 * whole bundle, so every update threw away every note in it. The same folder was no better on the
 * other two desktops for a different reason: a program installed under `Program Files` or `/opt`
 * belongs to the machine, and the user running it may not be allowed to write there at all.
 *
 * Each desktop has a place meant for exactly this, and these are the ones it uses:
 * - macOS: `~/Library/Application Support/Conifer`
 * - Windows: `%LOCALAPPDATA%\Conifer` — local rather than roaming (`%APPDATA%`), because a SQLite
 *   file being copied between machines behind the app's back is how a database gets corrupted
 * - anything else, Linux included: `$XDG_DATA_HOME/conifer`, or `~/.local/share/conifer`
 *
 * Resolved once per run, which is also when anything left in the old place is moved across
 * ([migrateLegacyData]) — before the first caller is handed the folder, and so before anything in
 * it is opened.
 */
internal val jvmDataFolder: File by lazy { resolveDataFolder() }

/**
 * Says in the log where the data is and whether it had to be moved there. Called once the log file
 * exists, because [jvmDataFolder] is first asked for *by* the log file — so the move that matters
 * most to a reader happens a moment before there is anywhere to write it down.
 */
internal fun logDataFolder() {
    Napier.i { "data folder: ${jvmDataFolder.absolutePath}" }
    migrationNote?.let { Napier.i { it } }
}

/**
 * The name of the folder, written out here rather than taken from `AppConfig.appName`: this is where
 * somebody's notes live, not a label, and renaming the app must not lose them. A `./gradlew
 * :desktopApp:run` gets a folder of its own, as it did when the folder followed the jar into
 * `build/` — a build being worked on has no business writing into the notes being kept.
 */
private val folderName: String get() = if (isJvmDebugLaunch) "Conifer-dev" else "Conifer"

/** Points the whole folder somewhere else, for trying out a migration or a second set of notes. */
private const val DATA_FOLDER_PROPERTY = "conifer.dataFolder"

private var migrationNote: String? = null

private fun resolveDataFolder(): File {
    val override = System.getProperty(DATA_FOLDER_PROPERTY)?.takeIf { it.isNotBlank() }
    val folder = override?.let(::File) ?: defaultDataFolder()
    folder.mkdirs()
    migrationNote = migrateLegacyData(from = legacyDataFolder(), into = folder)
    return folder
}

private fun defaultDataFolder(): File {
    val home = File(System.getProperty("user.home") ?: ".")
    // Matched loosely for the same reason [JvmPlatform] matches it loosely: the JVM still says
    // "Mac OS X", and "Windows 11" is one of a dozen spellings of the other one.
    val osName = System.getProperty("os.name")?.lowercase() ?: ""
    return when {
        osName.contains("mac") -> File(home, "Library/Application Support/$folderName")
        osName.contains("win") -> File(windowsAppData(home), folderName)
        else -> File(xdgDataHome(home), folderName.lowercase())
    }
}

private fun windowsAppData(home: File): File =
    environmentFolder("LOCALAPPDATA")
        ?: environmentFolder("APPDATA")
        ?: File(home, "AppData/Local")

private fun xdgDataHome(home: File): File =
    environmentFolder("XDG_DATA_HOME") ?: File(home, ".local/share")

private fun environmentFolder(name: String): File? =
    runCatching { System.getenv(name) }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::File)

/**
 * Where the data used to be: `data` beside the app's own folder, worked out from the jar's location
 * exactly as it was before this file existed, so an install that has been written to since 1.2.5 is
 * found rather than abandoned.
 */
private fun legacyDataFolder(): File? = runCatching {
    val jarFile = File(ConiferApp::class.java.protectionDomain.codeSource.location.toURI())
    File(
        jarFile
            .parentFile // app folder
            .parentFile, // root folder
        "data"
    )
}.getOrNull()

/**
 * Moves [from]'s contents into [into] on the first run that has somewhere better to put them, and
 * returns what to say about it in the log; null when there was nothing to do.
 *
 * Only ever into a home without a database of its own: once this app has written notes here, an
 * installation folder still holding a database holds an *older* one, and copying that over the top
 * of what has been written since is the one outcome worse than leaving it where it is.
 *
 * Copied whole rather than file by file, which takes the WAL and the shared-memory file along with
 * the database — a set that is only consistent if it travels together. Nothing has opened the
 * database at this point, so it is a consistent set. The old folder is then deleted, so that an
 * older build started afterwards cannot go on writing into a second copy nobody reads; failing to
 * delete it is worth a line in the log and nothing more.
 */
internal fun migrateLegacyData(from: File?, into: File): String? {
    val legacy = from?.takeIf { it.isDirectory } ?: return null
    if (runCatching { legacy.canonicalFile == into.canonicalFile }.getOrDefault(true)) return null
    if (!File(legacy, DATABASE_NAME).isFile) return null
    if (File(into, DATABASE_NAME).exists()) {
        return "left the data in ${legacy.absolutePath} where it was: there is already a database here"
    }
    return runCatching {
        legacy.copyRecursively(into, overwrite = false)
        val deleted = legacy.deleteRecursively()
        "moved the data out of ${legacy.absolutePath}" +
                if (deleted) "" else ", which could not be deleted afterwards"
    }.getOrElse { "could not move the data out of ${legacy.absolutePath}: $it" }
}
