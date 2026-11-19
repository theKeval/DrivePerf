import DPDesignSystem
import DPSharedBridge
import SwiftUI

// MARK: - Timeline

/// The synced timeline (S-ANL-01): every lane on one x-axis, the quality bands across them, the event lane and the
/// cursor with a value bubble per lane. Only this view reads the cursor, so a drag redraws it and nothing else.
struct AnalysisTimelineCanvas: View {
    let model: AnalysisViewModel
    let timeline: any AnalysisTimelineView
    @Environment(\.dynamicTypeSize) private var typeSize

    static func laneHeight(_ c: AnalysisChannelKind) -> CGFloat {
        switch c {
        case .speed: 44
        case .lonG, .latG: 64
        case .elevation: 34
        case .jerk: 40
        case .quality: 14
        }
    }

    static let laneGap: CGFloat = 6
    static let eventLane: CGFloat = 20
    static let axisRow: CGFloat = 16

    private var scale: CGFloat { typeSize >= .accessibility1 ? 1.5 : 1 }

    private var heights: [CGFloat] { timeline.lanes.map { Self.laneHeight($0.channel) * scale } }

    private var totalHeight: CGFloat { heights.reduce(0) { $0 + $1 + Self.laneGap } + Self.eventLane + Self.axisRow }

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            ZStack(alignment: .topLeading) {
                Canvas { ctx, size in draw(ctx, size) }
                    .accessibilityHidden(true)
                ticks(width: width)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { model.drag(x: $0.location.x, width: width) }
                    .onEnded { _ in model.dragEnded() })
            .simultaneousGesture(
                MagnifyGesture()
                    .onChanged { model.pinchChanged($0.magnification) }
                    .onEnded { _ in model.pinchEnded() })
            .simultaneousGesture(TapGesture(count: 2).onEnded { model.send(.resetZoom) })
        }
        .frame(height: totalHeight)
        .sensoryFeedback(.selection, trigger: model.snappedEventId) { _, new in new != nil }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(timeline.summary)
        .accessibilityValue(model.readout?.accessibilityValue ?? "")
        .accessibilityAdjustableAction { direction in
            let step = max(timeline.timeAt(1) - timeline.timeAt(0), 1) / 20
            model.jump(to: model.cursorS + (direction == .increment ? step : -step))
        }
        .accessibilityChartDescriptor(AnalysisChartDescriptor(timeline))
    }

    /// Event ticks as buttons: "Hard braking, 10:41 into the drive." (44 pt targets).
    private func ticks(width: CGFloat) -> some View {
        let top = heights.reduce(0) { $0 + $1 + Self.laneGap } + Self.eventLane / 2 - DPLayout.touchMin / 2
        return ForEach(timeline.ticks) { tick in
            Button { model.jump(to: tick.tPeakS) } label: { Color.clear }
                .frame(width: DPLayout.touchMin, height: DPLayout.touchMin)
                .contentShape(Rectangle())
                .offset(x: width * tick.x - DPLayout.touchMin / 2, y: top)
                .accessibilityLabel(tick.accessibilityLabel)
        }
    }

    private func laneRects(_ width: CGFloat) -> [CGRect] {
        var y: CGFloat = 0
        return heights.map { h in
            defer { y += h + Self.laneGap }
            return CGRect(x: 0, y: y, width: width, height: h)
        }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let rects = laneRects(size.width)
        let bottom = rects.last?.maxY ?? 0
        drawBands(ctx, size, bottom)
        for (i, lane) in timeline.lanes.enumerated() { drawLane(ctx, lane, rects[i]) }
        let eventY = bottom + Self.laneGap + Self.eventLane / 2
        for t in timeline.ticks {
            ctx.eventMark(t.type, needed: t.needed, at: CGPoint(x: size.width * t.x, y: eventY), size: 12, surface: DPColor.surface)
        }
        for (i, t) in timeline.axisTicks.enumerated() {
            let anchor: UnitPoint = i == 0 ? .topLeading : (i == timeline.axisTicks.count - 1 ? .topTrailing : .top)
            ctx.draw(
                Text(t.label).font(.system(size: 9.5).monospacedDigit()).foregroundStyle(DPColor.text3),
                at: CGPoint(x: size.width * t.x, y: bottom + Self.laneGap + Self.eventLane), anchor: anchor)
        }
        drawCursor(ctx, size, rects)
    }

    private func drawBands(_ ctx: GraphicsContext, _ size: CGSize, _ bottom: CGFloat) {
        for b in timeline.bands {
            let r = CGRect(x: max(b.x0, 0) * size.width, y: 0, width: (min(b.x1, 1) - max(b.x0, 0)) * size.width, height: bottom)
            switch b.style {
            case .estimated: ctx.fill(Path(r), with: .color(DPColor.Data.speed.opacity(0.09)))
            case .noGps: ctx.fill(Path(r), with: .color(DPColor.Data.gapStroke.opacity(0.18)))
            case .excluded: hatch(ctx, r)
            case .observed: break
            }
        }
    }

    private func hatch(_ ctx: GraphicsContext, _ r: CGRect) {
        var c = ctx
        c.clip(to: Path(r))
        var p = Path()
        var x = r.minX - r.height
        while x < r.maxX {
            p.move(to: CGPoint(x: x, y: r.maxY))
            p.addLine(to: CGPoint(x: x + r.height, y: r.minY))
            x += 6
        }
        c.stroke(p, with: .color(DPColor.Data.excludedHatch.opacity(1)), lineWidth: 1)
    }

    private func drawLane(_ ctx: GraphicsContext, _ lane: AnalysisLane, _ r: CGRect) {
        ctx.stroke(Path(roundedRect: r, cornerRadius: 4), with: .color(AnalysisPalette.grid), lineWidth: 1)
        if let z = lane.zeroY {
            let y = r.maxY - z * r.height
            ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: y)); $0.addLine(to: CGPoint(x: r.maxX, y: y)) }, with: .color(AnalysisPalette.axis), lineWidth: 1)
        }
        if lane.channel == .quality {
            ctx.fill(Path(r), with: .color(DPColor.Data.qualityGood.opacity(0.35)))
            return
        }
        let line = Self.path(lane.xs, lane.ys, in: r)
        let color = AnalysisPalette.line(lane.channel)
        if lane.channel == .elevation {
            ctx.fill(Self.area(lane.xs, lane.ys, in: r), with: .color(AnalysisPalette.elevationFill))
        }
        let width: CGFloat = lane.channel == .jerk ? 1.2 : 1.6
        let split = lane.dashedUntilX * r.width
        var dashed = ctx
        dashed.clip(to: Path(CGRect(x: 0, y: r.minY, width: split, height: r.height)))
        dashed.stroke(line, with: .color(color.opacity(DPEstimated.opacity)), style: StrokeStyle(lineWidth: width, dash: [4, 4]))
        var solid = ctx
        solid.clip(to: Path(CGRect(x: split, y: r.minY, width: r.width - split, height: r.height)))
        let dash: [CGFloat] = lane.channel == .latG ? [6, 3] : []
        solid.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: width, lineJoin: .round, dash: dash))
        ctx.draw(
            Text("\(lane.title)  \(lane.rangeLabel)").font(.system(size: 10.5, weight: .semibold)).foregroundStyle(DPColor.text2),
            at: CGPoint(x: 4, y: r.minY + 2), anchor: .topLeading)
    }

    private func drawCursor(_ ctx: GraphicsContext, _ size: CGSize, _ rects: [CGRect]) {
        let x = timeline.xOf(model.cursorS)
        guard (0...1).contains(x), let readout = model.readout else { return }
        let px = x * size.width
        ctx.stroke(
            Path { $0.move(to: CGPoint(x: px, y: 0)); $0.addLine(to: CGPoint(x: px, y: size.height - Self.axisRow)) },
            with: .color(DPColor.Data.cursor), lineWidth: 1.5)
        for (i, v) in readout.values.enumerated() where i < rects.count {
            let r = rects[i]
            let y = v.y.isNaN ? r.midY : r.maxY - min(max(v.y, 0), 1) * r.height
            let text = ctx.resolve(Text(v.text).font(.system(size: 11, weight: .semibold).monospacedDigit()).foregroundStyle(DPColor.Data.cursor))
            let ts = text.measure(in: size)
            let left = px + ts.width + 10 > size.width ? px - ts.width - 6 : px + 6
            let top = min(max(y - ts.height / 2, r.minY), r.maxY - ts.height)
            ctx.fill(Path(roundedRect: CGRect(x: left - 3, y: top, width: ts.width + 6, height: ts.height), cornerRadius: 4), with: .color(DPColor.surface))
            ctx.draw(text, at: CGPoint(x: left, y: top), anchor: .topLeading)
            if !v.y.isNaN {
                ctx.fill(Path(ellipseIn: CGRect(x: px - 3, y: y - 3, width: 6, height: 6)), with: .color(AnalysisPalette.line(v.channel)))
            }
        }
    }

    /// A lane polyline; a `nan` y breaks it.
    static func path(_ xs: [Double], _ ys: [Double], in r: CGRect) -> Path {
        var p = Path()
        var pen = false
        for i in xs.indices {
            guard !ys[i].isNaN else { pen = false; continue }
            let pt = CGPoint(x: r.minX + xs[i] * r.width, y: r.maxY - ys[i] * r.height)
            if pen { p.addLine(to: pt) } else { p.move(to: pt) }
            pen = true
        }
        return p
    }

    /// The area under a lane polyline, piece by piece between gaps.
    static func area(_ xs: [Double], _ ys: [Double], in r: CGRect) -> Path {
        var p = Path()
        var start: CGFloat?
        var last: CGFloat = 0
        func close() {
            if let s = start {
                p.addLine(to: CGPoint(x: last, y: r.maxY))
                p.addLine(to: CGPoint(x: s, y: r.maxY))
                p.closeSubpath()
            }
            start = nil
        }
        for i in xs.indices {
            guard !ys[i].isNaN else { close(); continue }
            let x = r.minX + xs[i] * r.width
            let y = r.maxY - ys[i] * r.height
            if start == nil {
                p.move(to: CGPoint(x: x, y: r.maxY))
                p.addLine(to: CGPoint(x: x, y: y))
                start = x
            } else {
                p.addLine(to: CGPoint(x: x, y: y))
            }
            last = x
        }
        close()
        return p
    }
}

