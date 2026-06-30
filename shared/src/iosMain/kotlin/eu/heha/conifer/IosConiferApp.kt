package eu.heha.conifer

@Suppress("unused")
object IosConiferApp {

    fun initialize(isDebug: Boolean) {
        ConiferApp.initialize(
            isDebug = isDebug,
            platform = IosPlatform,
            databaseInitializer = IosDatabaseInitializer,
            clipboardController = IosClipboardController
        )
    }
}