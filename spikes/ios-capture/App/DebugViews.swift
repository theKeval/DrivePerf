import CaptureCore
import CoreLocation
import SwiftUI

// Debug UI only. It deliberately shows distances and times, never coordinates or a map,
// so a screenshot of it is safe to share.

struct RootView: View {
    let engine: CaptureEngine

    var body: some View {
        TabView {
            StatusView(engine: engine).tabItem { Label("Status", systemImage: "gauge") }
            TripsView(engine: engine).tabItem { Label("Trips", systemImage: "car") }
            WakeLogView().tabItem { Label("Wakes", systemImage: "list.bullet") }
        }
    }
}

struct StatusView: View {
    @Bindable var engine: CaptureEngine

    var body: some View {
        NavigationStack {
            List {
                Section("Capture") {
                    row("Phase", engine.phase.rawValue)
                    row("Last trigger", engine.lastEvent)
                    row("Parked region armed", engine.parkedRegionArmed ? "yes" : "no")
                    row("Service session", engine.sessionDiagnostic)
                    Button(engine.active == nil ? "Start manual drive" : "Stop recording") {
                        Task { await engine.toggleManual(source: "app") }
                    }
                }
                Section("Health") {
                    row("Location", locationText)
                    row("Precise", engine.fullAccuracy ? "yes" : "NO")
                    row("Motion", engine.motionAuthorized == .authorized ? "granted" : "NOT granted")
                    row("Notifications", engine.notificationsAuthorized ? "granted" : "NOT granted")
                    row("Low Power Mode", engine.lowPowerMode ? "ON" : "off")
                    Button("Grant permissions") { engine.requestPermissions() }
                }
                Section("Personal Team profile") {
                    ForEach(Array(engine.installs.enumerated()), id: \.offset) { index, install in
                        row(
                            "Install \(index + 1)",
                            install.firstLaunchAt.formatted(date: .abbreviated, time: .shortened))
                        if let expiry = install.profileExpiresAt {
                            row("Expires", expiry.formatted(date: .abbreviated, time: .shortened))
                        }
                    }
                }
                Section("Experiment") {
                    Toggle("Legacy GPS keep-alive", isOn: $engine.legacyKeeperEnabled)
                    Button("Test notification in 60 s") { engine.scheduleWatchdogTest() }
                }
            }
            .navigationTitle("Capture Spike")
        }
    }

    private var locationText: String {
        switch engine.authorization {
        case .authorizedAlways: "Always"
        case .authorizedWhenInUse: "While Using (NEEDS Always)"
        case .denied: "Denied"
        case .restricted: "Restricted"
        case .notDetermined: "Not asked"
        @unknown default: "Unknown"
        }
    }

    private func row(_ title: String, _ value: String) -> some View {
        LabeledContent(title, value: value)
    }
}

struct TripsView: View {
    let engine: CaptureEngine

    var body: some View {
        NavigationStack {
            List(engine.trips) { trip in
                NavigationLink {
                    TripDetailView(trip: trip)
                } label: {
                    TripRow(trip: trip)
                }
            }
            .overlay {
                if engine.trips.isEmpty { ContentUnavailableView("No trips yet", systemImage: "car") }
            }
            .navigationTitle("Trips")
        }
    }
}

private struct TripRow: View {
    let trip: TripRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(trip.status.rawValue.capitalized).font(.headline)
                Spacer()
                Text(trip.wakeAt.formatted(date: .abbreviated, time: .shortened)).font(.caption)
            }
            Text("\(trip.wakeTrigger.rawValue) · \(trip.mode.rawValue)").font(.subheadline)
            if let metrics = trip.metrics {
                Text(Format.meters(metrics.distanceMeters) + " · start " + Format.meters(metrics.startLatencyMeters))
                    .font(.caption).foregroundStyle(.secondary)
            }
            if let rejection = trip.rejection {
                Text(rejection).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}

struct TripDetailView: View {
    let trip: TripRecord
    @State private var exports: [URL] = []
    @State private var exportError: String?

