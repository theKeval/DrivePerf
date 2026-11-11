import SafariServices
import SwiftUI

/// What the in-app browser may open: web pages only. Anything else (a `tel:` or `mailto:` link, a Settings URL) goes to
/// the system as before.
enum InAppBrowserPolicy {
    static func opensInApp(_ url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased() else { return false }
        return scheme == "https" || scheme == "http"
    }
}

/// `SFSafariViewController` for SwiftUI: the Terms of Use, Privacy Policy and "How it works" links open here, inside
/// the app, not in Safari (F-ONB-02.3, F-ONB-03.3). No cookies or history are shared with Safari beyond what the
/// system view controller does by itself, and the app sees none of the page.
private struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let configuration = SFSafariViewController.Configuration()
        configuration.entersReaderIfAvailable = false
        let controller = SFSafariViewController(url: url, configuration: configuration)
        controller.dismissButtonStyle = .close
        return controller
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

private struct BrowserTarget: Identifiable {
    let url: URL
    var id: URL { url }
}

private struct InAppBrowserModifier: ViewModifier {
    @State private var target: BrowserTarget?

    func body(content: Content) -> some View {
        content
            .environment(
                \.openURL,
                OpenURLAction { url in
                    guard InAppBrowserPolicy.opensInApp(url) else { return .systemAction }
                    target = BrowserTarget(url: url)
                    return .handled
                }
            )
            .fullScreenCover(item: $target) { target in
                SafariView(url: target.url).ignoresSafeArea()
            }
    }
}

extension View {
    /// Opens every web link in this view in an in-app browser. Apply it once per presentation: a sheet that contains
    /// links applies it again, because one view cannot present two things at once.
    func opensLinksInApp() -> some View {
        modifier(InAppBrowserModifier())
    }
}
