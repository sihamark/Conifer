package eu.heha.conifer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.heha.conifer.auth.Credentials
import eu.heha.conifer.di.coreModule
import eu.heha.conifer.di.platformModule
import eu.heha.conifer.ui.BitsRoute
import eu.heha.conifer.ui.theme.ConiferTheme
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.compose.getKoin
import org.koin.core.context.startKoin

object ConiferApp {

    fun initialize(
        isDebug: Boolean,
        platform: Platform,
        databaseInitializer: DatabaseInitializer,
        syncPrefsInitializer: SyncPrefsInitializer,
        credentialsInitializer: CredentialsInitializer,
        browserOpener: BrowserOpener,
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
                    credentialsInitializer,
                    browserOpener,
                    clipboardController
                )
            )
        }
    }

    @Composable
    fun AppContent(permissionHandler: PermissionHandler? = null) {
        // A no-op everywhere except the web target, where KSafe needs to finish an async
        // WebCrypto cache load before Credentials (read synchronously) can see real values.
        var credentialsReady by remember { mutableStateOf(false) }
        val koin = getKoin()
        LaunchedEffect(Unit) {
            koin.get<CredentialsInitializer>().awaitCredentialsReady()
            val credentials = koin.get<Credentials>()
            if (!credentials.isKeySecurelyStored) {
                // Not a fatal condition (the data is still AES-256-GCM encrypted either way, see
                // Credentials.isKeySecurelyStored) - but a silent key-custody downgrade to a
                // filesystem-permission-only fallback should never go unnoticed.
                Napier.w {
                    "credentials encryption key is not stored in an OS-backed secure store " +
                            "(KSafe fell back to its software tier) - see KSafe.protectionInfo for details"
                }
            }
            credentialsReady = true
        }
        if (!credentialsReady) return

        ConiferTheme {
            BitsRoute(permissionHandler)
        }
    }
}