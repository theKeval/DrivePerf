import DPSharedBridge
import Foundation

/// `TripFileStore` on the file system: one folder per trip under Application Support, every file protected with
/// `completeUntilFirstUserAuthentication`, so capture works on a locked phone once it has been unlocked since boot
/// (A3 §2). Trip files never leave the phone in this shell.
///
/// The format is an interim CSV capture log (one file per stream). The R0 codec (Wire protobuf) belongs to
/// `:shared:data:local` (DRIVE-62); this store is replaced by that adapter when it lands.
public final class FileTripStore: TripFileStoreBridge, @unchecked Sendable {
    public static let defaultRoot: URL = FileManager.default
        .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("Capture", isDirectory: true)

    private let root: URL
    private let tripsRoot: URL
    private let sentinel: URL

    public init(root: URL = FileTripStore.defaultRoot) {
        self.root = root
        tripsRoot = root.appendingPathComponent("trips", isDirectory: true)
        sentinel = root.appendingPathComponent("unlock.sentinel")
    }

    /// Reads a sentinel file with the same protection class as the trip files. It is unreadable only before the
    /// first unlock after a reboot, unlike `UIApplication.isProtectedDataAvailable`, which is false on every lock
    /// (spike change 5).
    public func isWritable() -> Bool {
        let manager = FileManager.default
        if manager.fileExists(atPath: sentinel.path) {
            return (try? Data(contentsOf: sentinel)) != nil
        }
        do {
            try manager.createDirectory(at: root, withIntermediateDirectories: true)
            try Data([1]).write(to: sentinel, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            return true
        } catch {
            return false
        }
    }

    public func open(tripId: String) throws(TripFileError) -> any TripFileWriter {
        guard isWritable() else { throw .storageLocked }
        do {
            return try CSVTripWriter(directory: directory(for: tripId))
        } catch {
            throw .writeFailed
        }
    }

    public func delete(tripId: String) {
        try? FileManager.default.removeItem(at: directory(for: tripId))
    }

    public func sizeBytes(tripId: String) -> Int64 {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: directory(for: tripId), includingPropertiesForKeys: [.fileSizeKey])) ?? []
        return files.reduce(0) { total, url in
            total + Int64((try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
        }
    }

    func directory(for tripId: String) -> URL {
        tripsRoot.appendingPathComponent(tripId, isDirectory: true)
    }
}

/// Appends samples on one serial queue; motion arrives from Core Motion's queue, fixes from the main actor.
final class CSVTripWriter: TripFileWriter, @unchecked Sendable {
    private let queue = DispatchQueue(label: "com.driveperf.tripwriter", qos: .userInitiated)
    private var location: BufferedFile
    private var motion: BufferedFile
    private var activity: BufferedFile
    private var sampleCounts = SampleCounts()
    private var closed = false

    init(directory: URL) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        location = try BufferedFile(
            url: directory.appendingPathComponent("location.csv"),
            header: "t_mono_ns,wall_ms,lat,lng,alt_m,h_acc_m,v_acc_m,speed_mps,speed_acc_mps,course_deg,course_acc_deg\n")
        motion = try BufferedFile(
            url: directory.appendingPathComponent("motion.csv"),
            header: "t_mono_ns,ax_g,ay_g,az_g,gx,gy,gz,rx_dps,ry_dps,rz_dps,qx,qy,qz,qw\n")
        activity = try BufferedFile(
            url: directory.appendingPathComponent("activity.csv"), header: "start_wall_ms,kind,confidence\n")
    }

    var counts: SampleCounts { queue.sync { sampleCounts } }

    func append(_ sample: LocationSample) {
        queue.async { [self] in
            guard !closed else { return }
            let s = sample
            location.append(
                "\(s.tMonoNs),\(s.wallMs),\(s.lat),\(s.lng),\(f(s.altM)),\(f(s.hAccM)),\(f(s.vAccM)),"
                    + "\(f(s.speedMps)),\(f(s.speedAccMps)),\(f(s.courseDeg)),\(f(s.courseAccDeg))\n")
            sampleCounts.location += 1
        }
    }

    func append(_ sample: MotionSample) {
        queue.async { [self] in
            guard !closed else { return }
            let a = sample.userAccelG
            let g = sample.gravityUp
            let r = sample.rotRateDps
            let q = sample.attitude
            motion.append(
                "\(sample.tMonoNs),\(a.x),\(a.y),\(a.z),\(g.x),\(g.y),\(g.z),\(r.x),\(r.y),\(r.z),"
                    + "\(f(q?.x)),\(f(q?.y)),\(f(q?.z)),\(f(q?.w))\n")
            sampleCounts.motion += 1
        }
    }

    func append(_ sample: ActivitySample) {
        queue.async { [self] in
            guard !closed else { return }
            activity.append("\(sample.startWallMs),\(sample.kind.rawValue),\(sample.confidence.rawValue)\n")
            sampleCounts.activity += 1
        }
    }

    func flush() {
        queue.sync {
            location.flush()
            motion.flush()
            activity.flush()
        }
    }

    func close() {
        queue.sync {
            guard !closed else { return }
            closed = true
            location.close()
            motion.close()
            activity.close()
        }
    }

    private func f(_ value: Double?) -> String { value.map { "\($0)" } ?? "" }
}

private struct BufferedFile {
    private static let flushBytes = 64 * 1024
    /// ≤ 5 s between fsyncs (§0.2); 2 s like the spike.
    private static let flushInterval: TimeInterval = 2

    private let handle: FileHandle
    private var buffer = Data()
    private var lastFlush = Date()

    init(url: URL, header: String) throws {
        let manager = FileManager.default
        if !manager.fileExists(atPath: url.path) {
            manager.createFile(
                atPath: url.path, contents: Data(header.utf8),
                attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        }
        handle = try FileHandle(forWritingTo: url)
        try handle.seekToEnd()
    }

    mutating func append(_ line: String) {
        buffer.append(contentsOf: line.utf8)
        if buffer.count >= Self.flushBytes || Date().timeIntervalSince(lastFlush) >= Self.flushInterval { flush() }
    }

    mutating func flush() {
        lastFlush = Date()
        guard !buffer.isEmpty else { return }
        do {
            try handle.write(contentsOf: buffer)
            try handle.synchronize()
            buffer.removeAll(keepingCapacity: true)
        } catch {
            // The next flush retries; the buffer is kept.
        }
    }

    mutating func close() {
        flush()
        try? handle.close()
    }
}
