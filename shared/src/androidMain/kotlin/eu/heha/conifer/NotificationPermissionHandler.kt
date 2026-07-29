package eu.heha.conifer

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Activity
import android.os.Build
import androidx.core.app.ActivityCompat
import eu.heha.conifer.core.R
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationPermissionHandler(
    private val activity: Activity
) : PermissionHandler {

    private val _isPermissionGranted = MutableStateFlow(false)
    override val isPermissionGranted = _isPermissionGranted.asStateFlow()

    override val permissionRationale = PermissionRationale(
        lead = activity.getString(R.string.notification_permission_rationale_lead),
        text = activity.getString(R.string.notification_permission_rationale)
    )

    fun checkPermission() {
        Napier.d { "check permission" }
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