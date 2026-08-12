// File-level: the posix write and the CoreGraphics rect below are cinterop declarations.
@file:OptIn(ExperimentalForeignApi::class)

package eu.heha.conifer

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs

/**
 * The iOS share sheet, with the report as a file: the way anything leaves an iPhone, and the only
 * way a log gets off one at all short of plugging it into a Mac.
 *
 * The file goes into the temporary directory rather than into `Documents/`, since it is a copy made
 * to be sent and the system is welcome to reclaim it afterwards. It is written on the calling
 * thread, and only the presenting is hopped onto the main queue - UIKit's rule, and the reason this
 * answers `true` for "handed to UIKit" rather than for "sheet is on screen": whether a sheet appears
 * is known one runloop turn later than anyone here can wait for.
 */
object IosReportShareController : ReportShareController {
    override fun share(fileName: String, text: String): Boolean {
        val path = NSTemporaryDirectory() + fileName
        val file = fopen(path, "w")
        if (file == null) {
            Napier.w { "could not write the crash report to $path" }
            return false
        }
        try {
            fputs(text, file)
        } finally {
            fclose(file)
        }
        val url = NSURL.fileURLWithPath(path)
        dispatch_async(dispatch_get_main_queue()) {
            val presenter = topmostViewController()
            if (presenter == null) {
                Napier.w { "no view controller to show the share sheet from" }
                return@dispatch_async
            }
            val sheet = UIActivityViewController(
                activityItems = listOf(url),
                applicationActivities = null,
            )
            // An iPad shows this as a popover and needs somewhere to point it at; without an anchor
            // UIKit raises rather than guesses. The middle of the screen is the honest answer here,
            // since the share was asked for by a button this object knows nothing about.
            sheet.popoverPresentationController?.let { popover ->
                val view = presenter.view
                popover.sourceView = view
                popover.sourceRect = CGRectMake(
                    view.bounds.useContents { size.width / 2 },
                    view.bounds.useContents { size.height / 2 },
                    0.0,
                    0.0,
                )
            }
            presenter.presentViewController(sheet, animated = true, completion = null)
        }
        return true
    }
}

/**
 * The view controller anything presented has to be presented from: the key window's root, or
 * whatever it has itself presented since - presenting from a controller that is already covered is
 * the classic way for a sheet to silently never appear.
 */
private fun topmostViewController(): UIViewController? {
    val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
    val keyWindow = windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
    var controller = keyWindow?.rootViewController ?: return null
    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}
