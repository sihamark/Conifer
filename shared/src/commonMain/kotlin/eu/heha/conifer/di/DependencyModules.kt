package eu.heha.conifer.di

import eu.heha.conifer.BrowserOpener
import eu.heha.conifer.ClipboardController
import eu.heha.conifer.CredentialsInitializer
import eu.heha.conifer.DatabaseInitializer
import eu.heha.conifer.DateTimeFormats
import eu.heha.conifer.Platform
import eu.heha.conifer.SyncPrefsInitializer
import eu.heha.conifer.model.BitsRepository
import eu.heha.conifer.model.database.DatabaseController
import eu.heha.conifer.net.coniferUserAgent
import eu.heha.conifer.prefs.SyncPrefs
import eu.heha.conifer.sync.SyncCoordinator
import eu.heha.conifer.ui.BitsViewModel
import eu.heha.conifer.ui.SyncViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Platform-agnostic dependencies. The platform-specific implementations
 * ([Platform], [DateTimeFormats], [DatabaseInitializer], [ClipboardController]) are contributed by
 * [platformModule],
 * which is built from the values passed to `ConiferApp.initialize(...)`.
 */
val coreModule = module {
    singleOf(::DatabaseController)
    single { SyncPrefs(get<SyncPrefsInitializer>().createSyncPrefsStore()) }
    single { get<CredentialsInitializer>().createCredentials() }
    singleOf(::BitsRepository)
    // Not singleOf(::SyncCoordinator): its trailing `loginFlow` parameter has a default value
    // (LoginFlowV2()), but singleOf resolves every constructor parameter via get() regardless of
    // defaults, so it would demand a LoginFlowV2 binding that doesn't exist and never should -
    // omitting it here is what lets Kotlin's own default apply (with the platform-specific
    // user agent it derives its LoginFlowV2 from).
    single { SyncCoordinator(get(), get(), get(), get(), coniferUserAgent(get())) }
    // Not viewModelOf(::BitsViewModel): the clipboard controller is optional and has to resolve to
    // null where no platform supplied one. The permission handler is not injected at all — the
    // screen binds the current one, see BitsViewModel.bindPermissionHandler.
    viewModel {
        BitsViewModel(
            repository = get(),
            dateTimeFormats = get(),
            clipboardController = getOrNull()
        )
    }
    // Same reason as BitsViewModel above: the clipboard controller is optional, so it has to
    // resolve to null rather than fail to resolve.
    viewModel {
        SyncViewModel(
            coordinator = get(),
            bitsRepository = get(),
            clipboardController = getOrNull()
        )
    }
}

/**
 * Wraps the explicitly supplied platform implementations into a Koin module. A null
 * [clipboardController] is simply left unbound, so `getOrNull()` resolves it to null.
 */
fun platformModule(
    platform: Platform,
    dateTimeFormats: DateTimeFormats,
    databaseInitializer: DatabaseInitializer,
    syncPrefsInitializer: SyncPrefsInitializer,
    credentialsInitializer: CredentialsInitializer,
    browserOpener: BrowserOpener,
    clipboardController: ClipboardController?
) = module {
    single { platform }
    single { dateTimeFormats }
    single { databaseInitializer }
    single { syncPrefsInitializer }
    single { credentialsInitializer }
    single { browserOpener }
    clipboardController?.let { controller -> single { controller } }
}