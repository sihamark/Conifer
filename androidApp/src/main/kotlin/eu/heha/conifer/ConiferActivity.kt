package eu.heha.conifer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch


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