package eu.heha.conifer

import kotlin.time.Instant

/** When this build was made, out of [BuildInfo.buildTimeMillis]. */
val BuildInfo.buildTime: Instant
    get() = Instant.fromEpochMilliseconds(buildTimeMillis)

/**
 * How a build names itself where a person will read it back: the log file's header, a bug report, the
 * debug details on the sync pane. `1.2.4 (9), commit 29ba2459, built 2026-08-10T19:58:08Z`.
 *
 * Both halves earn their place. The commit is what a report can actually be looked up by - "version
 * 1.2.4" spans a month of them - and the build time is what separates two runs of the same commit,
 * which is the whole difference between a machine that has the fix and one that hasn't. A build made
 * from a working tree with uncommitted changes says so: its hash describes source that only exists on
 * the machine it was built on.
 *
 * In UTC and ISO, like everything else the app writes down rather than shows (see [DateTimeFormats]):
 * this is read beside logs from other devices, and the reader's own clock is not the question.
 * Truncated to the second, which is as precise as a build time ever needs to be.
 *
 * Every value is a parameter defaulting to [BuildInfo] so that this can be read back in a test - the
 * generated object holds whatever the machine running the test happened to build.
 */
fun buildLabel(
    versionName: String = BuildInfo.versionName,
    versionCode: Int = BuildInfo.versionCode,
    commit: String = BuildInfo.commit,
    isModified: Boolean = BuildInfo.isModified,
    buildTime: Instant = BuildInfo.buildTime,
): String = buildString {
    append(versionName)
    append(" (").append(versionCode).append("), commit ").append(commit)
    if (isModified) append(" (modified)")
    append(", built ")
    append(Instant.fromEpochSeconds(buildTime.epochSeconds))
}
