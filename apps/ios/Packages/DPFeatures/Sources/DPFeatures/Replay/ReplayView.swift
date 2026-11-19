import DPDesignSystem
import DPSharedBridge
import MapKit
import SwiftUI

/// S-RPL-01 Trip Replay (F-RPL-01) with the data-quality overlay (F-RPL-08, S-DQ-01 styles): the route on a MapKit map
/// (or a plain canvas offline), the marker on the display clock, play and pause, four speeds and a scrubber with the
/// overlay spans marked.
public struct ReplayView: View {
    @State private var model: ReplayViewModel
    let title: String
    let subtitle: String
    /// What VoiceOver says for the map: "Map of your drive, 39.0 km."
    let mapSummary: String
    @State private var isLegendPresented = false

    public init(model: ReplayViewModel, title: String, subtitle: String, mapSummary: String) {
        _model = State(initialValue: model)
        self.title = title
        self.subtitle = subtitle
        self.mapSummary = mapSummary
    }

    public var body: some View {
        ZStack {
            DPColor.Data.mapLand.ignoresSafeArea()
            switch model.phase {
            case .loading:
                ProgressView("Loading replay\u{2026}").font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            case .failed:
                ReplayMessageCard(
                    text: "Couldn\u{2019}t load this drive\u{2019}s detail. Your recording is safe.",
                    action: ("Try again", { Task { await model.load() } }))
            case .locked:
                ReplayMessageCard(
                    text: "Replay for drives older than 30 days is part of Pro. Nothing is deleted.\n"
                        + "Your summary, score and Story stay free forever.",
                    action: nil)
            case .ready:
                ready
            }
        }
        .navigationTitle(title)
        .navigationSubtitle(subtitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    isLegendPresented = true
                    model.legendOpened()
                } label: {
                    Image(dp: .info).resizable().scaledToFit().frame(width: 18, height: 18)
                }
                .accessibilityLabel("Map legend")
                .accessibilityIdentifier("replay.legend")
                .disabled(model.phase != .ready)
                .popover(isPresented: $isLegendPresented) {
                    ReplayLegend(styles: model.stylesPresent).presentationCompactAdaptation(.popover)
                }
            }
        }
        .task { await model.load() }
        .task { await model.observeConnectivity() }
    }

    private var ready: some View {
        ZStack(alignment: .top) {
            Group {
                if model.isOnline {
                    ReplayMapLayer(model: model)
                } else {
                    ReplayCanvasLayer(model: model)
                }
            }
            .ignoresSafeArea(edges: .bottom)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(mapSummary)
            // A sibling of the map, so VoiceOver reads the label and notes.
            ReplayChips(model: model)
        }
        .safeAreaInset(edge: .bottom) {
            ReplayPlaybackBar(model: model)
                .padding(.horizontal, DPLayout.screenMargin)
                .padding(.bottom, DPSpace.s2)
        }
        // AC4: the display clock drives playback; paused, it stops ticking.
        .background {
            TimelineView(.animation(minimumInterval: nil, paused: !model.isPlaying)) { context in
                Color.clear.onChange(of: context.date) { _, date in
                    model.tick(at: date.timeIntervalSinceReferenceDate)
                }
            }
        }
    }
}

/// The span label and notes over the map. Its own view: it reads the marker time, so only it redraws per frame.
private struct ReplayChips: View {
    let model: ReplayViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            if let label = model.spanLabel {
                ReplaySpanChip(style: model.activeSpan?.style ?? .observed, text: label)
                    .transition(.opacity)
            }
            if model.summaryOnly {
                ReplayNote(text: "Detailed data was removed to save space. This is the summary route.")
            }
            if !model.isOnline {
                ReplayNote(text: "Map unavailable offline. Your route and data are all here.")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, DPLayout.screenMargin)
        .padding(.top, DPSpace.s2)
        .animation(.easeInOut(duration: 0.2), value: model.spanLabel)
    }
}

// MARK: - Map (online)

/// The route on MapKit. Its content depends only on the segments, so marker movement never rebuilds the polylines;
/// the marker is an overlay positioned through the map proxy.
private struct ReplayMapLayer: View {
    let model: ReplayViewModel
    @State private var position: MapCameraPosition = .automatic
    @State private var camera: MapCamera?
    @State private var width: CGFloat = 402

