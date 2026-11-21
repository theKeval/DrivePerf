import CoreLocation
import Foundation

/// Holds the app's `CLServiceSession` (iOS 18+): it declares the authorization the app needs (Always while
/// automatic trips are on, When In Use otherwise) so Core Location keeps delivering to a background-relaunched app.
/// Created first thing at launch (ADR-002 §5), recreated when the automatic-trips choice changes.
///
/// DRIVE-222: a session only lets the app start background location if it was started while the app was in use; one
/// created during a background launch reports `insufficientlyInUse`. So a session created in the background is
/// recreated the next time `hold` runs in the foreground, and every diagnostic change goes to the diagnostics log.
@MainActor
public final class LocationServiceSessions {
    public enum Diagnostic: String, Sendable {
        case none, authorizationDenied, alwaysDenied, insufficientlyInUse, fullAccuracyDenied
    }

    public private(set) var diagnostic: Diagnostic = .none
    private let log: LocationDiagnosticsLog?
    private var session: CLServiceSession?
    private var diagnosticsTask: Task<Void, Never>?
    private var wantsAlways = false
    private var createdInUse = false

    public init(log: LocationDiagnosticsLog? = nil) {
        self.log = log
    }

    /// Holds a session for `always` (or When In Use). `runState` is where the app is now: a session created in the
    /// background is replaced once the app is in use.
    public func hold(always: Bool, runState: AppRunState = .current) {
        guard Self.needsNewSession(
            hasSession: session != nil, wantsAlways: wantsAlways, always: always, createdInUse: createdInUse,
            inUse: runState.isInUse)
        else { return }
        wantsAlways = always
        createdInUse = runState.isInUse
        diagnosticsTask?.cancel()
        // The new session first, so the app never holds none while it replaces one.
        let previous = session
        let session = CLServiceSession(authorization: always ? .always : .whenInUse)
        self.session = session
        previous?.invalidate()
        let authorization = always ? "always" : "whenInUse"
        log?.record("service_session", ["authorization": authorization, "app": runState.rawValue])
        diagnosticsTask = Task { [weak self, log] in
            var last: String?
            do {
                for try await diagnostic in session.diagnostics {
                    self?.diagnostic = Self.map(diagnostic)
                    let key = LocationDiagnosticKey.key(diagnostic)
                    guard key != last else { continue }
                    last = key
                    log?.record(
                        "service_session_diagnostic",
                        ["authorization": authorization, "diagnostic": key, "app": AppRunState.current.rawValue])
                }
            } catch {
                self?.diagnostic = .authorizationDenied
                log?.record("service_session_diagnostic", ["authorization": authorization, "diagnostic": "streamError"])
            }
        }
    }

    public func release() {
        diagnosticsTask?.cancel()
        diagnosticsTask = nil
        session?.invalidate()
        session = nil
        createdInUse = false
    }

    /// Pure, so it is unit-tested.
    nonisolated static func needsNewSession(
        hasSession: Bool, wantsAlways: Bool, always: Bool, createdInUse: Bool, inUse: Bool
    ) -> Bool {
        !hasSession || always != wantsAlways || (inUse && !createdInUse)
    }

    private static func map(_ diagnostic: CLServiceSession.Diagnostic) -> Diagnostic {
        if diagnostic.authorizationDenied { return .authorizationDenied }
        if diagnostic.alwaysAuthorizationDenied { return .alwaysDenied }
        if diagnostic.insufficientlyInUse { return .insufficientlyInUse }
        if diagnostic.fullAccuracyDenied { return .fullAccuracyDenied }
        return .none
    }
}
