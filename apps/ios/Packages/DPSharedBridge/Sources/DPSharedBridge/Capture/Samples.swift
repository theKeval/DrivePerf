import Foundation

/// Swift mirrors of the capture types in `:shared:domain` (`com.driveperf.domain.capture`, pipeline-and-scoring.md
/// §2.1): same names, fields and units, so `DPPlatform` (which cannot link the Kotlin framework) and the app
/// target's Kotlin adapters (`DrivePerf/CoreBridge/`) map one to the other field by field.
///
/// Times: `tMonoNs` is the monotonic clock of `ClockBridge` (continuous, keeps counting while the phone sleeps);
/// `wallMs` is UTC epoch milliseconds, for display only (§2.2).

/// A 3-vector in the normalised device frame (Kotlin `Vec3`).
public struct Vec3: Equatable, Sendable {
    public var x: Double
    public var y: Double
    public var z: Double

    public init(x: Double, y: Double, z: Double) {
        self.x = x
        self.y = y
        self.z = z
    }

    public var norm: Double { (x * x + y * y + z * z).squareRoot() }
}

/// A unit quaternion (Kotlin `Quat`).
public struct Quat: Equatable, Sendable {
    public var w: Double
    public var x: Double
    public var y: Double
    public var z: Double

    public init(w: Double, x: Double, y: Double, z: Double) {
        self.w = w
        self.x = x
        self.y = y
        self.z = z
    }
}

/// A WGS 84 position in degrees (Kotlin `GeoPoint`). Its description is redacted (ADR-007).
public struct GeoPoint: Equatable, Sendable, Codable {
    public var lat: Double
    public var lng: Double

    public init(lat: Double, lng: Double) {
        self.lat = lat
        self.lng = lng
    }
}

extension GeoPoint: CustomStringConvertible, CustomDebugStringConvertible {
    public var description: String { "GeoPoint(<redacted>)" }
    public var debugDescription: String { description }
}

/// One GNSS fix (Kotlin `LocationSample`). Its description is redacted so a fix can never reach a log or crash
/// report (ADR-007).
public struct LocationSample: Equatable, Sendable {
    public var tMonoNs: Int64
    public var wallMs: Int64
    public var lat: Double
    public var lng: Double
    public var altM: Double?
    public var hAccM: Double?
    public var vAccM: Double?
    public var speedMps: Double?
    public var speedAccMps: Double?
    public var courseDeg: Double?
    public var courseAccDeg: Double?

    public init(
        tMonoNs: Int64, wallMs: Int64, lat: Double, lng: Double, altM: Double? = nil, hAccM: Double? = nil,
        vAccM: Double? = nil, speedMps: Double? = nil, speedAccMps: Double? = nil, courseDeg: Double? = nil,
        courseAccDeg: Double? = nil
    ) {
        self.tMonoNs = tMonoNs
        self.wallMs = wallMs
        self.lat = lat
        self.lng = lng
        self.altM = altM
        self.hAccM = hAccM
        self.vAccM = vAccM
        self.speedMps = speedMps
        self.speedAccMps = speedAccMps
        self.courseDeg = courseDeg
        self.courseAccDeg = courseAccDeg
    }

    public var point: GeoPoint { GeoPoint(lat: lat, lng: lng) }
}

extension LocationSample: CustomStringConvertible, CustomDebugStringConvertible {
    public var description: String { "LocationSample(<redacted>)" }
    public var debugDescription: String { description }
}

/// One device-motion sample in the normalised device frame (Kotlin `MotionSample`, §2.1): right-handed, x to the
/// right edge, y to the top edge, z out of the screen. `userAccelG` is the device's own kinematic acceleration in g,
/// `gravityUp` a unit vector pointing away from Earth, `rotRateDps` positive counter-clockwise about each axis.
public struct MotionSample: Equatable, Sendable {
    public var tMonoNs: Int64
    public var userAccelG: Vec3
    public var gravityUp: Vec3
    public var rotRateDps: Vec3
    public var attitude: Quat?

    public init(tMonoNs: Int64, userAccelG: Vec3, gravityUp: Vec3, rotRateDps: Vec3, attitude: Quat?) {
        self.tMonoNs = tMonoNs
        self.userAccelG = userAccelG
        self.gravityUp = gravityUp
        self.rotRateDps = rotRateDps
        self.attitude = attitude
    }
}

