import DPSharedBridge
import Foundation
@testable import DPFeatures

@MainActor
final class FakeRecorder: TripRecording, TripHistory {
    var snapshot = RecorderSnapshot()
    var startError: RecorderError?
    var stopResult: TripSummary?
    var tripList: [TripSummary] = []
    private(set) var startCount = 0
    private(set) var autoDetect: Bool?
    private var observers: [AsyncStream<RecorderSnapshot>.Continuation] = []
    private var historyObservers: [AsyncStream<[TripSummary]>.Continuation] = []

    func snapshots() -> AsyncStream<RecorderSnapshot> {
        AsyncStream { continuation in
            continuation.yield(snapshot)
            observers.append(continuation)
        }
    }

    func startManualTrip() throws(RecorderError) {
        startCount += 1
        if let startError { throw startError }
        publish(RecorderSnapshot(
            phase: .recording,
            live: LiveTrip(id: "t", source: .manual, startedAt: Date(), distanceM: 0, hasFix: false)))
    }

    func stopTrip() async -> TripSummary? {
        publish(RecorderSnapshot(phase: .idle, lastFinished: stopResult))
        return stopResult
    }

    func setAutoDetect(enabled: Bool) { autoDetect = enabled }

    func trips() -> AsyncStream<[TripSummary]> {
        AsyncStream { continuation in
            continuation.yield(tripList)
            historyObservers.append(continuation)
        }
    }

    func publish(_ next: RecorderSnapshot) {
        snapshot = next
        observers.forEach { $0.yield(next) }
    }

    func finishStreams() {
        observers.forEach { $0.finish() }
        historyObservers.forEach { $0.finish() }
    }
}

@MainActor
final class FakePermissions: PermissionRequesting {
    var state = PermissionSnapshot()
    /// What each prompt returns.
    var whenInUseResult: LocationAccess = .whenInUse
    var preciseResult = true
    var alwaysResult: LocationAccess = .always
    var motionResult: MotionAccess = .authorized
    var notificationsResult: NotificationAccess = .authorized
    private(set) var requests: [String] = []
    private(set) var openedSettings = 0

    func current() async -> PermissionSnapshot { state }

    func requestLocationWhenInUse() async -> PermissionSnapshot {
        requests.append("whenInUse")
        state.location = whenInUseResult
        state.preciseLocation = preciseResult
        return state
    }

    func requestLocationAlways() async -> PermissionSnapshot {
        requests.append("always")
        state.location = alwaysResult
        return state
    }

    func requestPreciseLocation() async -> PermissionSnapshot {
        requests.append("precise")
        state.preciseLocation = true
        return state
    }

    func requestMotion() async -> PermissionSnapshot {
        requests.append("motion")
        state.motion = motionResult
        return state
    }

    func requestNotifications() async -> PermissionSnapshot {
        requests.append("notifications")
        state.notifications = notificationsResult
        return state
    }

    func openSystemSettings() { openedSettings += 1 }
}

@MainActor
final class MemoryPreferences: PreferencesStoring {
    private(set) var preferences = AppPreferences()

    func update(_ change: (inout AppPreferences) -> Void) { change(&preferences) }
}

@MainActor
final class FakeCrashReporter: CrashReporting {
    private(set) var consents: [Bool] = []
    var status: CrashReportingStatus = .off

    func apply(consent: Bool) {
        consents.append(consent)
        status = consent ? .notConfigured : .off
    }
}

@MainActor
final class FakeSelfTest: SelfTesting {
    func run() -> AsyncStream<[SelfTestStep]> {
        AsyncStream { continuation in
            continuation.yield([SelfTestStep(id: "a", title: "A", state: .running)])
            continuation.yield([SelfTestStep(id: "a", title: "A", state: .passed("ok"))])
            continuation.finish()
        }
    }
}

struct FakeHello: HelloProviding {
    func hello() -> HelloContent { HelloContent(title: "Hello DrivePerf", subtitle: "Shared core running on test") }
}

@MainActor
func makeDependencies(
    recorder: FakeRecorder = FakeRecorder(), permissions: FakePermissions = FakePermissions(),
    preferences: MemoryPreferences = MemoryPreferences(), crash: FakeCrashReporter = FakeCrashReporter()
) -> AppDependencies {
    AppDependencies(
        recorder: recorder, history: recorder, permissions: permissions, preferences: preferences,
        crashReporter: crash, selfTest: FakeSelfTest(), flags: BuildFlags(), coreInfo: FakeHello(),
        refreshCapture: {})
}
