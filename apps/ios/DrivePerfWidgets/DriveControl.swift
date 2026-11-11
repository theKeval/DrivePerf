import SwiftUI
import WidgetKit

struct DriveControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: DriveWidgetSnapshot.controlKind, provider: Provider()) { snapshot in
            let action = snapshot.action
            ControlWidgetButton(action: action.controlIntent) {
                Label(action.title, systemImage: action.symbol)
            } actionLabel: { _ in
                switch action {
                case .start: Text("DrivePerf")
                case .showLive: Text("For passengers, or before you set off")
                case .stop:
                    if let start = snapshot.recording?.startedAt {
                        Text("Recording · \(max(0, Int(Date().timeIntervalSince(start) / 60))) min")
                    } else {
                        Text("Recording")
                    }
                case .fixLocation: Text("Location needed")
                }
            }
        }
        .displayName("DrivePerf")
        .description("Start a drive, show its Live Activity, or stop recording.")
    }

    private struct Provider: ControlValueProvider {
        let previewValue = DriveWidgetSnapshot()
        func currentValue() async throws -> DriveWidgetSnapshot { .read() }
    }
}

extension DriveWidgetSnapshot.Action {
    var title: LocalizedStringKey {
        switch self {
        case .start: "Start drive"
        case .showLive: "Show live drive"
        case .stop: "Stop drive"
        case .fixLocation: "Fix location"
        }
    }

    var symbol: String {
        switch self {
        case .start: "record.circle"
        case .showLive: "waveform"
        case .stop: "stop.fill"
        case .fixLocation: "location.slash"
        }
    }

    fileprivate var controlIntent: StartTripIntent {
        switch self {
        case .start, .fixLocation: StartTripIntent(fromControlCenter: true)
        case .showLive(let id): StartTripIntent(tripID: id, fromControlCenter: true)
        case .stop(let id): StartTripIntent(tripID: id, fromControlCenter: true, stopsTrip: true)
        }
    }
}
