package eu.heha.conifer

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * On Android an app is never told that it is ending - it is simply killed, and being killed while in
 * the background is the ordinary way an app goes away. So the log says goodbye when the app leaves
 * the screen, and takes it back when it returns.
 *
 * That makes the marker mean: "the last thing this run did was go into the background". A run that
 * ends with it was put away and then reclaimed, which is ordinary; a run whose log stops with the
 * app still on screen was killed in the foreground, which is not - a native crash below Kotlin, an
 * ANR the system killed, a phone that ran out of memory while the user was looking at it.
 *
 * Counted across activities rather than taken off a single one, because a configuration change stops
 * one activity and starts the next: without the count, every rotation would close the log.
 */
class AndroidLogClosingInitializer(private val application: Application) : LogClosingInitializer {
    override fun installHandler(closeLog: () -> Unit, reopenLog: () -> Unit) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var startedActivities = 0
                private var hasBeenClosed = false

                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                    // Not on the first activity of the run: the log has just been opened and has
                    // nothing to reopen, and a "reopened" line above the first real one reads oddly.
                    if (startedActivities == 1 && hasBeenClosed) reopenLog()
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities--
                    if (startedActivities == 0) {
                        hasBeenClosed = true
                        closeLog()
                    }
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }
}