    var body: some View {
        List {
            Section("Wake") {
                row("Trigger", trip.wakeTrigger.rawValue)
                row("Launch", trip.launchKind.rawValue)
                row("App state", trip.appStateAtWake)
                row("Confirmed by", trip.confirmation ?? "-")
                row("Rejected", trip.rejection ?? "-")
                row("Relaunches", "\(trip.relaunchCount)")
                row("Low Power at start", trip.lowPowerModeAtStart ? "yes" : "no")
                row("Legacy keep-alive", trip.legacyKeeperEnabled ? "on" : "off")
                ForEach(Array(trip.triggers.enumerated()), id: \.offset) { _, event in
                    row(event.kind.rawValue, "+\(Format.seconds(event.at.timeIntervalSince(trip.wakeAt)))")
                }
            }
            if let metrics = trip.metrics {
                Section("Metrics") {
                    row("Distance", Format.meters(metrics.distanceMeters))
                    row("Duration", Format.seconds(metrics.durationSeconds))
                    row("Parking spot → first point", Format.meters(metrics.startLatencyMeters))
                    row("Start latency", Format.seconds(metrics.startLatencySeconds))
                    row("First fix after trigger", Format.seconds(metrics.firstFixAfterTriggerSeconds))
                    row("IMU samples/s", String(format: "%.1f", metrics.imuSamplesPerSecond))
                    row("GPS samples/s", String(format: "%.2f", metrics.gpsSamplesPerSecond))
                    row("IMU delivery", String(format: "%.1f%%", metrics.imuDelivery * 100))
                    row("GPS delivery", String(format: "%.1f%%", metrics.gpsDelivery * 100))
                    row("IMU gaps", "\(metrics.imuGaps) (longest \(Format.seconds(metrics.longestImuGapSeconds)))")
                    row("GPS gaps", "\(metrics.gpsGaps) (longest \(Format.seconds(metrics.longestGpsGapSeconds)))")
                    row("Battery delta", metrics.batteryDeltaPercent.map { String(format: "%.0f%%", $0) } ?? "-")
                    row("Battery %/h", metrics.batteryPercentPerHour.map { String(format: "%.1f", $0) } ?? "-")
                }
            }
            Section("Streams") {
                row("Legacy fixes", "\(trip.fixesLegacy)")
                row("liveUpdates fixes", "\(trip.fixesLiveUpdates)")
                row("Backfill samples", "\(trip.backfillSamples)")
                row("Backfill window", Format.seconds(trip.backfillWindowSeconds))
            }
            if !trip.notes.isEmpty {
                Section("Notes") {
                    ForEach(trip.notes, id: \.self) { Text($0).font(.caption) }
                }
            }
            Section("Export (raw real coordinates: never commit)") {
                Button("Prepare JSON and GPX") { prepareExport() }
                ForEach(exports, id: \.self) { url in
                    ShareLink(item: url) { Text(url.lastPathComponent).font(.caption) }
                }
                if let exportError { Text(exportError).foregroundStyle(.red) }
            }
        }
        .navigationTitle(trip.status.rawValue.capitalized)
    }

    private func prepareExport() {
        do {
            exports = try TripStore.export(trip)
            exportError = nil
        } catch {
            exportError = error.localizedDescription
        }
    }

    private func row(_ title: String, _ value: String) -> some View {
        LabeledContent(title, value: value)
    }
}

struct WakeLogView: View {
    @State private var entries: [WakeEntry] = []

    var body: some View {
        NavigationStack {
            List(Array(entries.suffix(300).reversed().enumerated()), id: \.offset) { _, entry in
                VStack(alignment: .leading) {
                    Text(entry.kind).font(.subheadline)
                    Text(entry.at.formatted(date: .abbreviated, time: .standard)).font(.caption2)
                    if !entry.detail.isEmpty { Text(entry.detail).font(.caption) }
                }
            }
            .navigationTitle("Wake log")
            .onAppear { entries = WakeLog.entries() }
            .refreshable { entries = WakeLog.entries() }
        }
    }
}

enum Format {
    static func meters(_ value: Double?) -> String {
        guard let value else { return "-" }
        return value >= 1000 ? String(format: "%.2f km", value / 1000) : String(format: "%.0f m", value)
    }

    static func seconds(_ value: Double?) -> String {
        guard let value else { return "-" }
        return String(format: "%.1f s", value)
    }
}