/// Audio Graph for the timeline (S-ANL-01 accessibility): one series per channel, its height in the lane.
struct AnalysisChartDescriptor: AXChartDescriptorRepresentable {
    private let t0: Double
    private let t1: Double
    private let summary: String
    private let series: [(name: String, points: [(x: Double, y: Double)])]

    /// Reads the timeline on the main actor; the descriptor itself is built from these plain values.
    @MainActor
    init(_ timeline: any AnalysisTimelineView) {
        t0 = timeline.timeAt(0)
        t1 = max(timeline.timeAt(1), t0 + 1)
        summary = timeline.summary
        series = timeline.lanes.filter { !$0.xs.isEmpty }.map { lane in
            let step = max(lane.xs.count / 60, 1)
            let points = stride(from: 0, to: lane.xs.count, by: step).compactMap { i -> (x: Double, y: Double)? in
                lane.ys[i].isNaN ? nil : (timeline.timeAt(lane.xs[i]), lane.ys[i])
            }
            return (lane.title, points)
        }
    }

    func makeChartDescriptor() -> AXChartDescriptor {
        let x = AXNumericDataAxisDescriptor(title: "Time", range: t0...t1, gridlinePositions: []) { v in
            "\(Int(v) / 60) min \(Int(v) % 60) s"
        }
        let y = AXNumericDataAxisDescriptor(title: "Level in its lane", range: 0...1, gridlinePositions: []) { v in
            "\(Int((v * 100).rounded())) percent"
        }
        let described = series.map { s in
            AXDataSeriesDescriptor(name: s.name, isContinuous: true, dataPoints: s.points.map { AXDataPoint(x: $0.x, y: $0.y) })
        }
        return AXChartDescriptor(title: "Timeline", summary: summary, xAxis: x, yAxis: y, additionalAxes: [], series: described)
    }
}

