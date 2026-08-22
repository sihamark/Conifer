package eu.heha.conifer

import android.content.Context
import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import eu.heha.conifer.auth.Credentials

class AndroidCredentialsInitializer(
    private val context: Context
) : CredentialsInitializer {
    override fun createCredentials(): Credentials = Credentials(
        KSafe(context.applicationContext, config = KSafeConfig(appNamespace = KSAFE_APP_NAMESPACE))
    )
}
