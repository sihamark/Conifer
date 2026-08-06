package eu.heha.conifer

import platform.UIKit.UIDevice

data object IosPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

    /** As on Android: assume the on-screen keyboard, and let a real one announce itself. */
    override val hasHardwareKeyboard: Boolean = false
}