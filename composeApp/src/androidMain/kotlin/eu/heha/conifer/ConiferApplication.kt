package eu.heha.conifer

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import io.github.aakira.napier.DebugAntilog

class ConiferApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ConiferApp.initialize(DebugAntilog())
    }
}