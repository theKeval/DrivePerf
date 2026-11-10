import DPDesignSystem
import SwiftUI

/// The frame every onboarding page shares: a top bar (glass Back, step progress, optional Skip), scrolling content
/// and a floating Liquid Glass dock that stays pinned at XL text sizes (accessibility notes in each spec).
struct OnboardingScaffold<Content: View, Dock: View>: View {
    let step: OnboardingStep
    let canGoBack: Bool
    var showsSkip = false
    var alignment: HorizontalAlignment = .center
    let onBack: () -> Void
    var onSkip: () -> Void = {}
    @ViewBuilder let content: () -> Content
    @ViewBuilder let dock: () -> Dock

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                VStack(alignment: alignment, spacing: DPSpace.s5) {
                    content()
                }
                .frame(maxWidth: .infinity, alignment: Alignment(horizontal: alignment, vertical: .top))
                .padding(.horizontal, DPSpace.s6)
                .padding(.top, DPSpace.s4)
                .padding(.bottom, DPSpace.s8)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .safeAreaInset(edge: .bottom) {
            GlassEffectContainer {
                VStack(spacing: DPSpace.s1) { dock() }
                    .padding(DPSpace.s3)
                    .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 46, style: .continuous))
            }
            .padding(.horizontal, DPSpace.s2)
        }
        .dpScreenBackground()
    }

    private var topBar: some View {
        ZStack {
            if let setup = step.setupStep {
                StepProgress(step: setup, total: OnboardingStep.setupTotal)
            }
            HStack {
                if canGoBack {
                    Button(action: onBack) {
                        DPIcon.chevronLeft.image.resizable().frame(width: 20, height: 20)
                            .frame(width: DPLayout.toolbarButton, height: DPLayout.toolbarButton)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .foregroundStyle(DPColor.text)
                    .accessibilityLabel("Back")
                }
                Spacer()
                if showsSkip {
                    Button("Skip", action: onSkip)
                        .font(DPFont.body)
                        .foregroundStyle(DPColor.text2)
                        .frame(minWidth: DPLayout.touchMin, minHeight: DPLayout.touchMin)
                }
            }
        }
        .frame(height: DPLayout.toolbarButton)
        .padding(.horizontal, DPLayout.screenMargin)
        .padding(.top, DPSpace.s2)
    }
}

/// Page title and lead in the onboarding style.
struct OnboardingHeading: View {
    let title: String
    let lead: String?
    var alignment: TextAlignment = .center

    var body: some View {
        VStack(alignment: alignment == .center ? .center : .leading, spacing: DPSpace.s3) {
            Text(title)
                .font(DPFont.onboardingTitle)
                .foregroundStyle(DPColor.text)
                .accessibilityAddTraits(.isHeader)
            if let lead {
                Text(lead).font(DPFont.body).foregroundStyle(DPColor.text2)
            }
        }
        .multilineTextAlignment(alignment)
        .frame(maxWidth: .infinity, alignment: alignment == .center ? .center : .leading)
    }
}

/// Page dots for the three intro pages (current = 18 × 6 Glide capsule).
struct PageDots: View {
    let current: Int
    let total: Int

    var body: some View {
        HStack(spacing: DPSpace.s1_5) {
            ForEach(0..<total, id: \.self) { index in
                Capsule()
                    .fill(index == current ? DPColor.accent : DPColor.fillStrong)
                    .frame(width: index == current ? 18 : 6, height: 6)
            }
        }
        .accessibilityElement()
        .accessibilityLabel("Page \(current + 1) of \(total)")
        .padding(.vertical, DPSpace.s1)
    }
}

/// The permission art: a soft route on a faint grid with an optional badge, greyed when the permission is off.
struct PermissionArt: View {
    let icon: DPIcon
    var dimmed = false

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: DPRadius.lg, style: .continuous)
                .fill(DPColor.accentContainer.opacity(dimmed ? 0.25 : 0.6))
            Grid(horizontalSpacing: 0, verticalSpacing: 0) {
                ForEach(0..<4, id: \.self) { _ in
                    GridRow {
                        ForEach(0..<6, id: \.self) { _ in
                            Rectangle().stroke(DPColor.accent.opacity(0.08), lineWidth: 0.5)
                        }
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: DPRadius.lg, style: .continuous))
            DPIcon.drives.image.resizable().scaledToFit()
                .frame(width: 140, height: 90)
                .foregroundStyle(dimmed ? DPColor.text3 : DPColor.accent)
            icon.image.resizable().scaledToFit()
                .frame(width: 30, height: 30)
                .foregroundStyle(dimmed ? DPColor.text3 : DPColor.accent)
                .padding(DPSpace.s2)
                .background(DPColor.surface, in: Circle())
                .offset(x: 70, y: -34)
        }
        .frame(width: 220, height: 132)
        .accessibilityHidden(true)
    }
}
