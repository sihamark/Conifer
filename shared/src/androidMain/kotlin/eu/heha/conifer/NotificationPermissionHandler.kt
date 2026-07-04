package eu.heha.conifer

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Activity
import android.os.Build
import androidx.core.app.ActivityCompat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationPermissionHandler(
    private val activity: Activity
) : PermissionHandler {

    private val _isPermissionGranted = MutableStateFlow(false)
    override val isPermissionGranted = _isPermissionGranted.asStateFlow()

    override val permissionRationale =
        "This app needs the notification permission to enable you to quickly add bits to your app via a notification."

    fun checkPermission() {
        Napier.e { "check permission" }
        _isPermissionGranted.value = isPermissionGranted()
    }

    private fun isPermissionGranted(): Boolean =
        NotificationController.isNotificationPermissionGranted(activity)

    override fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity, arrayOf(POST_NOTIFICATIONS), 0)
        }
    }
}