/// Raw accelerometer history, gravity included, in g (Kotlin `RecordedAccelSample`, `CMSensorRecorder`, §2.8).
public struct RecordedAccelSample: Equatable, Sendable {
    public var tMonoNs: Int64
    public var accelG: Vec3

    public init(tMonoNs: Int64, accelG: Vec3) {
        self.tMonoNs = tMonoNs
        self.accelG = accelG
    }
}

/// Kotlin `ActivityType`.
public enum ActivityType: String, Equatable, Sendable, Codable {
    case stationary, walking, running, cycling, automotive, unknown
}

/// Kotlin `ActivityConfidence`.
public enum ActivityConfidence: Int, Comparable, Equatable, Sendable, Codable {
    case low, medium, high

    public static func < (lhs: Self, rhs: Self) -> Bool { lhs.rawValue < rhs.rawValue }
}

/// One activity classification, live or from history (Kotlin `ActivitySample`). `tMonoNs` and `wallMs` are when
/// the activity started.
public struct ActivitySample: Equatable, Sendable {
    public var tMonoNs: Int64
    public var wallMs: Int64
    public var type: ActivityType
    public var confidence: ActivityConfidence

    public init(tMonoNs: Int64, wallMs: Int64, type: ActivityType, confidence: ActivityConfidence) {
        self.tMonoNs = tMonoNs
        self.wallMs = wallMs
        self.type = type
        self.confidence = confidence
    }

    /// Walking, running or cycling at medium confidence or better (§1.5 `humanPowered`).
    public var isHumanPowered: Bool {
        confidence >= .medium && [.walking, .running, .cycling].contains(type)
    }
}

/// What the motion hardware can do (Kotlin `MotionCapability`).
public struct MotionCapability: Equatable, Sendable {
    public var hasGyroscope: Bool
    /// 0 when device motion is unavailable.
    public var maxRateHz: Int

    public init(hasGyroscope: Bool, maxRateHz: Int) {
        self.hasGyroscope = hasGyroscope
        self.maxRateHz = maxRateHz
    }
}

/// Kotlin `ThermalState`, the iOS names.
public enum ThermalState: String, Equatable, Sendable {
    case nominal, fair, serious, critical
}

/// Battery, Low Power Mode and thermal state (Kotlin `PowerStatus`).
public struct PowerStatus: Equatable, Sendable {
    public var batteryPct: Int?
    public var isCharging: Bool
    public var lowPowerMode: Bool
    public var thermal: ThermalState

    public init(batteryPct: Int?, isCharging: Bool, lowPowerMode: Bool, thermal: ThermalState) {
        self.batteryPct = batteryPct
        self.isCharging = isCharging
        self.lowPowerMode = lowPowerMode
        self.thermal = thermal
    }
}

/// Why the app was woken (Kotlin `WakeKind`, §1.3). Visits confirm parking but never start a trip, so they are not
/// a wake kind. `activityVehicle` is Android only.
public enum WakeKind: String, Equatable, Sendable, Codable {
    case parkedExit = "PARKED_EXIT"
    case moveSignal = "MOVE_SIGNAL"
    case activityVehicle = "ACTIVITY_VEHICLE"
    case manual = "MANUAL"
    case relaunch = "RELAUNCH"
}

/// One wake (Kotlin `WakeEvent`). `tMonoNs` and `wallMs` are the OS event's own time, not the delivery time, so a
/// stale event can be dropped. `sourceDetail` says where a manual start came from (`app`, `live_activity`, …): a
/// code key, never free text.
public struct WakeEvent: Equatable, Sendable {
    public var kind: WakeKind
    public var tMonoNs: Int64
    public var wallMs: Int64
    public var sourceDetail: String?

    public init(kind: WakeKind, tMonoNs: Int64, wallMs: Int64, sourceDetail: String? = nil) {
        self.kind = kind
        self.tMonoNs = tMonoNs
        self.wallMs = wallMs
        self.sourceDetail = sourceDetail
    }
}

extension Date {
    /// UTC epoch milliseconds, the `wallMs` of every sample and event.
    public var wallMs: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }

    public init(wallMs: Int64) {
        self.init(timeIntervalSince1970: TimeInterval(wallMs) / 1000)
    }
}
