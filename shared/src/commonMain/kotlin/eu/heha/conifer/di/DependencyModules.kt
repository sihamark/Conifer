package eu.heha.conifer.di

import eu.heha.conifer.BrowserOpener
import eu.heha.conifer.ClipboardController
import eu.heha.conifer.CredentialsInitializer
import eu.heha.conifer.DatabaseInitializer
import eu.heha.conifer.Platform
import eu.heha.conifer.SyncPrefsInitializer
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.prefs.SyncPrefs
import eu.heha.conifer.sync.SyncCoordinator
import eu.heha.conifer.ui.BitsViewModel
import eu.heha.conifer.ui.SyncViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Platform-agnostic dependencies. The platform-specific implementations
 * ([Platform], [DatabaseInitializer], [ClipboardController]) are contributed by [platformModule],
 * which is built from the values passed to `ConiferApp.initialize(...)`.
 */
val coreModule = module {
    singleOf(::DatabaseController)
    single { SyncPrefs(get<SyncPrefsInitializer>().createSyncPrefsStore()) }
    single { get<CredentialsInitializer>().createCredentials() }
    singleOf(::BitsRepository)
    singleOf(::SyncCoordinator)
    viewModel {
        BitsViewModel(
            repository = get(),
            clipboardController = getOrNull(),
            permissionHandler = it.getOrNull()
        )
    }
    viewModelOf(::SyncViewModel)
}

/**
 * Wraps the explicitly supplied platform implementations into a Koin module. A null
 * [clipboardController] is simply left unbound, so `getOrNull()` resolves it to null.
 */
fun platformModule(
    platform: Platform,
    databaseInitializer: DatabaseInitializer,
    syncPrefsInitializer: SyncPrefsInitializer,
    credentialsInitializer: CredentialsInitializer,
    browserOpener: BrowserOpener,
    clipboardController: ClipboardController?
) = module {
    single { platform }
    single { databaseInitializer }
    single { syncPrefsInitializer }
    single { credentialsInitializer }
    single { browserOpener }
    clipboardController?.let { controller -> single { controller } }
}