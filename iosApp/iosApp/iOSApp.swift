import SwiftUI
import ConiferApp

@main
struct iOSApp: App {
    init() {
        IosConiferApp.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ConiferAppView().ignoresSafeArea()
        }
    }
}