    var body: some View {
        MapReader { proxy in
            Map(position: $position, interactionModes: [.pan, .zoom, .rotate]) {
                ReplayRouteContent(polylines: model.polylines)
            }
            .mapStyle(.standard(elevation: .flat, emphasis: .muted, pointsOfInterest: .excludingAll))
            .mapControls {}
            // The marker overlay reads the camera, so it follows pans and zooms.
            .onMapCameraChange(frequency: .continuous) { context in camera = context.camera }
            .onMapCameraChange(frequency: .onEnd) { context in
                model.cameraSettled(longitudeDelta: context.region.span.longitudeDelta, viewWidth: width)
            }
            .overlay {
                ReplayMarkerLayer(model: model, heading: camera?.heading ?? 0) { lat, lng in
                    proxy.convert(CLLocationCoordinate2D(latitude: lat, longitude: lng), to: .local)
                }
                .allowsHitTesting(false)
            }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        .onAppear { if let bounds = model.bounds { position = .region(Self.region(bounds)) } }
    }

    /// The route's box with room for the chips and the playback bar.
    static func region(_ b: ReplayBounds) -> MKCoordinateRegion {
        let center = CLLocationCoordinate2D(latitude: (b.minLat + b.maxLat) / 2, longitude: (b.minLng + b.maxLng) / 2)
        let span = MKCoordinateSpan(
            latitudeDelta: max((b.maxLat - b.minLat) * 1.6, 0.005),
            longitudeDelta: max((b.maxLng - b.minLng) * ReplayViewModel.fitPadding, 0.005))
        // Shift up a little: the playback bar covers the bottom of the map.
        return MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: center.latitude - span.latitudeDelta * 0.12, longitude: center.longitude),
            span: span)
    }
}

/// The route polylines per style (F-RPL-08): observed solid on a casing, estimated dashed at 60 %, no GPS a thin
/// dotted break, excluded dimmed.
private struct ReplayRouteContent: MapContent {
    let polylines: [ReplayPolyline]

    var body: some MapContent {
        ForEach(polylines) { line in
            switch line.style {
            case .observed:
                MapPolyline(coordinates: line.coordinates)
                    .stroke(DPColor.Data.routeCasing, style: ReplayStroke.casing)
                MapPolyline(coordinates: line.coordinates)
                    .stroke(DPColor.Data.route, style: ReplayStroke.route)
            case .estimated:
                MapPolyline(coordinates: line.coordinates)
                    .stroke(DPColor.Data.route.opacity(DPEstimated.opacity), style: ReplayStroke.estimated)
            case .noGps:
                MapPolyline(coordinates: line.coordinates)
                    .stroke(DPColor.Data.gapStroke, style: ReplayStroke.gap)
            case .excluded:
                MapPolyline(coordinates: line.coordinates)
                    .stroke(DPColor.Data.routeCasing.opacity(0.6), style: ReplayStroke.casing)
                MapPolyline(coordinates: line.coordinates)
                    .stroke(DPColor.Data.routeFaded, style: ReplayStroke.excluded)
            }
        }
        if let start = polylines.first?.coordinates.first {
            Annotation("Start", coordinate: start) {
                Circle().strokeBorder(DPColor.Data.route, lineWidth: 3).frame(width: 14, height: 14)
                    .background(Circle().fill(DPColor.Data.routeCasing))
            }
            .annotationTitles(.hidden)
        }
        if let end = polylines.last?.coordinates.last {
            Annotation("End", coordinate: end) {
                Circle().fill(DPColor.Data.route).frame(width: 10, height: 10)
                    .overlay(Circle().strokeBorder(DPColor.Data.routeCasing, lineWidth: 2))
            }
            .annotationTitles(.hidden)
        }
    }
}

/// Stroke styles shared by the map and the canvas (components.md map: route 3.8 on a 7.5 casing, estimated 4/4,
/// gap dotted 1/6).
enum ReplayStroke {
    static let route = StrokeStyle(lineWidth: 3.8, lineCap: .round, lineJoin: .round)
    static let casing = StrokeStyle(lineWidth: 7.5, lineCap: .round, lineJoin: .round)
    static let estimated = StrokeStyle(lineWidth: 3.8, lineCap: .butt, lineJoin: .round, dash: DPEstimated.dash)
    static let gap = StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round, dash: [1, 6])
    static let excluded = StrokeStyle(lineWidth: 3.8, lineCap: .round, lineJoin: .round)
}

