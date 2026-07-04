package eu.heha.conifer

import platform.UIKit.UIDevice

data object IosPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}