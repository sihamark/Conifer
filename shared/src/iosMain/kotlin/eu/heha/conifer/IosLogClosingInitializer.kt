package eu.heha.conifer

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * iOS gives no dependable notice of an app ending - `applicationWillTerminate` is not called for a
 * suspended app the system reclaims, which is how an iPhone app usually goes away. So, as on
 * Android, the log says goodbye when the app goes into the background and takes it back when it
 * returns.
 *
 * The marker therefore means "the last thing this run did was leave the screen". A run whose log
 * stops without it was killed while the user was looking at it, and on this platform that covers the
 * one crash Kotlin never sees: a signal - a bad access, a watchdog kill - which ends the process
 * below the runtime and never reaches [eu.heha.conifer.log.UncaughtError] at all.
 */
object IosLogClosingInitializer : LogClosingInitializer {
    override fun installHandler(closeLog: () -> Unit, reopenLog: () -> Unit) {
        val center = NSNotificationCenter.defaultCenter
        // The main queue, which is where these notifications are posted anyway: a log line is a
        // short write, and having it land in order matters more than getting off that thread.
        center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { closeLog() }
        center.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { reopenLog() }
    }
}
