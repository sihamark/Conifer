package eu.heha.conifer

import android.os.Build

data object AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    /**
     * A phone or tablet has an on-screen keyboard, which sends no shortcuts. One with a keyboard
     * attached is real but uncommon, and the screen notices that for itself the first time a
     * modifier arrives, so guessing "no" here costs such a device only its first Alt press.
     */
    override val hasHardwareKeyboard: Boolean = false
}