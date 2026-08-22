package eu.heha.conifer.auth

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeProtectionLevel
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [Credentials] round-trips through a real [KSafe] instance (encrypted at rest, per Nextcloud
 * sync spec §3.2), isolated to a fresh temp directory per test so runs never share state.
 */
class CredentialsTest {

    @Test
    fun usernameAndAppPasswordRoundTrip() {
        val credentials = credentials()

        credentials.username = "alice"
        credentials.appPassword = "app-password-xyz"

        assertEquals("alice", credentials.username)
        assertEquals("app-password-xyz", credentials.appPassword)
    }

    @Test
    fun defaultsToEmptyStrings() {
        val credentials = credentials()

        assertEquals("", credentials.username)
        assertEquals("", credentials.appPassword)
    }

    @Test
    fun isKeySecurelyStoredReflectsKSafesProtectionInfo() {
        // Mirrors the real threshold rather than hardcoding an expected tier, since it varies by
        // machine (e.g. this passes on a dev Mac backed by the login Keychain, but could differ
        // on a keyring-less CI container) - this only checks the two stay consistent.
        val ksafe = KSafe(baseDir = Files.createTempDirectory("conifer-credentials-test").toFile())
        val credentials = Credentials(ksafe)

        val expected = ksafe.protectionInfo.effectiveLevel >= KSafeProtectionLevel.SANDBOX_PROTECTED
        assertEquals(expected, credentials.isKeySecurelyStored)
    }

    private fun credentials() =
        Credentials(KSafe(baseDir = Files.createTempDirectory("conifer-credentials-test").toFile()))
}
