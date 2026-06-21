package eu.heha.conifer

import android.os.Build

data object AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}