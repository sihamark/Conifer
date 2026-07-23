package eu.heha.conifer

import androidx.compose.runtime.Composable
import eu.heha.conifer.di.coreModule
import eu.heha.conifer.di.platformModule
import eu.heha.conifer.ui.BitsRoute
import eu.heha.conifer.ui.theme.ConiferTheme
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.startKoin

object ConiferApp {

    fun initialize(
        isDebug: Boolean,
        platform: Platform,
        databaseInitializer: DatabaseInitializer,
        syncPrefsInitializer: SyncPrefsInitializer,
        clipboardController: ClipboardController? = null
    ) {
        // DebugAntilog derives its tag from the runtime stack trace, which breaks under
        // R8/ProGuard optimization in release builds — so only install it for debug builds.
        if (isDebug) {
            Napier.base(DebugAntilog())
        }
        startKoin {
            modules(
                coreModule,
                platformModule(
                    platform,
                    databaseInitializer,
                    syncPrefsInitializer,
                    clipboardController
                )
            )
        }
    }

    @Composable
    fun AppContent(permissionHandler: PermissionHandler? = null) {
        ConiferTheme {
            BitsRoute(permissionHandler)
        }
    }
}