import SwiftUI
import ConiferApp

@main
struct iOSApp: App {
    init() {
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        IosConiferApp.shared.initialize(isDebug: isDebug)
    }

    var body: some Scene {
        WindowGroup {
            ConiferAppView().ignoresSafeArea()
        }
    }
}
