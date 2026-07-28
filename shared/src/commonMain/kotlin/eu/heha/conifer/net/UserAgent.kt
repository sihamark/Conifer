package eu.heha.conifer.net

import eu.heha.conifer.Platform

/**
 * The `User-Agent` every Conifer HTTP request identifies itself with.
 *
 * This is not cosmetic: Nextcloud names the app password created by Login Flow v2 (spec §10)
 * after the `User-Agent` of the initiating `POST /index.php/login/v2` request, and that name is
 * what the user sees - and revokes by - under Settings → Security → "Devices & sessions".
 * Without it Ktor sends its own `KTOR_DEFAULT_USER_AGENT` ("ktor-client"), which tells the user
 * nothing about which app or device holds the token.
 *
 * Used as a fallback where no [Platform] is at hand (tests, direct
 * [eu.heha.conifer.sync.KtorWebDavStore] use); prefer [coniferUserAgent].
 */
const val CONIFER_USER_AGENT: String = "Conifer"

/**
 * [CONIFER_USER_AGENT] plus the device detail [Platform.name] carries (e.g. `Android 36`,
 * `Mac OS X 15.5, Java 21.0.2`, `iOS 18.2`), so one app password per platform stays
 * distinguishable in Nextcloud's device list.
 */
fun coniferUserAgent(platform: Platform): String = "$CONIFER_USER_AGENT (${platform.name})"
