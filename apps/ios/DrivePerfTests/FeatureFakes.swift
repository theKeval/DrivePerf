import DPSharedBridge
import Foundation
@testable import DPFeatures

@MainActor
final class FakeRecorder: TripRecording, TripHistory {
    var status = RecorderStatus.idle
    /// What a stop saves (nil: the trip is discarded and never listed).
    var stopResult: TripSummary?
    /// False: a manual wake does not start (the trip file is unavailable before the first unlock).
    var canStart = true
    var tripList: [TripSummary] = []
    private(set) var wakes: [WakeEvent] = []
    private(set) var autoDetect: Bool?
    private var observers: [AsyncStream<RecorderStatus>.Continuation] = []
    private var historyObservers: [AsyncStream<[TripSummary]>.Continuation] = []

    func statuses() -> AsyncStream<RecorderStatus> {
        AsyncStream { continuation in
            continuation.yield(status)
            observers.append(continuation)
        }
    }

    func onLaunch() {}

    /// True: the start is reported after `onWake` returns, as the shared core does.
    var startsLater = false
    /// With `startsLater`: the recorder reports `startFailed` instead of a trip.
    var failsStart = false

    func onWake(_ event: WakeEvent) {
        wakes.append(event)
        guard canStart else { return }
        let next: RecorderStatus = failsStart
            ? { var s = RecorderStatus.idle; s.startFailed = true; return s }()
            : .recording(isManual: event.kind == .manual)
        if startsLater {
            Task { @MainActor in self.publish(next) }
        } else {
            publish(next)
        }
    }

    func stop() {
        if let stopResult { publishTrips(tripList + [stopResult]) }
        publish(.idle)
    }

    private(set) var passengerMarks: [Bool] = []
    /// What `markPassenger(keep: true)` saves, as the core does: the trip ends and is listed (nil: not listed).
    var passengerResult: TripSummary?

    func markPassenger(keep: Bool) {
        passengerMarks.append(keep)
        if keep, let passengerResult { publishTrips(tripList + [passengerResult]) }
        publish(.idle)
    }

    func setAutoDetect(enabled: Bool) { autoDetect = enabled }

    func observeTrips() -> AsyncStream<[TripSummary]> {
        AsyncStream { continuation in
            continuation.yield(tripList)
            historyObservers.append(continuation)
        }
    }

    func publish(_ next: RecorderStatus) {
        status = next
        observers.forEach { $0.yield(next) }
    }

    func publishTrips(_ trips: [TripSummary]) {
        tripList = trips
        historyObservers.forEach { $0.yield(trips) }
    }

    func finishStreams() {
        observers.forEach { $0.finish() }
        historyObservers.forEach { $0.finish() }
    }
}

extension RecorderStatus {
    static func recording(
        isManual: Bool = true, distanceM: Double = 0, elapsedS: Double = 0, state: RecorderState = .recording
    ) -> RecorderStatus {
        RecorderStatus(
            state: state, tripId: "t", isManual: isManual, elapsedS: elapsedS, distanceM: distanceM,
            autoDetectEnabled: true)
    }
}

/// A fixed clock for wakes created by view models.
struct FixedClock: ClockBridge {
    func monotonicNs() -> Int64 { 42_000_000_000 }
    func wallMs() -> Int64 { 1_800_000_000_000 }
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
        recorder: recorder, history: recorder, clock: FixedClock(), permissions: permissions, preferences: preferences,
        crashReporter: crash, selfTest: FakeSelfTest(), flags: BuildFlags(), coreInfo: FakeHello(),
        refreshCapture: {})
}

@MainActor
final class FakeCheckIns: AutoDetectCheckIns {
    var lastCheckIn: Date?
}
