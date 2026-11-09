import SwiftUI
import UIKit
import UserNotifications

@main
struct CaptureSpikeApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView(engine: CaptureEngine.shared)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { CaptureEngine.shared.foregrounded() }
                }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        CaptureEngine.shared.launch(launchedForLocation: launchOptions?[.location] != nil)
        return true
    }

    func applicationProtectedDataDidBecomeAvailable(_ application: UIApplication) {
        CaptureEngine.shared.protectedDataBecameAvailable()
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter, willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list]
    }
}
