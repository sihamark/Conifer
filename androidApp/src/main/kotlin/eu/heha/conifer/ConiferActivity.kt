package eu.heha.conifer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
            NotificationController(this@ConiferActivity).conversationNotification()
            repeatOnLifecycle(State.RESUMED) {
                notificationPermissionHandler.checkPermission()
            }
        }

        setContent {
            ConiferApp.AppContent(notificationPermissionHandler)
        }
    }
}


@Preview
@Composable
private fun AppAndroidPreview() {
    ConiferApp.AppContent()
}