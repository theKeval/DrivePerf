import DPSharedBridge
import Foundation
import UIKit

/// `PowerState` on UIKit and `ProcessInfo`: battery level and charging, Low Power Mode, thermal state. `current()` is
/// synchronous from any thread, so the battery values (main-thread UIKit) are cached and refreshed on change.
public final class SystemPowerState: PowerStateBridge, @unchecked Sendable {
    private let lock = NSLock()
    private var battery: (pct: Int?, charging: Bool) = (nil, false)
    private var sink: (@Sendable (PowerStatus) -> Void)?
    private var observers: [NSObjectProtocol] = []

    @MainActor
    public init() {
        UIDevice.current.isBatteryMonitoringEnabled = true
        readBattery()
    }

    public func current() -> PowerStatus {
        let battery = lock.withLock { self.battery }
        let info = ProcessInfo.processInfo
        return PowerStatus(
            batteryPct: battery.pct, isCharging: battery.charging, lowPowerMode: info.isLowPowerModeEnabled,
            thermal: Self.thermal(info.thermalState))
    }

    public func start(sink: @escaping @Sendable (PowerStatus) -> Void) {
        lock.withLock { self.sink = sink }
        onMain { [weak self] in self?.observe() }
    }

    public func stop() {
        lock.withLock { sink = nil }
        onMain { [weak self] in
            guard let self else { return }
            self.observers.forEach(NotificationCenter.default.removeObserver)
            self.observers = []
        }
    }

    @MainActor
    private func observe() {
        guard observers.isEmpty else { return }
        let center = NotificationCenter.default
        let names: [Notification.Name] = [
            UIDevice.batteryLevelDidChangeNotification, UIDevice.batteryStateDidChangeNotification,
            .NSProcessInfoPowerStateDidChange, ProcessInfo.thermalStateDidChangeNotification,
        ]
        observers = names.map { name in
            center.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.changed() }
            }
        }
    }

    @MainActor
    private func changed() {
        readBattery()
        let status = current()
        lock.withLock { sink }?(status)
    }

    @MainActor
    private func readBattery() {
        let device = UIDevice.current
        let reading = Self.batteryReading(level: device.batteryLevel, state: device.batteryState)
        lock.withLock { battery = reading }
    }

    /// `UIDevice` reports -1 when the level is unknown (the simulator, monitoring off); that becomes nil, not 0 %,
    /// so the core never mistakes it for a nearly empty battery (F-CAP-16 backs off at 15 % or less).
    static func batteryReading(level: Float, state: UIDevice.BatteryState) -> (pct: Int?, charging: Bool) {
        let pct = level < 0 ? nil : min(100, Int((level * 100).rounded()))
        return (pct, state == .charging || state == .full)
    }

    static func thermal(_ state: ProcessInfo.ThermalState) -> ThermalState {
        switch state {
        case .nominal: .nominal
        case .fair: .fair
        case .serious: .serious
        case .critical: .critical
        @unknown default: .serious
        }
    }
}
