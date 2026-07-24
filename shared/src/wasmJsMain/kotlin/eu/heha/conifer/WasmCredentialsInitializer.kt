package eu.heha.conifer

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import eu.anifantakis.lib.ksafe.awaitCacheReady
import eu.heha.conifer.auth.Credentials

object WasmCredentialsInitializer : CredentialsInitializer {
    // Retained (rather than building a fresh KSafe per call) because web instances don't share
    // their in-memory cache even when pointed at the same storage - awaitCredentialsReady() must
    // wait on the exact instance createCredentials() handed out.
    private val ksafe = KSafe(config = KSafeConfig(appNamespace = KSAFE_APP_NAMESPACE))

    override fun createCredentials(): Credentials = Credentials(ksafe)

    override suspend fun awaitCredentialsReady() {
        ksafe.awaitCacheReady()
    }
}
