import DPDesignSystem
import DPSharedBridge
import MapKit
import SwiftUI

// S-RPL-01 pins (DRIVE-34 F-RPL-03, DRIVE-203 F-RPL-02): event pins by shape (hollow when "Needed"), stop capsules and
// the neutral max-speed triangle. They are buttons in time order, then stops, then max speed; a selected pin grows
// 1.18× and the others dim to 50 %. Each event pin pulses once as the marker passes it.

/// Local data colours this story needs that the frozen token file lacks (values from the DRIVE-39 spec tokens).
enum ReplayDataColor {
    static let longG = Color(dark: 0x5AB8_F5FF, light: 0x0A6A_B8FF)
    static let latG = Color(dark: 0xF5A1_42FF, light: 0xB25A_00FF)
}

extension DrivingEventType {
    var glyph: DPEventGlyph.Kind {
        switch self {
        case .brake: .brake
        case .accel: .accel
        case .corner: .corner
        }
    }
}

/// The pins as MapKit annotations, so they follow pans and zooms natively.
struct ReplayPinContent: MapContent {
    let model: ReplayViewModel

    var body: some MapContent {
        if let overlay = model.overlay {
            ForEach(overlay.eventPins) { pin in
                if let lat = pin.lat, let lng = pin.lng {
                    Annotation(pin.accessibilityLabel, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng)) {
                        ReplayEventPinButton(model: model, pin: pin)
                    }
                    .annotationTitles(.hidden)
                }
            }
            ForEach(overlay.stopPins) { stop in
                if let lat = stop.lat, let lng = stop.lng {
                    Annotation("Stop \(stop.number)", coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng)) {
                        ReplayStopPinButton(model: model, stop: stop, count: overlay.stopCount)
                    }
                    .annotationTitles(.hidden)
                }
            }
            if let max = overlay.maxSpeed {
                Annotation("Max speed", coordinate: CLLocationCoordinate2D(latitude: max.lat, longitude: max.lng)) {
                    ReplayMaxSpeedPinButton(model: model)
                }
                .annotationTitles(.hidden)
            }
        }
    }
}

/// The same pins on the plain canvas, placed with its projection.
struct ReplayCanvasPins: View {
    let model: ReplayViewModel
    let project: (Double, Double) -> CGPoint?

    var body: some View {
        if let overlay = model.overlay {
            ZStack {
                ForEach(overlay.eventPins) { pin in
                    if let lat = pin.lat, let lng = pin.lng, let at = project(lat, lng) {
                        ReplayEventPinButton(model: model, pin: pin).position(at)
                    }
                }
                ForEach(overlay.stopPins) { stop in
                    if let lat = stop.lat, let lng = stop.lng, let at = project(lat, lng) {
                        ReplayStopPinButton(model: model, stop: stop, count: overlay.stopCount).position(at)
                    }
                }
                if let max = overlay.maxSpeed, let at = project(max.lat, max.lng) {
                    ReplayMaxSpeedPinButton(model: model).position(at)
                }
            }
        }
    }
}

private enum PinEmphasis {
    case normal, selected, dimmed

    init(_ selection: ReplaySelectionInfo?, mine: ReplaySelectionInfo) {
        self = selection == nil ? .normal : selection == mine ? .selected : .dimmed
    }

    var scale: CGFloat { self == .selected ? 1.18 : 1 }
    var opacity: Double { self == .dimmed ? 0.5 : 1 }
}

private struct PinTarget<Face: View>: View {
    let emphasis: PinEmphasis
    let label: String
    let identifier: String
    let action: () -> Void
    @ViewBuilder let face: () -> Face

    var body: some View {
        Button(action: action) {
            face()
                .frame(width: DPLayout.touchMin, height: DPLayout.touchMin)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .scaleEffect(emphasis.scale)
        .opacity(emphasis.opacity)
        .animation(.spring(duration: 0.25), value: emphasis.scale)
        .accessibilityLabel(label)
        .accessibilityIdentifier(identifier)
        .sensoryFeedback(.impact(weight: .light), trigger: emphasis == .selected)
    }
}

struct ReplayEventPinButton: View {
    let model: ReplayViewModel
    let pin: ReplayEventPinInfo

    var body: some View {
        PinTarget(
            emphasis: PinEmphasis(model.selection, mine: .event(pin.id)), label: pin.accessibilityLabel,
            identifier: "replay.eventPin", action: { model.selectEvent(pin.id) }
        ) {
            ZStack {
                ReplayPinPulse(kind: pin.kind, trigger: model.pulses[pin.id])
                DPEventGlyph(pin.kind.glyph, needed: pin.needed, size: 26)
            }
        }
    }
}

/// The pass pulse: ring scale 1 → 2.4 and opacity 0.75 → 0 over 600 ms, once (F-RPL-03 AC2). Reduced motion: a
/// 600 ms outline highlight with no scale. No sound, no haptic.
private struct ReplayPinPulse: View {
    let kind: DrivingEventType
    let trigger: Date?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private struct Value {
        var scale: CGFloat = 1
        var opacity: Double = 0
    }

    var body: some View {
        let color = DPEventGlyph.color(kind.glyph)
        Group {
            if reduceMotion {
                Circle().strokeBorder(color, lineWidth: 2).frame(width: 32, height: 32)
            } else {
                Circle().fill(color).frame(width: 26, height: 26)
            }
        }
        .keyframeAnimator(initialValue: Value(), trigger: trigger) { content, value in
            content.scaleEffect(reduceMotion ? 1 : value.scale).opacity(value.opacity)
        } keyframes: { _ in
            KeyframeTrack(\.scale) {
                LinearKeyframe(1, duration: 0)
                CubicKeyframe(2.4, duration: 0.6)
            }
            KeyframeTrack(\.opacity) {
                LinearKeyframe(trigger == nil ? 0 : 0.75, duration: 0)
                CubicKeyframe(0, duration: 0.6)
            }
        }
        .accessibilityHidden(true)
    }
}

struct ReplayStopPinButton: View {
    let model: ReplayViewModel
    let stop: ReplayStopPinInfo
    let count: Int

    var body: some View {
        let seconds = Int(stop.durationS.rounded())
        PinTarget(
            emphasis: PinEmphasis(model.selection, mine: .stop(stop.number)),
            label: "Stop \(stop.number) of \(count), stopped for \(seconds) seconds", identifier: "replay.stopPin",
            action: { model.selectStop(stop.number) }
        ) {
            ZStack {
                Capsule().fill(DPColor.text2).frame(width: 24, height: 18)
                    .overlay(Capsule().strokeBorder(DPColor.Data.pinRing, lineWidth: 1.5))
                HStack(spacing: 3) {
                    Capsule().fill(DPColor.Data.pinGlyph).frame(width: 2.2, height: 8)
                    Capsule().fill(DPColor.Data.pinGlyph).frame(width: 2.2, height: 8)
                }
            }
        }
    }
}

/// Neutral outline triangle, never animated or emphasised (D5/D6, motion.md §3.6).
struct ReplayMaxSpeedPinButton: View {
    let model: ReplayViewModel

    var body: some View {
        PinTarget(
            emphasis: PinEmphasis(model.selection, mine: .maxSpeed), label: "Max speed", identifier: "replay.maxSpeedPin",
            action: { model.selectMaxSpeed() }
        ) {
            ReplayTriangle()
                .fill(DPColor.Data.mapLand)
                .overlay(ReplayTriangle().stroke(DPColor.text, style: StrokeStyle(lineWidth: 2, lineJoin: .round)))
                .frame(width: 22, height: 20)
        }
    }
}

struct ReplayTriangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
