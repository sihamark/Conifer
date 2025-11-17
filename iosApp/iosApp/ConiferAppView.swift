import UIKit
import SwiftUI
import ComposeApp

struct ConiferAppView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ConiferAppViewControllerKt.viewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}



