package eu.heha.conifer.di

import eu.heha.conifer.DateTimeFormats
import eu.heha.conifer.JvmBrowserOpener
import eu.heha.conifer.JvmClipboardController
import eu.heha.conifer.JvmCredentialsInitializer
import eu.heha.conifer.JvmDatabaseInitializer
import eu.heha.conifer.JvmDateTimeFormats
import eu.heha.conifer.JvmPlatform
import eu.heha.conifer.JvmPreferencesInitializer
import eu.heha.conifer.Platform
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the two things the screen looks up for itself are actually in the graph.
 *
 * Everything else is resolved through a constructor, so a missing binding is a compile error; these
 * two are asked for by type at runtime — [Platform] in `BitsPane`, [DateTimeFormats] in
 * `ConiferApp.AppContent` — where a missing binding is instead a crash on the first screen, on
 * somebody else's machine.
 *
 * Only those two are resolved here. Koin builds its singles lazily, so nothing else in the graph is
 * touched and no database is created by asking.
 */
class PlatformModuleTest {

    @Test
    fun theScreenSOwnLookupsResolve() {
        val koin = koinApplication {
            modules(
                coreModule,
                platformModule(
                    platform = JvmPlatform,
                    dateTimeFormats = JvmDateTimeFormats(),
                    databaseInitializer = JvmDatabaseInitializer,
                    preferencesInitializer = JvmPreferencesInitializer,
                    credentialsInitializer = JvmCredentialsInitializer,
                    browserOpener = JvmBrowserOpener,
                    clipboardController = JvmClipboardController
                )
            )
        }.koin

        assertTrue(koin.get<Platform>() is JvmPlatform)
        assertTrue(koin.get<DateTimeFormats>() is JvmDateTimeFormats)
    }
}