// MARK: - Map

/// The S-ANL-01 map card: the route in its quality styles, the pins and the cursor marker. Tap snaps the cursor to the
/// nearest track point (AC2). Drawn without tiles, so it works offline.
struct AnalysisMapCanvas: View {
    let model: AnalysisViewModel
    let map: any AnalysisMapView

    private struct Fit {
        let scale: CGFloat
        let ox: CGFloat
        let oy: CGFloat

        init(widthM: Double, heightM: Double, _ size: CGSize, pad: CGFloat = 14) {
            scale = min((size.width - 2 * pad) / max(widthM, 1), (size.height - 2 * pad) / max(heightM, 1))
            ox = (size.width - widthM * scale) / 2
            oy = (size.height + heightM * scale) / 2
        }

        func screen(_ p: AnalysisMapPoint) -> CGPoint { CGPoint(x: ox + p.x * scale, y: oy - p.y * scale) }

        func metres(_ o: CGPoint) -> AnalysisMapPoint { AnalysisMapPoint(x: (o.x - ox) / scale, y: (oy - o.y) / scale) }
    }

    var body: some View {
        let (w, h) = (map.widthM, map.heightM)
        return GeometryReader { geo in
            Canvas { ctx, size in
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(DPColor.Data.mapLand))
                let fit = Fit(widthM: w, heightM: h, size)
                for s in map.segments { route(ctx, s, fit) }
                for p in map.pins {
                    ctx.eventMark(p.type, needed: p.needed, at: fit.screen(p.at), size: 14, surface: DPColor.surface)
                }
                if let m = model.readout?.marker {
                    let c = fit.screen(m)
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 8, y: c.y - 8, width: 16, height: 16)), with: .color(DPColor.Data.routeCasing))
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 6, y: c.y - 6, width: 12, height: 12)), with: .color(DPColor.Data.marker))
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { location in
                let p = Fit(widthM: w, heightM: h, geo.size).metres(location)
                model.tapMap(x: p.x, y: p.y)
            }
        }
        .accessibilityElement()
        .accessibilityLabel("Map of this drive with the cursor marker")
        .accessibilityHint("Moves the cursor to the point you tap.")
        .accessibilityAddTraits(.isButton)
    }

    private func route(_ ctx: GraphicsContext, _ s: AnalysisMapSegment, _ fit: Fit) {
        guard let first = s.points.first else { return }
        var p = Path()
        p.move(to: fit.screen(first))
        for pt in s.points.dropFirst() { p.addLine(to: fit.screen(pt)) }
        switch s.style {
        case .observed:
            ctx.stroke(p, with: .color(DPColor.Data.routeCasing), style: StrokeStyle(lineWidth: 7.5, lineCap: .round, lineJoin: .round))
            ctx.stroke(p, with: .color(DPColor.Data.route), style: StrokeStyle(lineWidth: 3.8, lineCap: .round, lineJoin: .round))
        case .estimated:
            ctx.stroke(p, with: .color(DPColor.Data.route.opacity(DPEstimated.opacity)), style: StrokeStyle(lineWidth: 3.8, dash: [4, 4]))
        case .noGps:
            ctx.stroke(p, with: .color(DPColor.Data.gapStroke), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [1, 6]))
        case .excluded:
            ctx.stroke(p, with: .color(DPColor.Data.routeFaded), style: StrokeStyle(lineWidth: 3.8, lineCap: .round, lineJoin: .round))
        }
    }
}

