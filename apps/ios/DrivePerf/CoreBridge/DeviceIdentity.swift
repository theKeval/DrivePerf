import Foundation
import Security

/// This install's device ID (data-model.md §1.4): a UUIDv7 kept in the Keychain as `ThisDeviceOnly`, so it does not
/// move to a new phone through a backup (§2.7). Created on first use.
enum DeviceIdentity {
    private static let service = "com.driveperf.device"
    private static let account = "deviceId"

    static func current() -> String {
        if let stored = read() { return stored }
        let id = uuidV7()
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: Data(id.utf8),
        ]
        SecItemAdd(query as CFDictionary, nil)
        return read() ?? id
    }

    /// RFC 9562 version 7: 48-bit Unix milliseconds, then random bits (data-model.md P3).
    static func uuidV7(now: Date = Date()) -> String {
        var b = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, b.count, &b)
        let ms = UInt64(now.timeIntervalSince1970 * 1000)
        for i in 0..<6 { b[i] = UInt8(truncatingIfNeeded: ms >> (8 * UInt64(5 - i))) }
        b[6] = (b[6] & 0x0F) | 0x70
        b[8] = (b[8] & 0x3F) | 0x80
        let uuid = UUID(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
        return uuid.uuidString.lowercased()
    }

    private static func read() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }
}
