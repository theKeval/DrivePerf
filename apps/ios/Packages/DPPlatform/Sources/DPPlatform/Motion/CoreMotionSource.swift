import CoreMotion
import DPSharedBridge
import Foundation

/// `MotionSource` on `CMDeviceMotion` (50 Hz nominal, `xArbitraryCorrectedZVertical`). Samples are delivered on a
/// private serial queue through a `@Sendable` closure: a closure created in a `@MainActor` context would inherit
/// main-actor isolation and trap on the first sample, which lost two field trips in Spike 1 (spike change 6).
public final class CoreMotionSource: MotionSourceBridge, @unchecked Sendable {
    private let manager = CMMotionManager()
    private let queue: OperationQueue
    private let lock = NSLock()

    public init() {
        queue = OperationQueue()
        queue.name = "com.driveperf.motion"
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInitiated
    }

    public var isAvailable: Bool { manager.isDeviceMotionAvailable }

    public func start(rateHz: Int, onSample: @escaping @Sendable (MotionSample) -> Void) {
        lock.withLock {
            guard manager.isDeviceMotionAvailable else { return }
            manager.stopDeviceMotionUpdates()
            manager.deviceMotionUpdateInterval = 1 / Double(max(rateHz, 1))
            manager.startDeviceMotionUpdates(using: .xArbitraryCorrectedZVertical, to: queue) { @Sendable motion, _ in
                guard let motion else { return }
                onSample(MotionNormalizer.sample(from: motion))
            }
        }
    }

    /// IMU back-off (§1.6: 10 Hz after 120 s stationary) and back to 50 Hz on a pull-away.
    public func setRate(hz: Int) {
        lock.withLock { manager.deviceMotionUpdateInterval = 1 / Double(max(hz, 1)) }
    }

    public func stop() {
        lock.withLock { manager.stopDeviceMotionUpdates() }
    }
}

/// Core Motion → normalised device frame (§2.1). Pure, so it is unit-tested.
///
/// Core Motion uses the same axes as the spec (x right edge, y top edge, z out of the screen), so only signs and
/// units change:
/// - `gravity` points towards Earth (face up at rest reads z = −1 g); the spec wants `gravityUp`, so it is negated.
/// - The raw accelerometer reads −1 g face up, so it reports the negative of specific force. Since
///   raw = gravity + userAcceleration and gravity is the Earth-pointing vector, `userAcceleration` = −(kinematic
///   acceleration); it is negated so +x means accelerating towards the right edge.
/// - `rotationRate` is right-handed (counter-clockwise positive) in rad/s; converted to °/s.
///
/// The spec requires a real-device conformance check (shake right, tilt, spin) rather than trusting documentation:
/// the on-device self-test has a "slide right" step that confirms `userAccelerationSign`.
public enum MotionNormalizer {
    public static let userAccelerationSign = -1.0
    public static let gravitySign = -1.0
    static let degreesPerRadian = 180 / Double.pi

    public static func sample(from motion: CMDeviceMotion) -> MotionSample {
        let q = motion.attitude.quaternion
        return normalize(
            timestamp: motion.timestamp,
            userAcceleration: (motion.userAcceleration.x, motion.userAcceleration.y, motion.userAcceleration.z),
            gravity: (motion.gravity.x, motion.gravity.y, motion.gravity.z),
            rotationRate: (motion.rotationRate.x, motion.rotationRate.y, motion.rotationRate.z),
            quaternion: (q.x, q.y, q.z, q.w))
    }

    public static func normalize(
        timestamp: TimeInterval,
        userAcceleration a: (Double, Double, Double),
        gravity g: (Double, Double, Double),
        rotationRate r: (Double, Double, Double),
        quaternion q: (Double, Double, Double, Double)?
    ) -> MotionSample {
        MotionSample(
            tMonoNs: Int64((timestamp * 1_000_000_000).rounded()),
            userAccelG: Vec3(x: a.0 * userAccelerationSign, y: a.1 * userAccelerationSign, z: a.2 * userAccelerationSign),
            gravityUp: Vec3(x: g.0 * gravitySign, y: g.1 * gravitySign, z: g.2 * gravitySign),
            rotRateDps: Vec3(x: r.0 * degreesPerRadian, y: r.1 * degreesPerRadian, z: r.2 * degreesPerRadian),
            attitude: q.map { Quat(x: $0.0, y: $0.1, z: $0.2, w: $0.3) })
    }
}
