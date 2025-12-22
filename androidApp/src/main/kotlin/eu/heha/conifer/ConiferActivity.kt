package eu.heha.conifer

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Activity
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val imageResources = arrayOf(
    R.raw.bark,
    R.raw.cone,
    R.raw.pollen_cones,
    R.raw.tree,
    R.raw.young_seeds,
)

class ConiferActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val notificationPermissionHandler = NotificationPermissionHandler(this)
        notificationPermissionHandler.checkPermission()
        lifecycleScope.launch {
            repeatOnLifecycle(State.RESUMED) {
                notificationPermissionHandler.checkPermission()
            }
        }

        setContent {
            ConiferApp.AppContent(notificationPermissionHandler)
        }
    }
}


class NotificationPermissionHandler(
    private val activity: Activity
) : ConiferApp.PermissionHandler {

    private val _isPermissionGranted = MutableStateFlow(false)
    override val isPermissionGranted = _isPermissionGranted.asStateFlow()

    override val permissionRationale =
        "This app needs the notification permission to enable you to quickly add bits to your app via a notification."

    fun checkPermission() {
        Napier.e { "check permission" }
        _isPermissionGranted.value = isPermissionGranted()
    }

    private fun isPermissionGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(activity, POST_NOTIFICATIONS) == PERMISSION_GRANTED
        } else {
            true
        }

    override fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity, arrayOf(POST_NOTIFICATIONS), 0)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    ConiferApp.AppContent()
}