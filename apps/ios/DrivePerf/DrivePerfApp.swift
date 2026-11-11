import SwiftUI
import UIKit
import UserNotifications

@main
struct DrivePerfApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            TripSurfacePresentation(
                router: AppEnvironment.shared.surfaceRouter,
                dependencies: AppEnvironment.shared.dependencies)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { AppEnvironment.shared.refreshCapture() }
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
        // Core Location sessions first, before any UI or the Kotlin core (ADR-002 §5). The `.location` launch option
        // is not relied on: Spike 1 saw cold background launches without it.
        AppEnvironment.shared.launch()
        return true
    }

    func applicationProtectedDataDidBecomeAvailable(_ application: UIApplication) {
        AppEnvironment.shared.protectedDataBecameAvailable()
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter, willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.list]
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse
    ) async {
        let request = response.notification.request
        guard let route = TripNotificationRoute(
            actionID: response.actionIdentifier, requestID: request.identifier,
            category: request.content.categoryIdentifier, tripID: request.content.threadIdentifier
        ) else { return }
        let environment = await AppEnvironment.shared
        await environment.surfaceRouter.open(route, recorder: environment.recorder)
    }
}
