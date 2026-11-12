import CoreMotion
import DPSharedBridge
import Foundation

/// `MotionSource` on `CMDeviceMotion` (50 Hz nominal, `xArbitraryCorrectedZVertical`). Samples are delivered on a
/// private serial queue through a `@Sendable` closure: a closure created in a `@MainActor` context would inherit
/// main-actor isolation and trap on the first sample, which lost two field trips in Spike 1 (spike change 6).
/// Samples go to the sink in batches of `batchSize` (≈ 0.5 s at 50 Hz), so the Kotlin core is not called 50 times a
/// second; `stop()` flushes the last partial batch on the same queue before it returns.
public final class CoreMotionSource: MotionSourceBridge, @unchecked Sendable {
    public static let batchSize = 25

    private let manager = CMMotionManager()
    private let recorder = CMSensorRecorder()
    private let queue: OperationQueue
    private let clock: SystemClock
    private let lock = NSLock()
    private var pending: [MotionSample] = []
    private var sink: (@Sendable ([MotionSample]) -> Void)?

    public init(clock: SystemClock = SystemClock()) {
        self.clock = clock
        queue = OperationQueue()
        queue.name = "com.driveperf.motion"
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInitiated
    }

    public func capability() -> MotionCapability {
        MotionCapability(
            hasGyroscope: manager.isGyroAvailable, maxRateHz: manager.isDeviceMotionAvailable ? 100 : 0)
    }

    public func start(rateHz: Int, sink: @escaping @Sendable ([MotionSample]) -> Void) {
        lock.withLock {
            guard manager.isDeviceMotionAvailable else { return }
            manager.stopDeviceMotionUpdates()
            pending = []
            self.sink = sink
            manager.deviceMotionUpdateInterval = Self.interval(forRateHz: rateHz)
            let clock = clock
            manager.startDeviceMotionUpdates(using: .xArbitraryCorrectedZVertical, to: queue) {
                @Sendable [weak self] motion, _ in
                guard let self, let motion else { return }
                self.collect(MotionNormalizer.sample(from: motion, clock: clock))
            }
        }
    }

    /// IMU back-off (F-CAP-16: 50, 25 or 10 Hz). The shared core decides when (Low Power Mode, battery at or below
    /// 15 %, thermal state serious or critical); this only applies it, to the running updates at once.
    public func setRate(rateHz: Int) {
        lock.withLock { manager.deviceMotionUpdateInterval = Self.interval(forRateHz: rateHz) }
    }

    /// The update interval for a requested rate: never faster than the hardware's 100 Hz, never slower than 1 Hz.
    static func interval(forRateHz rateHz: Int) -> TimeInterval {
        1 / Double(min(max(rateHz, 1), 100))
    }

    public func stop() {
        let (rest, sink) = lock.withLock { () -> ([MotionSample], (@Sendable ([MotionSample]) -> Void)?) in
            manager.stopDeviceMotionUpdates()
            defer {
                pending = []
                self.sink = nil
            }
            return (pending, self.sink)
        }
        guard !rest.isEmpty, let sink else { return }
        // The last partial batch goes out on the motion queue too, never on the caller's thread (spike change 6),
        // and before `stop()` returns, so no sample arrives after it.
        if OperationQueue.current === queue {
            sink(rest)
        } else {
            queue.addOperations([BlockOperation { sink(rest) }], waitUntilFinished: true)
        }
    }

    private func collect(_ sample: MotionSample) {
        let ready = lock.withLock { () -> ([MotionSample], (@Sendable ([MotionSample]) -> Void)?)? in
            pending.append(sample)
            guard pending.count >= Self.batchSize else { return nil }
            defer { pending = [] }
            return (pending, sink)
        }
        if let (batch, sink) = ready { sink?(batch) }
    }

    /// Starts `CMSensorRecorder`, so the estimated start (§2.8) has accelerometer history to read. The recording
    /// is kept by the OS for up to 12 hours; call it again at every launch while automatic trips are on.
    /// Does nothing without Motion & Fitness access, so it never shows the prompt.
    public func recordAccelerometerHistory(forHours hours: Double = 12) {
        guard CMSensorRecorder.isAccelerometerRecordingAvailable(),
            CMSensorRecorder.authorizationStatus() == .authorized
        else { return }
        recorder.recordAccelerometer(forDuration: hours * 3600)
    }

    public func queryRecordedAccelerometer(
        fromWallMs: Int64, toWallMs: Int64, callback: @escaping @Sendable ([RecordedAccelSample]) -> Void
    ) {
        guard CMSensorRecorder.isAccelerometerRecordingAvailable(), fromWallMs < toWallMs else {
            callback([])
            return
        }
        let clock = clock
        DispatchQueue.global(qos: .utility).async {
            let recorder = CMSensorRecorder()
            var samples: [RecordedAccelSample] = []
            if let list = recorder.accelerometerData(from: Date(wallMs: fromWallMs), to: Date(wallMs: toWallMs)) {
                var items = NSFastEnumerationIterator(list)
                while let next = items.next() {
                    guard let item = next as? CMRecordedAccelerometerData else { continue }
                    let a = item.acceleration
                    samples.append(
                        RecordedAccelSample(
                            tMonoNs: clock.monotonicNs(atWall: item.startDate), accelG: Vec3(x: a.x, y: a.y, z: a.z)))
                }
            }
            callback(samples)
        }
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

    public static func sample(from motion: CMDeviceMotion, clock: SystemClock) -> MotionSample {
        let q = motion.attitude.quaternion
        return normalize(
            tMonoNs: clock.monotonicNs(atUptime: motion.timestamp),
            userAcceleration: (motion.userAcceleration.x, motion.userAcceleration.y, motion.userAcceleration.z),
            gravity: (motion.gravity.x, motion.gravity.y, motion.gravity.z),
            rotationRate: (motion.rotationRate.x, motion.rotationRate.y, motion.rotationRate.z),
            quaternion: (q.w, q.x, q.y, q.z))
    }

    public static func normalize(
        tMonoNs: Int64,
        userAcceleration a: (Double, Double, Double),
        gravity g: (Double, Double, Double),
        rotationRate r: (Double, Double, Double),
        /// (w, x, y, z)
        quaternion q: (Double, Double, Double, Double)?
    ) -> MotionSample {
        MotionSample(
            tMonoNs: tMonoNs,
            userAccelG: Vec3(x: a.0 * userAccelerationSign, y: a.1 * userAccelerationSign, z: a.2 * userAccelerationSign),
            gravityUp: Vec3(x: g.0 * gravitySign, y: g.1 * gravitySign, z: g.2 * gravitySign),
            rotRateDps: Vec3(x: r.0 * degreesPerRadian, y: r.1 * degreesPerRadian, z: r.2 * degreesPerRadian),
            attitude: q.map { Quat(w: $0.0, x: $0.1, y: $0.2, z: $0.3) })
    }
}
