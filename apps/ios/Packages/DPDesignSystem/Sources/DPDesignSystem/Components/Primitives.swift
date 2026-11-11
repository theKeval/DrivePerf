import SwiftUI

/// A 28 pt glyph disc used by checklists and promise cards (components.md §0).
public struct GlyphDisc: View {
    public enum Tone: Sendable { case positive, neutral, warning, accent }

    private let icon: DPIcon
    private let tone: Tone
    private let size: CGFloat

    public init(_ icon: DPIcon, tone: Tone = .positive, size: CGFloat = 28) {
        self.icon = icon
        self.tone = tone
        self.size = size
    }

    public var body: some View {
        icon.image
            .resizable()
            .scaledToFit()
            .frame(width: size * 0.55, height: size * 0.55)
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .background(background, in: Circle())
            .accessibilityHidden(true)
    }

    private var foreground: Color {
        switch tone {
        case .positive, .accent: DPColor.accentText
        case .neutral: DPColor.text2
        case .warning: DPColor.warning
        }
    }

    private var background: Color {
        switch tone {
        case .positive, .accent: DPColor.accentContainer
        case .neutral: DPColor.fill
        case .warning: DPColor.warningContainer
        }
    }
}

/// One row of a checklist card: glyph disc, bold line and an optional supporting line.
public struct ChecklistRow: View {
    private let icon: DPIcon
    private let tone: GlyphDisc.Tone
    private let title: String
    private let detail: String?

    public init(_ icon: DPIcon, tone: GlyphDisc.Tone = .positive, title: String, detail: String? = nil) {
        self.icon = icon
        self.tone = tone
        self.title = title
        self.detail = detail
    }

    public var body: some View {
        HStack(alignment: .top, spacing: DPSpace.s3) {
            GlyphDisc(icon, tone: tone)
            VStack(alignment: .leading, spacing: DPSpace.s0_5) {
                Text(title).font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                if let detail {
                    Text(detail).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, DPSpace.s3)
        .padding(.horizontal, DPSpace.s4)
        .accessibilityElement(children: .combine)
    }
}

/// Stacks rows inside one card with inset 0.5 pt separators.
public struct DPCardStack<Content: View>: View {
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        VStack(spacing: 0) {
            Group(subviews: content) { subviews in
                ForEach(Array(subviews.enumerated()), id: \.offset) { index, subview in
                    if index > 0 {
                        Rectangle().fill(DPColor.separator).frame(height: 0.5).padding(.leading, 56)
                    }
                    subview
                }
            }
        }
        .dpCard()
    }
}

extension View {
    /// Card surface: `surface` fill, radius 24 (radius.ios.card).
    public func dpCard(radius: CGFloat = DPRadius.card) -> some View {
        background(DPColor.surface, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }

    /// Full-screen grouped background used by tab roots and onboarding.
    public func dpScreenBackground() -> some View {
        background(DPColor.bgGrouped.ignoresSafeArea())
    }
}

/// Onboarding step progress ("Setup, step n of N").
public struct StepProgress: View {
    private let step: Int
    private let total: Int

    public init(step: Int, total: Int) {
        self.step = step
        self.total = total
    }

    public var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(DPColor.fillStrong)
                Capsule().fill(DPColor.accent)
                    .frame(width: proxy.size.width * CGFloat(step) / CGFloat(max(total, 1)))
            }
        }
        .frame(width: 144, height: 6)
        .accessibilityElement()
        .accessibilityLabel("Setup, step \(step) of \(total)")
    }
}

/// The filled primary action (Glide on Night): native Liquid Glass prominent style tinted with the accent.
public struct DPPrimaryButton: View {
    private let title: String
    private let icon: DPIcon?
    private let height: CGFloat
    private let action: () -> Void

    public init(_ title: String, icon: DPIcon? = nil, height: CGFloat = DPLayout.primaryButtonHeight,
                action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.height = height
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: DPSpace.s2) {
                if let icon { icon.image.resizable().frame(width: 18, height: 18) }
                Text(title).font(DPFont.button)
            }
            .foregroundStyle(DPColor.onAccent)
            .frame(maxWidth: .infinity, minHeight: height)
        }
        .buttonStyle(.glassProminent)
        .tint(DPColor.accent)
        .buttonBorderShape(.capsule)
    }
}

/// The secondary text action under a primary button ("Not now").
public struct DPSecondaryButton: View {
    private let title: String
    private let action: () -> Void

    public init(_ title: String, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(title)
                .font(DPFont.button)
                .foregroundStyle(DPColor.accentText)
                .frame(maxWidth: .infinity, minHeight: DPLayout.touchMin)
        }
        .buttonStyle(.plain)
    }
}
