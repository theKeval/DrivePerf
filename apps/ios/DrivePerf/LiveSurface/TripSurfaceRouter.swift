import DPDesignSystem
import DPFeatures
import DPPlatform
import Observation
import SwiftUI
import UserNotifications

enum TripNotificationRoute: Equatable {
    case recording(String)
    case decoded(String)
    /// "Auto-detect may be paused" (F-CAP-14, S-LIVE-10): opens the capture status sheet.
    case watchdog

    init?(actionID: String, requestID: String, category: String, tripID: String) {
        guard actionID == UNNotificationDefaultActionIdentifier else { return nil }
        if requestID == AutoDetectWatchdog.identifier {
            self = .watchdog
            return
        }
        guard !tripID.isEmpty, requestID == LocalNotifier.identifier(for: tripID) else { return nil }
        switch category {
        case LocalNotifier.recordingCategory, "":
            // Recording notices delivered before categories were added have the same identifier and thread.
            self = .recording(tripID)
        case LocalNotifier.decodedCategory:
            self = .decoded(tripID)
        default:
            return nil
        }
    }
}

@MainActor
@Observable
final class TripSurfaceRouter {
    private(set) var rootRevision = 0
    var isLocationHelpPresented = false
    /// Opens the capture status sheet from a notification (the watchdog).
    let statusRequests = CaptureStatusRequests()

    func showLocationHelp() { isLocationHelpPresented = true }

    func open(_ route: TripNotificationRoute, recorder: CoreTripRecorder) async {
        switch route {
        case .recording(let tripID):
            guard await TripIntentHandler.waitForActiveTrip(recorder), recorder.status.tripId == tripID else { return }
        case .decoded:
            // The shell opens on Drives. Its existing capture gate takes precedence during another drive.
            break
        case .watchdog:
            // The app was opened, so auto-detect re-arms (a check-in); the sheet shows what, if anything, to fix.
            AppEnvironment.shared.refreshCapture()
            statusRequests.request()
            return
        }
        isLocationHelpPresented = false
        // Re-enter the shell at its root so an existing sheet cannot cover the in-drive screen.
        rootRevision += 1
    }
}

struct TripSurfacePresentation: View {
    @Bindable var router: TripSurfaceRouter

    let dependencies: AppDependencies

    var body: some View {
        AppRootView(dependencies: dependencies)
            .id(router.rootRevision)
            .sheet(isPresented: $router.isLocationHelpPresented) {
                VStack(alignment: .leading, spacing: DPSpace.s4) {
                    HStack(spacing: DPSpace.s3) {
                        GlyphDisc(.gps, tone: .warning, size: 36)
                        Text("DrivePerf needs location to record")
                            .font(DPFont.sheetTitle)
                    }
                    Text("Without it, DrivePerf can’t draw a route or measure a drive, so nothing records, not even manual trips.")
                        .font(DPFont.body).foregroundStyle(DPColor.text2)
                    DPPrimaryButton("Open Settings", icon: .gear) {
                        AppEnvironment.shared.permissions.openSystemSettings()
                    }
                    DPSecondaryButton("Not now") { router.isLocationHelpPresented = false }
                }
                .padding(DPSpace.s6)
                .presentationDetents([.medium])
            }
    }
}