// MARK: - Plain canvas (offline, AC5)

/// The same route, marker and labels projected from the bounding box onto a plain canvas, when there is no network.
private struct ReplayCanvasLayer: View {
    let model: ReplayViewModel

    var body: some View {
        GeometryReader { geometry in
            let projection = ReplayProjection(bounds: model.bounds, size: geometry.size)
            ZStack {
                Canvas { context, _ in
                    ReplayCanvasRoute.draw(model.segments, in: &context, projection: projection)
                }
                ReplayMarkerLayer(model: model, heading: 0) { lat, lng in projection.point(lat: lat, lng: lng) }
            }
        }
        .padding(.horizontal, DPLayout.screenMargin)
        .padding(.top, 72)
        .padding(.bottom, 200)
    }
}

/// Equirectangular projection of the bounding box into a size, aspect kept, north up.
struct ReplayProjection: Sendable {
    let bounds: ReplayBounds?
    let size: CGSize

    func point(lat: Double, lng: Double) -> CGPoint? {
        guard let b = bounds, size.width > 0, size.height > 0 else { return nil }
        let midLat = (b.minLat + b.maxLat) / 2 * .pi / 180
        let width = max((b.maxLng - b.minLng) * cos(midLat), 1e-9)
        let height = max(b.maxLat - b.minLat, 1e-9)
        let scale = min(size.width / width, size.height / height)
        let offsetX = (size.width - width * scale) / 2
        let offsetY = (size.height - height * scale) / 2
        return CGPoint(
            x: offsetX + (lng - b.minLng) * cos(midLat) * scale,
            y: offsetY + (b.maxLat - lat) * scale)
    }
}

enum ReplayCanvasRoute {
    static func draw(_ segments: [ReplaySegment], in context: inout GraphicsContext, projection: ReplayProjection) {
        for segment in segments {
            var path = Path()
            for (index, point) in segment.points.enumerated() {
                guard let p = projection.point(lat: point.lat, lng: point.lng) else { continue }
                index == 0 ? path.move(to: p) : path.addLine(to: p)
            }
            switch segment.style {
            case .observed:
                context.stroke(path, with: .color(DPColor.Data.routeCasing), style: ReplayStroke.casing)
                context.stroke(path, with: .color(DPColor.Data.route), style: ReplayStroke.route)
            case .estimated:
                context.stroke(path, with: .color(DPColor.Data.route.opacity(DPEstimated.opacity)), style: ReplayStroke.estimated)
            case .noGps:
                context.stroke(path, with: .color(DPColor.Data.gapStroke), style: ReplayStroke.gap)
            case .excluded:
                context.stroke(path, with: .color(DPColor.Data.routeFaded), style: ReplayStroke.excluded)
            }
        }
    }
}

// MARK: - Marker

/// The marker (S-RPL-01): 16 pt dot with a 3 pt route ring, a 36 pt halo at 20 % and an 18 pt heading wedge (40°). It
/// hides in a GPS gap.
private struct ReplayMarkerLayer: View {
    let model: ReplayViewModel
    let heading: Double
    let project: (Double, Double) -> CGPoint?

    var body: some View {
        GeometryReader { _ in
            if let frame = model.frame, !model.isMarkerHidden, let point = project(frame.lat, frame.lng) {
                ReplayMarker(bearing: frame.bearingDeg.map { $0 - heading })
                    .position(point)
            }
        }
    }
}

struct ReplayMarker: View {
    let bearing: Double?

    var body: some View {
        ZStack {
            Circle().fill(DPColor.Data.route.opacity(0.2)).frame(width: 36, height: 36)
            if let bearing {
                ReplayHeadingWedge()
                    .fill(DPColor.Data.route)
                    .frame(width: 36, height: 36)
                    .rotationEffect(.degrees(bearing))
            }
            Circle().fill(DPColor.Data.marker).frame(width: 16, height: 16)
                .overlay(Circle().strokeBorder(DPColor.Data.route, lineWidth: 3))
        }
        .accessibilityHidden(true)
    }
}

