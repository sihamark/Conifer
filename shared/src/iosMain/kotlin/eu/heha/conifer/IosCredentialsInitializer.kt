package eu.heha.conifer

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import eu.heha.conifer.auth.Credentials

object IosCredentialsInitializer : CredentialsInitializer {
    override fun createCredentials(): Credentials =
        Credentials(KSafe(config = KSafeConfig(appNamespace = KSAFE_APP_NAMESPACE)))
}
