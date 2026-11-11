import CryptoKit
import DPSharedBridge
import Foundation

/// `TripFileStore` on the file system: `trips/<tripId>/<kind>.bin` under Application Support. Protection follows the
/// port: R0 and R1 are `completeUnlessOpen` (created and appended while the phone is locked, as long as the file stays
/// open), R0 is excluded from device backup, the preview is `completeUntilFirstUserAuthentication`. Bytes only: the
/// file format is the core's job.
///
/// Thread-safe. Append handles are opened read-write and stay open until the file is finished (`compress`, `write`,
/// `delete`, `deleteTrip`), because a `completeUnlessOpen` file cannot be reopened while the phone is locked.
public final class FileTripStore: TripFileStoreBridge, @unchecked Sendable {
    public static let defaultRoot: URL = FileManager.default
        .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("Capture", isDirectory: true)

    private let root: URL
    private let tripsRoot: URL
    private let sentinel: URL
    private let lock = NSLock()
    private var handles: [String: FileHandle] = [:]

    public init(root: URL = FileTripStore.defaultRoot) {
        self.root = root
        tripsRoot = root.appendingPathComponent("trips", isDirectory: true)
        sentinel = root.appendingPathComponent("unlock.sentinel")
    }

    /// True once the phone has been unlocked since boot. Decided by reading a sentinel file protected
    /// `completeUntilFirstUserAuthentication`, not by `UIApplication.isProtectedDataAvailable`, which is false on
    /// every lock (spike change 5). For the self-test and the app's readiness; the core learns it from `FileResult`.
    public func isUnlockedSinceBoot() -> Bool {
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

    // MARK: TripFileStoreBridge

    public func append(tripId: String, kind: TripFileKind, bytes: Data) -> FileResult {
        lock.withLock {
            do {
                let handle = try appendHandle(tripId: tripId, kind: kind)
                try handle.write(contentsOf: bytes)
                return .ok
            } catch {
                return Self.result(for: error)
            }
        }
    }

    public func sync(tripId: String, kind: TripFileKind) -> FileResult {
        lock.withLock {
            guard let handle = handles[key(tripId, kind)] else { return .ok }
            do {
                try handle.synchronize()
                return .ok
            } catch {
                return Self.result(for: error)
            }
        }
    }

    public func write(tripId: String, kind: TripFileKind, bytes: Data) -> FileResult {
        lock.withLock {
            closeHandle(tripId, kind)
            do {
                try FileManager.default.createDirectory(at: directory(for: tripId), withIntermediateDirectories: true)
                try? FileManager.default.removeItem(at: compressedURL(tripId, kind))
                let url = plainURL(tripId, kind)
                try bytes.write(to: url, options: [.atomic, Self.writingProtection(kind)])
                try Self.applyAttributes(to: url, kind: kind)
                return .ok
            } catch {
                return Self.result(for: error)
            }
        }
    }

    /// Reads through the open append handle when there is one: a trip that ends while the phone is locked can still
    /// read its `completeUnlessOpen` R0 that way, but not by reopening it. Without a handle (a relaunch while locked)
    /// this returns `unavailable` and leaves the plain file, so the caller can compress it after the next unlock.
    public func compress(tripId: String, kind: TripFileKind) -> FileResult {
        lock.withLock {
            defer { closeHandle(tripId, kind) }
            let plain = plainURL(tripId, kind)
            guard FileManager.default.fileExists(atPath: plain.path) else {
                return FileManager.default.fileExists(atPath: compressedURL(tripId, kind).path) ? .ok : .ioError
            }
            let target = compressedURL(tripId, kind)
            do {
                let bytes: Data
                if let handle = handles[key(tripId, kind)] {
                    try handle.synchronize()
                    try handle.seek(toOffset: 0)
                    bytes = try handle.readToEnd() ?? Data()
                } else {
                    bytes = try Data(contentsOf: plain)
                }
                try Gzip.compress(bytes).write(to: target, options: [.atomic, Self.writingProtection(kind)])
                try Self.applyAttributes(to: target, kind: kind)
                try FileManager.default.removeItem(at: plain)
                return .ok
            } catch {
                // Never leave both copies: `read` prefers the plain file, and a later retry starts again from it.
                try? FileManager.default.removeItem(at: target)
                return Self.result(for: error)
            }
        }
    }

    public func read(tripId: String, kind: TripFileKind) -> Data? {
        lock.withLock {
            if let plain = try? Data(contentsOf: plainURL(tripId, kind)) { return plain }
            guard let gzipped = try? Data(contentsOf: compressedURL(tripId, kind)) else { return nil }
            return try? Gzip.decompress(gzipped)
        }
    }

    public func describe(tripId: String, kind: TripFileKind, withSha256: Bool) -> StoredFile? {
        lock.withLock {
            let plain = plainURL(tripId, kind)
            let gzipped = compressedURL(tripId, kind)
            let manager = FileManager.default
            let compressed = !manager.fileExists(atPath: plain.path) && manager.fileExists(atPath: gzipped.path)
            let url = compressed ? gzipped : plain
            handles[key(tripId, kind)].map { try? $0.synchronize() }
            guard let size = (try? manager.attributesOfItem(atPath: url.path))?[.size] as? NSNumber else { return nil }
            return StoredFile(
                sizeBytes: size.int64Value, sha256: withSha256 ? Self.sha256(of: url) : nil, compressed: compressed)
        }
    }

    public func delete(tripId: String, kind: TripFileKind) -> FileResult {
        lock.withLock {
            closeHandle(tripId, kind)
            do {
                for url in [plainURL(tripId, kind), compressedURL(tripId, kind)]
                where FileManager.default.fileExists(atPath: url.path) {
                    try FileManager.default.removeItem(at: url)
                }
                return .ok
            } catch {
                return Self.result(for: error)
            }
        }
    }

    public func deleteTrip(tripId: String) -> FileResult {
        lock.withLock {
            TripFileKind.allCases.forEach { closeHandle(tripId, $0) }
            let folder = directory(for: tripId)
            guard FileManager.default.fileExists(atPath: folder.path) else { return .ok }
            do {
                try FileManager.default.removeItem(at: folder)
                return .ok
            } catch {
                return Self.result(for: error)
            }
        }
    }

    public func tripIds() -> [String] {
        let folders = (try? FileManager.default.contentsOfDirectory(
            at: tripsRoot, includingPropertiesForKeys: [.isDirectoryKey])) ?? []
        return folders
            .filter { (try? $0.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true }
            .map(\.lastPathComponent)
            .sorted()
    }

    public func freeSpaceBytes() -> Int64 {
        let values = try? root.deletingLastPathComponent()
            .resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        return values?.volumeAvailableCapacityForImportantUsage ?? 0
    }

    // MARK: Files

    func directory(for tripId: String) -> URL {
        tripsRoot.appendingPathComponent(tripId, isDirectory: true)
    }

    private func plainURL(_ tripId: String, _ kind: TripFileKind) -> URL {
        directory(for: tripId).appendingPathComponent("\(kind.rawValue).bin")
    }

    private func compressedURL(_ tripId: String, _ kind: TripFileKind) -> URL {
        directory(for: tripId).appendingPathComponent("\(kind.rawValue).bin.gz")
    }

    private func key(_ tripId: String, _ kind: TripFileKind) -> String { "\(tripId)/\(kind.rawValue)" }

    /// Must hold `lock`.
    private func appendHandle(tripId: String, kind: TripFileKind) throws -> FileHandle {
        if let handle = handles[key(tripId, kind)] { return handle }
        let manager = FileManager.default
        try manager.createDirectory(at: directory(for: tripId), withIntermediateDirectories: true)
        let url = plainURL(tripId, kind)
        if !manager.fileExists(atPath: url.path) {
            guard manager.createFile(atPath: url.path, contents: nil, attributes: [.protectionKey: Self.protection(kind)])
            else { throw CocoaError(.fileWriteUnknown) }
            try Self.applyAttributes(to: url, kind: kind)
        }
        // Read and write, so `compress` can read the file through this handle while the phone is locked.
        let handle = try FileHandle(forUpdating: url)
        try handle.seekToEnd()
        handles[key(tripId, kind)] = handle
        return handle
    }

    /// Must hold `lock`.
    private func closeHandle(_ tripId: String, _ kind: TripFileKind) {
        guard let handle = handles.removeValue(forKey: key(tripId, kind)) else { return }
        try? handle.synchronize()
        try? handle.close()
    }

    public static func protection(_ kind: TripFileKind) -> FileProtectionType {
        kind == .preview ? .completeUntilFirstUserAuthentication : .completeUnlessOpen
    }

    private static func writingProtection(_ kind: TripFileKind) -> Data.WritingOptions {
        kind == .preview ? .completeFileProtectionUntilFirstUserAuthentication : .completeFileProtectionUnlessOpen
    }

    private static func applyAttributes(to url: URL, kind: TripFileKind) throws {
        guard kind == .r0 else { return }
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var target = url
        try target.setResourceValues(values)
    }

    private static func sha256(of url: URL) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        var hasher = SHA256()
        while let chunk = try? handle.read(upToCount: 1 << 20), !chunk.isEmpty {
            hasher.update(data: chunk)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    /// Error → port code. Data protection refusals surface as permission errors.
    public static func result(for error: any Error) -> FileResult {
        let nsError = error as NSError
        if nsError.domain == NSCocoaErrorDomain {
            switch nsError.code {
            case CocoaError.fileWriteOutOfSpace.rawValue: return .diskFull
            case CocoaError.fileReadNoPermission.rawValue, CocoaError.fileWriteNoPermission.rawValue: return .unavailable
            default: break
            }
            if let underlying = nsError.userInfo[NSUnderlyingErrorKey] as? NSError {
                return result(for: underlying)
            }
        }
        if nsError.domain == NSPOSIXErrorDomain {
            switch Int32(nsError.code) {
            case ENOSPC: return .diskFull
            case EPERM, EACCES: return .unavailable
            default: break
            }
        }
        return .ioError
    }
}
