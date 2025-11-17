import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        IosConiferApp.shared.initialize()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
