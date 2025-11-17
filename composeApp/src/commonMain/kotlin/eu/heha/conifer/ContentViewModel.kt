package eu.heha.conifer

import androidx.lifecycle.ViewModel
import io.github.aakira.napier.Napier

class ContentViewModel : ViewModel() {
    val platform = getPlatform()

    fun logPlatformInfo() {
        Napier.i { "started on platform ${platform.name}" }
    }
}
