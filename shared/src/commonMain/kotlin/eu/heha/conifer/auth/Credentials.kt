package eu.heha.conifer.auth

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeProtectionLevel
import eu.anifantakis.lib.ksafe.invoke

/**
 * Nextcloud login credentials obtained via [LoginFlowV2] (Nextcloud sync spec §3.2): the account
 * name and an app password, encrypted at rest via KSafe (AES-256-GCM, key hardware-backed via
 * Android Keystore / Apple Keychain / the OS secret store on desktop and web). Its own storage
 * layer, deliberately separate from `SyncPrefs` (DataStore, non-secret) and Room - secrets never
 * belong in either of those.
 */
class Credentials(private val ksafe: KSafe) {
    var username by ksafe("")
    var appPassword by ksafe("")

    /**
     * Whether the AES key protecting [username]/[appPassword] currently lives in *some*
     * OS-managed secure store (Android Keystore, Apple Keychain, Windows DPAPI, macOS login
     * Keychain, Linux Secret Service, or a non-extractable WebCrypto key) rather than KSafe's
     * last-resort SOFTWARE tier, where the key sits in a local file protected only by
     * filesystem permissions.
     *
     * The stored value is always AES-256-GCM encrypted either way - this only detects a weaker
     * *key custody* fallback, which KSafe applies silently by design (e.g. an unreachable OS
     * keyring on headless Linux, a locked macOS keychain over SSH, or an explicit opt-out).
     * Callers must check this themselves if that fallback matters to them; see
     * [KSafe.protectionInfo] for the full diagnostic (`custody`, `notes`) when this is `false`.
     */
    val isKeySecurelyStored: Boolean
        get() = ksafe.protectionInfo.effectiveLevel >= KSafeProtectionLevel.SANDBOX_PROTECTED

    /**
     * Human-readable detail on where the key actually lives, for surfacing [isKeySecurelyStored]
     * `false` to the user (see [eu.anifantakis.lib.ksafe.KSafeProtectionInfo.custody]).
     */
    val keyCustodyDescription: String
        get() = ksafe.protectionInfo.custody
}
