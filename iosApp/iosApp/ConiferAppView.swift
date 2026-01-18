import UIKit
import SwiftUI
import ConiferApp

struct ConiferAppView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ConiferAppViewControllerKt.viewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}



