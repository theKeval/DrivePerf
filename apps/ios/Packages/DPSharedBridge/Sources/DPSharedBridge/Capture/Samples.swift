/// Sample contracts from docs/architecture/pipeline-and-scoring.md §2.1, as plain Swift values. They mirror the
/// Kotlin `LocationSample` and `MotionSample` that DRIVE-62 adds to `:shared:domain`; the app target maps one to
/// the other once those types exist.
///
/// Times: `tMonoNs` is the monotonic clock (`ProcessInfo.systemUptime`, the base Core Motion uses), `wallMs` is
/// wall-clock milliseconds since 1970, used for display only (§2.2).
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

public struct Quat: Equatable, Sendable {
    public var x: Double
    public var y: Double
    public var z: Double
    public var w: Double

    public init(x: Double, y: Double, z: Double, w: Double) {
        self.x = x
        self.y = y
        self.z = z
        self.w = w
    }
}

/// One GNSS fix. Its description is redacted so a fix can never reach a log or crash report (ADR-007).
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
}

extension LocationSample: CustomStringConvertible, CustomDebugStringConvertible {
    public var description: String { "LocationSample(<redacted>)" }
    public var debugDescription: String { description }
}

/// One device-motion sample in the normalised device frame (§2.1): right-handed, x to the right edge, y to the top
/// edge, z out of the screen. `userAccelG` is the device's own kinematic acceleration in g, `gravityUp` a unit
/// vector pointing away from Earth, `rotRateDps` positive counter-clockwise about each axis.
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

public enum ActivityKind: String, Equatable, Sendable, Codable {
    case stationary, walking, running, cycling, automotive, unknown
}

public enum ActivityConfidence: Int, Comparable, Equatable, Sendable, Codable {
    case low, medium, high

    public static func < (lhs: Self, rhs: Self) -> Bool { lhs.rawValue < rhs.rawValue }
}

/// One motion-activity change (`CMMotionActivity` on iOS). Not a wake trigger (§1.3).
public struct ActivitySample: Equatable, Sendable, Codable {
    public var startWallMs: Int64
    public var kind: ActivityKind
    public var confidence: ActivityConfidence

    public init(startWallMs: Int64, kind: ActivityKind, confidence: ActivityConfidence) {
        self.startWallMs = startWallMs
        self.kind = kind
        self.confidence = confidence
    }

    /// Walking, running or cycling at medium confidence or better (§1.5 `humanPowered`).
    public var isHumanPowered: Bool {
        confidence >= .medium && [.walking, .running, .cycling].contains(kind)
    }
}

/// What woke the app (§1.3). Visits confirm parking but never start a trip, so they are not a wake kind.
public enum WakeKind: String, Equatable, Sendable, Codable {
    case parkedExit = "PARKED_EXIT"
    case moveSignal = "MOVE_SIGNAL"
    case manual = "MANUAL"
    case relaunch = "RELAUNCH"
}

public struct WakeEvent: Equatable, Sendable {
    public var kind: WakeKind
    /// When the OS says the event happened.
    public var eventWallMs: Int64
    /// When the app received it.
    public var receivedWallMs: Int64

    public init(kind: WakeKind, eventWallMs: Int64, receivedWallMs: Int64) {
        self.kind = kind
        self.eventWallMs = eventWallMs
        self.receivedWallMs = receivedWallMs
    }
}
