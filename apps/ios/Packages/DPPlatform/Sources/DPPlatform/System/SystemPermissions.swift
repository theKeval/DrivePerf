import CoreLocation
import CoreMotion
import DPSharedBridge
import UIKit
import UserNotifications

/// Permission prompts for onboarding and the denied sheets (S-ONB-06/07/08/10/11/16).
@MainActor
public final class SystemPermissions: NSObject, PermissionRequesting {
    /// Must match the key in Info.plist `NSLocationTemporaryUsageDescriptionDictionary`.
    public static let precisePurposeKey = "PreciseDrives"

    private let manager = CLLocationManager()
    private let activity = CMMotionActivityManager()
    private var authorizationWaiters: [CheckedContinuation<Void, Never>] = []

    override public init() {
        super.init()
        manager.delegate = self
    }

    public func current() async -> PermissionSnapshot {
        let notifications = await UNUserNotificationCenter.current().notificationSettings()
        return PermissionSnapshot(
            location: Self.map(manager.authorizationStatus),
            preciseLocation: manager.accuracyAuthorization == .fullAccuracy,
            motion: Self.motionAccess(),
            notifications: Self.map(notifications.authorizationStatus))
    }

    public func requestLocationWhenInUse() async -> PermissionSnapshot {
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
            await waitForAuthorizationChange()
        }
        return await current()
    }

    /// iOS shows the upgrade prompt right away when the app is in the foreground. "Keep Only While Using" leaves
    /// the status unchanged, so the wait also ends when the app becomes active again after the alert.
    public func requestLocationAlways() async -> PermissionSnapshot {
        guard manager.authorizationStatus == .authorizedWhenInUse else { return await current() }
        // Listen before asking, so the alert's resign-active is not missed.
        let reactivated = Task { await Self.waitForReactivation() }
        await Task.yield()
        manager.requestAlwaysAuthorization()
        // Every child must end on cancellation, or the group outlives the 90 s timeout: awaiting another task's
        // value does not cancel that task, so the reactivation child cancels it itself.
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.waitForAuthorizationChange() }
            group.addTask {
                await withTaskCancellationHandler { await reactivated.value } onCancel: { reactivated.cancel() }
            }
            group.addTask { try? await Task.sleep(for: .seconds(90)) }
            await group.next()
            group.cancelAll()
        }
        resumeAuthorizationWaiters()
        return await current()
    }

    public func requestPreciseLocation() async -> PermissionSnapshot {
        if manager.accuracyAuthorization != .fullAccuracy {
            _ = try? await manager.requestTemporaryFullAccuracyAuthorization(withPurposeKey: Self.precisePurposeKey)
        }
        return await current()
    }

    /// The Motion & Fitness prompt appears on the first activity query.
    public func requestMotion() async -> PermissionSnapshot {
        if CMMotionActivityManager.isActivityAvailable(), CMMotionActivityManager.authorizationStatus() == .notDetermined {
            let now = Date()
            await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                activity.queryActivityStarting(from: now.addingTimeInterval(-60), to: now, to: .main) { _, _ in
                    continuation.resume()
                }
            }
        }
        return await current()
    }

    public func requestNotifications() async -> PermissionSnapshot {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
        return await current()
    }

    public func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func waitForAuthorizationChange() async {
        await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                authorizationWaiters.append(continuation)
            }
        } onCancel: {
            Task { @MainActor in self.resumeAuthorizationWaiters() }
        }
    }

    private func resumeAuthorizationWaiters() {
        let waiters = authorizationWaiters
        authorizationWaiters.removeAll()
        waiters.forEach { $0.resume() }
    }

    @MainActor
    private static func waitForReactivation() async {
        let center = NotificationCenter.default
        for await _ in center.notifications(named: UIApplication.willResignActiveNotification) { break }
        for await _ in center.notifications(named: UIApplication.didBecomeActiveNotification) { break }
    }

    static func map(_ status: CLAuthorizationStatus) -> LocationAccess {
        switch status {
        case .authorizedAlways: .always
        case .authorizedWhenInUse: .whenInUse
        case .denied: .denied
        case .restricted: .restricted
        default: .notDetermined
        }
    }

    static func motionAccess() -> MotionAccess {
        guard CMMotionActivityManager.isActivityAvailable() else { return .unavailable }
        switch CMMotionActivityManager.authorizationStatus() {
        case .authorized: return .authorized
        case .denied: return .denied
        case .restricted: return .restricted
        default: return .notDetermined
        }
    }

    static func map(_ status: UNAuthorizationStatus) -> NotificationAccess {
        switch status {
        case .authorized, .provisional, .ephemeral: .authorized
        case .denied: .denied
        default: .notDetermined
        }
    }
}

extension SystemPermissions: @preconcurrency CLLocationManagerDelegate {
    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        // The delegate fires once right after creation with the current status; waiters only exist after a request.
        resumeAuthorizationWaiters()
    }
}