// MARK: - G-G

/// The friction circle (F-RPL-06): density cloud, envelope, rings every 0.1 g (labelled when `expanded`), pins at
/// their peaks and the dot at the cursor.
struct AnalysisGgCanvas: View {
    let model: AnalysisViewModel
    let gg: AnalysisGgView
    var expanded = false

    var body: some View {
        Canvas { ctx, size in
            let r = min(size.width, size.height) / 2 - (expanded ? 18 : 6)
            let c = CGPoint(x: size.width / 2, y: size.height / 2)
            let k = r / gg.scaleG
            func at(_ p: AnalysisGPoint) -> CGPoint { CGPoint(x: c.x + p.latG * k, y: c.y - p.lonG * k) }
            var axes = Path()
            axes.move(to: CGPoint(x: c.x - r, y: c.y))
            axes.addLine(to: CGPoint(x: c.x + r, y: c.y))
            axes.move(to: CGPoint(x: c.x, y: c.y - r))
            axes.addLine(to: CGPoint(x: c.x, y: c.y + r))
            ctx.stroke(axes, with: .color(AnalysisPalette.grid), lineWidth: 1)
            for ring in gg.rings {
                let rr = ring.g * k
                ctx.stroke(
                    Path(ellipseIn: CGRect(x: c.x - rr, y: c.y - rr, width: 2 * rr, height: 2 * rr)),
                    with: .color(AnalysisPalette.ggRing), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                if expanded {
                    ctx.draw(
                        Text(ring.label).font(.system(size: 9.5).monospacedDigit()).foregroundStyle(DPColor.text3),
                        at: CGPoint(x: c.x + 2, y: c.y - rr), anchor: .bottomLeading)
                }
            }
            let dotR: CGFloat = expanded ? 2.5 : 1.6
            for d in gg.dots {
                let o = at(d.at)
                let alpha = d.emphasised ? 0.38 * (0.35 + 0.65 * d.weight) : 0.06
                ctx.fill(Path(ellipseIn: CGRect(x: o.x - dotR, y: o.y - dotR, width: 2 * dotR, height: 2 * dotR)), with: .color(AnalysisPalette.ggCloud.opacity(alpha)))
            }
            if let first = gg.envelope.first {
                var env = Path()
                env.move(to: at(first))
                for p in gg.envelope.dropFirst() { env.addLine(to: at(p)) }
                env.closeSubpath()
                ctx.stroke(env, with: .color(AnalysisPalette.ggEnvelope), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
            }
            for p in gg.pins {
                ctx.eventMark(p.type, needed: p.needed, at: at(p.at), size: expanded ? 16 : 10, surface: DPColor.surface)
            }
            if let dot = model.readout?.gg {
                let o = at(dot)
                ctx.fill(Path(ellipseIn: CGRect(x: o.x - 5, y: o.y - 5, width: 10, height: 10)), with: .color(DPColor.Data.routeCasing))
                ctx.fill(Path(ellipseIn: CGRect(x: o.x - 4, y: o.y - 4, width: 8, height: 8)), with: .color(AnalysisPalette.ggDot))
            }
        }
        .accessibilityElement()
        .accessibilityLabel(gg.accessibilityLabel)
    }
}