/// A 40° wedge pointing up, 18 pt from the centre.
struct ReplayHeadingWedge: Shape {
    func path(in rect: CGRect) -> Path {
        let c = CGPoint(x: rect.midX, y: rect.midY)
        let r: CGFloat = 18
        var path = Path()
        path.move(to: c)
        path.addArc(center: c, radius: r, startAngle: .degrees(-90 - 20), endAngle: .degrees(-90 + 20), clockwise: false)
        path.closeSubpath()
        return path
    }
}

// MARK: - Chips, notes, legend

private struct ReplaySpanChip: View {
    let style: ReplaySegmentStyle
    let text: String

    var body: some View {
        HStack(spacing: DPSpace.s1_5) {
            ReplayStyleSwatch(style: style).frame(width: 18, height: 8)
            Text(text).font(DPFont.caption.weight(.medium)).foregroundStyle(DPColor.text)
        }
        .padding(.horizontal, DPSpace.s3)
        .frame(minHeight: 28)
        .glassEffect(.regular, in: Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("replay.spanLabel")
    }
}

private struct ReplayNote: View {
    let text: String

    var body: some View {
        Text(text).font(DPFont.caption).foregroundStyle(DPColor.text)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, DPSpace.s3)
            .padding(.vertical, DPSpace.s2)
            .glassEffect(.regular, in: RoundedRectangle(cornerRadius: DPRadius.md))
    }
}

/// The overlay legend (F-RPL-08 AC1): swatch + pattern + word, never colour alone.
struct ReplayLegend: View {
    let styles: [ReplaySegmentStyle]

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s3) {
            Text("On the map").font(DPFont.cardTitle)
            ForEach(styles.isEmpty ? ReplaySegmentStyle.allCases : styles, id: \.self) { style in
                HStack(spacing: DPSpace.s3) {
                    ReplayStyleSwatch(style: style).frame(width: 32, height: 10)
                    Text(ReplayViewModel.legendLabel(style)).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .padding(DPSpace.s4)
        .frame(minWidth: 200, alignment: .leading)
    }
}

/// A short line in a style's stroke; excluded adds the 45° hatch it uses on the timeline and the scrubber.
struct ReplayStyleSwatch: View {
    let style: ReplaySegmentStyle

    var body: some View {
        Canvas { context, size in
            var line = Path()
            line.move(to: CGPoint(x: 2, y: size.height / 2))
            line.addLine(to: CGPoint(x: size.width - 2, y: size.height / 2))
            switch style {
            case .observed:
                context.stroke(line, with: .color(DPColor.Data.route), style: ReplayStroke.route)
            case .estimated:
                context.stroke(line, with: .color(DPColor.Data.route.opacity(DPEstimated.opacity)), style: ReplayStroke.estimated)
            case .noGps:
                context.stroke(line, with: .color(DPColor.Data.gapStroke), style: ReplayStroke.gap)
            case .excluded:
                ReplayHatch.fill(CGRect(origin: .zero, size: size), in: &context)
                context.stroke(line, with: .color(DPColor.Data.routeFaded), style: ReplayStroke.excluded)
            }
        }
        .accessibilityHidden(true)
    }
}

enum ReplayHatch {
    /// 45° hatch lines, 2 pt every 5 pt, clipped to `rect`.
    static func fill(_ rect: CGRect, in context: inout GraphicsContext) {
        var hatch = Path()
        var x = rect.minX - rect.height
        while x < rect.maxX {
            hatch.move(to: CGPoint(x: x, y: rect.maxY))
            hatch.addLine(to: CGPoint(x: x + rect.height, y: rect.minY))
            x += 5
        }
        context.drawLayer { layer in
            layer.clip(to: Path(rect))
            layer.stroke(hatch, with: .color(DPColor.Data.gapStroke.opacity(0.7)), lineWidth: 1.5)
        }
    }
}

private struct ReplayMessageCard: View {
    let text: String
    let action: (String, () -> Void)?

    var body: some View {
        VStack(spacing: DPSpace.s4) {
            Text(text).font(DPFont.body).foregroundStyle(DPColor.text).multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if let action {
                Button(action.0, action: action.1).buttonStyle(.glassProminent)
            }
        }
        .padding(DPSpace.s6)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: DPRadius.card))
        .padding(DPLayout.screenMargin)
    }
}
