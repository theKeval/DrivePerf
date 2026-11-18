import DPSharedBridge
import Foundation
import Network

/// `ConnectivityMonitoring` over `NWPathMonitor`: Replay draws the plain canvas while there is no network path
/// (F-RPL-01 AC5, EC-NET). MapKit does not report missing tiles to SwiftUI, so no path stands for "no tiles".
@MainActor
public final class NetworkConnectivity: ConnectivityMonitoring {
    public init() {}

    public func onlineUpdates() -> AsyncStream<Bool> {
        AsyncStream { continuation in
            let monitor = NWPathMonitor()
            monitor.pathUpdateHandler = { path in continuation.yield(path.status == .satisfied) }
            continuation.onTermination = { _ in monitor.cancel() }
            monitor.start(queue: DispatchQueue(label: "com.driveperf.connectivity"))
        }
    }
}
