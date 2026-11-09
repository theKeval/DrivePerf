import CaptureCore
import CoreMotion
import Foundation

/// Appends samples for the active trip. All file and stats access happens on one serial queue,
/// which is also the queue device-motion callbacks are delivered on.
final class TripWriter: @unchecked Sendable {
    private let queue = DispatchQueue(label: "capture.writer", qos: .userInitiated)
    let motionQueue: OperationQueue
    private let flushInterval: TimeInterval = 2
    private let bootOffset: TimeInterval

    private var gps: BufferedFile
    private var imu: BufferedFile
    private var backfill: BufferedFile
    private var imuStats: StreamStats

    init(directory: URL, imuStats: StreamStats) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        gps = try BufferedFile(url: directory.appendingPathComponent("gps.csv"))
        imu = try BufferedFile(url: directory.appendingPathComponent("imu.csv"))
        backfill = try BufferedFile(url: directory.appendingPathComponent("backfill.csv"))
        self.imuStats = imuStats
        bootOffset = Date().timeIntervalSince1970 - ProcessInfo.processInfo.systemUptime
        motionQueue = OperationQueue()
        motionQueue.maxConcurrentOperationCount = 1
        motionQueue.underlyingQueue = queue
    }

    /// Called on `motionQueue`.
    func append(motion: CMDeviceMotion) {
        let time = motion.timestamp + bootOffset
        let a = motion.userAcceleration
        let r = motion.rotationRate
        let g = motion.gravity
        let q = motion.attitude.quaternion
        imu.append(
            "\(time),\(a.x),\(a.y),\(a.z),\(r.x),\(r.y),\(r.z),\(g.x),\(g.y),\(g.z),\(q.x),\(q.y),\(q.z),\(q.w)\n",
            flushInterval: flushInterval)
        imuStats.record(at: Date(timeIntervalSince1970: time))
    }

    func append(fix: GPSFix) {
        queue.async { [self] in
            gps.append(
                "\(fix.time.timeIntervalSince1970),\(fix.point.latitude),\(fix.point.longitude),\(fix.altitude),"
                    + "\(fix.horizontalAccuracy),\(fix.verticalAccuracy),\(fix.speed),\(fix.speedAccuracy),"
                    + "\(fix.course)\n",
                flushInterval: flushInterval)
        }
    }

    func appendBackfill(time: TimeInterval, x: Double, y: Double, z: Double) {
        queue.sync { backfill.append("\(time),\(x),\(y),\(z)\n", flushInterval: flushInterval) }
    }

    func snapshotIMUStats() -> StreamStats {
        queue.sync { imuStats }
    }

    func flush() {
        queue.sync {
            gps.flush()
            imu.flush()
            backfill.flush()
        }
    }

    func close() {
        queue.sync {
            gps.close()
            imu.close()
            backfill.close()
        }
    }
}

private struct BufferedFile {
    private let handle: FileHandle
    private var buffer = Data()
    private var lastFlush = Date()

    init(url: URL) throws {
        let manager = FileManager.default
        if !manager.fileExists(atPath: url.path) {
            // Default class: writable while locked, after first unlock (A3 §2 file protection).
            manager.createFile(
                atPath: url.path, contents: nil,
                attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        }
        handle = try FileHandle(forWritingTo: url)
        try handle.seekToEnd()
    }

    mutating func append(_ line: String, flushInterval: TimeInterval) {
        buffer.append(contentsOf: line.utf8)
        if buffer.count >= 64 * 1024 || Date().timeIntervalSince(lastFlush) >= flushInterval { flush() }
    }

    mutating func flush() {
        lastFlush = Date()
        guard !buffer.isEmpty else { return }
        do {
            try handle.write(contentsOf: buffer)
            buffer.removeAll(keepingCapacity: true)
        } catch {
            NSLog("TripWriter flush failed: \(error)")
        }
    }

    mutating func close() {
        flush()
        try? handle.close()
    }
}
