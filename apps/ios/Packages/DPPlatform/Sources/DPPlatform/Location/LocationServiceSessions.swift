import CoreLocation
import Foundation

/// Holds the app's `CLServiceSession` (iOS 18+): it declares the authorization the app needs (Always while
/// automatic trips are on, When In Use otherwise) so Core Location keeps delivering to a background-relaunched app.
/// Created first thing at launch (ADR-002 §5), recreated when the automatic-trips choice changes.
@MainActor
public final class LocationServiceSessions {
    public enum Diagnostic: String, Sendable {
        case none, authorizationDenied, alwaysDenied, insufficientlyInUse, fullAccuracyDenied
    }

    public private(set) var diagnostic: Diagnostic = .none
    private var session: CLServiceSession?
    private var diagnosticsTask: Task<Void, Never>?
    private var wantsAlways = false

    public init() {}

    public func hold(always: Bool) {
        guard session == nil || always != wantsAlways else { return }
        wantsAlways = always
        diagnosticsTask?.cancel()
        session?.invalidate()
        let session = CLServiceSession(authorization: always ? .always : .whenInUse)
        self.session = session
        diagnosticsTask = Task { [weak self] in
            do {
                for try await diagnostic in session.diagnostics {
                    self?.diagnostic = Self.map(diagnostic)
                }
            } catch {
                self?.diagnostic = .authorizationDenied
            }
        }
    }

    public func release() {
        diagnosticsTask?.cancel()
        diagnosticsTask = nil
        session?.invalidate()
        session = nil
    }

    private static func map(_ diagnostic: CLServiceSession.Diagnostic) -> Diagnostic {
        if diagnostic.authorizationDenied { return .authorizationDenied }
        if diagnostic.alwaysAuthorizationDenied { return .alwaysDenied }
        if diagnostic.insufficientlyInUse { return .insufficientlyInUse }
        if diagnostic.fullAccuracyDenied { return .fullAccuracyDenied }
        return .none
    }
}
