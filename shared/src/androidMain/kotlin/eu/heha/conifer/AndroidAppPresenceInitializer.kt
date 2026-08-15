package eu.heha.conifer

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * On Android an app is never told that it is ending - it is simply killed, and being killed while in
 * the background is the ordinary way an app goes away. So the app is put away when it leaves the
 * screen and brought back when it returns.
 *
 * That makes the goodbye mean: "the last thing this run did *on screen* was go into the background".
 * A run that ends there was put away and then reclaimed, which is ordinary; a run that stops with
 * the app still on screen was killed in the foreground, which is not - a native crash below Kotlin,
 * an ANR the system killed, a phone that ran out of memory while the user was looking at it.
 *
 * Note "on screen": a phone app that has been put away is not stopped, it merely has nobody
 * watching, and it goes on running until the system gets round to reclaiming it. Which is why the
 * goodbye is recorded rather than read back off the end of the log ([eu.heha.conifer.log
 * .writeLogClosed]), and why what it goes on doing there is worth cutting down (see [AppPresence]).
 *
 * Counted across activities rather than taken off a single one, because one activity starting
 * another stops the first: without the count, opening a screen would put the app away.
 */
class AndroidAppPresenceInitializer(
    private val application: Application
) : AppPresenceInitializer {
    override fun installHandler(onPutAway: () -> Unit, onBroughtBack: () -> Unit) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var startedActivities = 0
                private var hasBeenPutAway = false

                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                    // Not on the first activity of the run: the app has just started and was never
                    // away, and a "reopened" line above the log's first real one reads oddly.
                    if (startedActivities == 1 && hasBeenPutAway) onBroughtBack()
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities--
                    if (startedActivities == 0) {
                        hasBeenPutAway = true
                        onPutAway()
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
