import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// The S-TRP-01 map hero (DRIVE-55): the route in Glide over the plain map canvas, drawn from the preview track as the
/// Analysis map card is (no tiles, no street names), with the estimated start dashed, GPS gaps dotted, stop capsules,
/// the neutral max-speed triangle and the event pins by shape. The quality badge and "Estimated start" sit top-left as
/// glass chips and "Replay" bottom-left. Tapping anywhere opens Replay; without Replay it is a picture only.
struct TripMapHero: View {
    let map: TripMap
    let quality: QualityBadge?
    let qualityWord: String?
    let links: TripLinkWords
    let onReplay: (() -> Void)?

    var body: some View {
        Button { onReplay?() } label: {
            ZStack {
                canvas
                VStack(alignment: .leading) {
                    HStack(spacing: DPSpace.s1_5) {
                        if let quality { QualityBadgeLabel(badge: quality, word: qualityWord ?? "").padding(chipPadding).glassEffect(.regular, in: Capsule()) }
                        if map.estimatedStart {
                            Text(links.estimatedStart).font(DPFont.caption.weight(.semibold)).foregroundStyle(DPColor.text)
                                .padding(chipPadding).glassEffect(.regular, in: Capsule())
                        }
                    }
                    Spacer()
                    if onReplay != nil {
                        Label {
                            Text("Replay").font(DPFont.caption.weight(.semibold))
                        } icon: {
                            Image(dp: .replay).resizable().scaledToFit().frame(width: 14, height: 14)
                        }
                        .foregroundStyle(DPColor.text)
                        .padding(chipPadding)
                        .glassEffect(.regular, in: Capsule())
                    }
                }
                .padding(DPSpace.s3)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            }
            .frame(height: 232)
            .clipShape(RoundedRectangle(cornerRadius: DPRadius.card))
            .contentShape(RoundedRectangle(cornerRadius: DPRadius.card))
        }
        .buttonStyle(.plain)
        .disabled(onReplay == nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(map.accessibilityLabel)
        .accessibilityHint(onReplay == nil ? "" : "Watch this drive on a map")
        .accessibilityAddTraits(onReplay == nil ? .isImage : .isButton)
        .accessibilityIdentifier("tripDetail.map")
    }

    private var chipPadding: EdgeInsets { EdgeInsets(top: 6, leading: 10, bottom: 6, trailing: 10) }

    private var canvas: some View {
        Canvas { ctx, size in
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(DPColor.Data.mapLand))
            let fit = Fit(map, size)
            for s in map.segments { route(ctx, s, fit) }
            for stop in map.stops {
                let c = fit.screen(stop)
                ctx.fill(Path(roundedRect: CGRect(x: c.x - 7, y: c.y - 4, width: 14, height: 8), cornerRadius: 4),
                    with: .color(DPColor.text2))
            }
            if let top = map.maxSpeed {
                let c = fit.screen(top)
                var t = Path()
                t.move(to: CGPoint(x: c.x, y: c.y - 6))
                t.addLine(to: CGPoint(x: c.x + 6, y: c.y + 5))
                t.addLine(to: CGPoint(x: c.x - 6, y: c.y + 5))
                t.closeSubpath()
                ctx.fill(t, with: .color(DPColor.text2))
            }
            for p in map.pins {
                ctx.eventMark(p.type, needed: p.needed, at: fit.screen(p.at), size: 18, surface: DPColor.surface)
            }
        }
    }

    /// The map's metres fitted into the card, north up, below the chips and above the Replay capsule.
    private struct Fit {
        let scale: CGFloat
        let ox: CGFloat
        let oy: CGFloat

        init(_ map: TripMap, _ size: CGSize, pad: CGFloat = 28, top: CGFloat = 52) {
            scale = min((size.width - 2 * pad) / max(map.widthM, 1), (size.height - pad - top) / max(map.heightM, 1))
            ox = (size.width - map.widthM * scale) / 2
            oy = top + (size.height - top - pad + map.heightM * scale) / 2
        }

        func screen(_ p: AnalysisMapPoint) -> CGPoint { CGPoint(x: ox + p.x * scale, y: oy - p.y * scale) }
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
