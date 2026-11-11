import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// WP-A onboarding (S-ONB-01…16). Copy is verbatim from docs/design/DRIVE-33, -38, -43, -49, -54, -57, -70, -80.
public struct OnboardingFlowView: View {
    @Bindable var model: OnboardingViewModel

    public init(model: OnboardingViewModel) {
        self.model = model
    }

    public var body: some View {
        Group {
            switch model.step {
            case .welcome: WelcomePage(model: model)
            case .storyPreview: StoryPreviewPage(model: model)
            case .privacy: PrivacyPage(model: model)
            case .ageTerms:
                if model.under18Blocked { Under18Page(model: model) } else { AgeTermsPage(model: model) }
            case .capturePromise: CapturePromisePage(model: model)
            case .location: LocationPage(model: model)
            case .motion: MotionPage(model: model)
            case .autoTrips: AutoTripsPage(model: model)
            case .notifications: NotificationsPage(model: model)
            case .helpImprove: HelpImprovePage(model: model)
            case .ready: ReadyPage(model: model)
            }
        }
        .animation(.smooth(duration: 0.22), value: model.step)
        .sheet(item: $model.sheet, onDismiss: { model.finishPreciseSheet() }) { sheet in
            switch sheet {
            case .precise: PreciseLocationSheet(model: model)
            case .locationDenied: LocationDeniedSheet(model: model)
            case .alwaysDeclined: AlwaysDeclinedSheet(model: model)
            }
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - S-ONB-01 Welcome

private struct WelcomePage: View {
    let model: OnboardingViewModel

    var body: some View {
        VStack(spacing: DPSpace.s5) {
            HStack {
                Spacer()
                Button("Skip") { model.skipIntro() }
                    .font(DPFont.body)
                    .foregroundStyle(DPColor.text2)
                    .frame(minWidth: DPLayout.touchMin, minHeight: DPLayout.touchMin)
            }
            .padding(.horizontal, DPLayout.screenMargin)
            Spacer()
            LoopMark(size: 172)
            VStack(spacing: DPSpace.s3) {
                Text("DrivePerf")
                    .font(.title3.weight(.semibold).width(.expanded))
                    .foregroundStyle(DPColor.text2)
                Text("Smooth is a skill.")
                    .font(DPFont.welcomeLead)
                    .foregroundStyle(DPColor.text)
                    .accessibilityAddTraits(.isHeader)
                Text("Every drive, decoded.")
                    .font(.title3)
                    .foregroundStyle(DPColor.text2)
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, DPSpace.s8)
            Spacer()
            PageDots(current: 0, total: 3)
            DPPrimaryButton("Get started", height: 52) { model.next() }
                .padding(.horizontal, DPSpace.s6)
                .padding(.bottom, DPSpace.s4)
        }
        .background(
            LinearGradient(
                colors: [Color(rgba: 0x0A14_20FF), Color(rgba: 0x080B_10FF), Color(rgba: 0x0507_0AFF)],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea())
    }
}

// MARK: - S-ONB-02 Story preview

private struct StoryPreviewPage: View {
    let model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(
            step: .storyPreview, canGoBack: model.canGoBack, showsSkip: true, onBack: model.back,
            onSkip: model.skipIntro
        ) {
            VStack(alignment: .leading, spacing: DPSpace.s3) {
                Text("EXAMPLE DRIVE").font(DPFont.caption.weight(.semibold)).foregroundStyle(DPColor.text2)
                Text("Thursday morning · Home → Work").font(DPFont.cardTitle).foregroundStyle(DPColor.text)
                DPIcon.drives.image.resizable().scaledToFit()
                    .foregroundStyle(DPColor.accent)
                    .frame(maxWidth: .infinity, maxHeight: 120)
                    .padding(.vertical, DPSpace.s4)
                HStack {
                    Spacer()
                    ZStack {
                        Circle().stroke(DPColor.fillStrong, lineWidth: 10)
                        Circle().trim(from: 0, to: 0.92).stroke(
                            DPColor.accent, style: StrokeStyle(lineWidth: 10, lineCap: .round)
                        ).rotationEffect(.degrees(-90))
                        Text("92").dpNumeral(.l).foregroundStyle(DPColor.text)
                    }
                    .frame(width: 92, height: 92)
                    Spacer()
                }
                Text("▲ +7 vs your usual on The Commute")
                    .font(DPFont.caption.weight(.semibold))
                    .foregroundStyle(DPColor.accentText)
                    .padding(.horizontal, DPSpace.s3)
                    .padding(.vertical, DPSpace.s1_5)
                    .glassEffect(.regular, in: Capsule())
                    .frame(maxWidth: .infinity)
            }
            .padding(DPSpace.s5)
            .frame(width: 280)
            .background(
                LinearGradient(
                    colors: [DPColor.Car.carmine.opacity(0.16), Color(rgba: 0x080B_10FF)], startPoint: .top,
                    endPoint: .bottom),
                in: RoundedRectangle(cornerRadius: DPRadius.cardHero, style: .continuous)
            )
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Example drive: Thursday morning, Home to Work, Smoothness 92, 7 above your usual.")
            OnboardingHeading(
                title: "Every drive, decoded.",
                lead: "Park, and each drive becomes a short Story: the route, your inputs and a Smoothness score "
                    + "compared with your own usual. Never with anyone else’s.")
        } dock: {
            PageDots(current: 1, total: 3)
            DPPrimaryButton("Next") { model.next() }
        }
    }
}

// MARK: - S-ONB-03 Private by design

private struct PrivacyPage: View {
    let model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(step: .privacy, canGoBack: model.canGoBack, onBack: model.back) {
            GlyphDisc(.lock, tone: .accent, size: 88)
            OnboardingHeading(
                title: "Private by design",
                lead: "No account needed. Your trips stay on your phone unless you turn on backup.")
            DPCardStack {
                ChecklistRow(.check, title: "Worked out on your phone")
                ChecklistRow(.close, tone: .neutral, title: "Never sold, never shared with insurers")
                ChecklistRow(.check, title: "You choose what’s shared")
            }
            VStack(alignment: .leading, spacing: DPSpace.s2) {
                Text("PRO PREVIEW")
                    .font(DPFont.caption.weight(.bold))
                    .foregroundStyle(DPColor.accentText)
                    .padding(.horizontal, DPSpace.s2)
                    .padding(.vertical, DPSpace.s1)
                    .background(DPColor.accentContainer, in: Capsule())
                Text("Pro Preview included for 14 days, no card").font(DPFont.cardTitle)
                    .foregroundStyle(DPColor.text)
                Text(
                    "Replay and Analysis for every drive, unlimited routes and trends. Then choose Pro, or keep "
                        + "Free: recording, Stories and scores stay free."
                )
                .font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            }
            .padding(DPSpace.s4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .dpCard()
        } dock: {
            PageDots(current: 2, total: 3)
            DPPrimaryButton("Continue") { model.next() }
        }
    }
}

// MARK: - S-ONB-04 Age and terms

private struct AgeTermsPage: View {
    @Bindable var model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(step: .ageTerms, canGoBack: model.canGoBack, onBack: model.back) {
            Text("18+")
                .dpNumeral(.l)
                .foregroundStyle(DPColor.accentText)
                .frame(width: 112, height: 112)
                .background(DPColor.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 34, style: .continuous))
                .accessibilityHidden(true)
            OnboardingHeading(
                title: "One quick check",
                lead: "DrivePerf is for drivers 18 and over. No date of birth needed: you just confirm it.")
            VStack(alignment: .leading, spacing: DPSpace.s3) {
                Button {
                    model.ageConfirmed.toggle()
                } label: {
                    HStack(spacing: DPSpace.s3) {
                        Image(systemName: model.ageConfirmed ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 26))
                            .foregroundStyle(model.ageConfirmed ? DPColor.accent : DPColor.text3)
                        Text("I’m 18 or older").font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                        Spacer()
                    }
                    .frame(minHeight: DPLayout.touchMin)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(.isToggle)
                .accessibilityValue(model.ageConfirmed ? "checked" : "unchecked")
                .sensoryFeedback(.selection, trigger: model.ageConfirmed)
                Text(termsLine)
                    .font(DPFont.meta)
                    .foregroundStyle(DPColor.text2)
                    .tint(DPColor.accentText)
            }
            .padding(DPSpace.s4)
            .dpCard()
        } dock: {
            DPPrimaryButton("Continue") { model.confirmAge() }
                .disabled(!model.canConfirmAge)
                .opacity(model.canConfirmAge ? 1 : 0.38)
            DPSecondaryButton("I’m under 18") { model.declareUnder18() }
        }
    }

    private var termsLine: AttributedString {
        var text = (try? AttributedString(
            markdown: "By continuing you agree to the [Terms of Use](https://driveperf.com/terms) and "
                + "[Privacy Policy](https://driveperf.com/privacy). We save the version you accepted, on this phone."
        )) ?? AttributedString("")
        for run in text.runs where run.link != nil { text[run.range].underlineStyle = .single }
        return text
    }
}

private struct Under18Page: View {
    let model: OnboardingViewModel

    var body: some View {
        VStack(spacing: DPSpace.s5) {
            Spacer()
            LoopGlyph(size: 96).foregroundStyle(DPColor.text3)
            OnboardingHeading(
                title: "DrivePerf is for drivers 18 and over for now",
                lead: "Thanks for checking it out. Nothing about you was saved, apart from a note on this phone so "
                    + "this screen shows next time.")
            Spacer()
            DPSecondaryButton("I tapped this by mistake") { model.undoUnder18() }
                .padding(.bottom, DPSpace.s6)
        }
        .padding(.horizontal, DPSpace.s6)
        .dpScreenBackground()
    }
}

// MARK: - S-ONB-05 How capture works

private struct CapturePromisePage: View {
    @Bindable var model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(step: .capturePromise, canGoBack: model.canGoBack, onBack: model.back) {
            PermissionArt(icon: .drives)
            OnboardingHeading(
                title: "How capture works",
                lead: "What automatic capture can and can’t do, so nothing surprises you later.")
            DPCardStack {
                ChecklistRow(.check, title: "Auto-detects most drives.")
                ChecklistRow(.info, tone: .neutral, title: "The first few hundred metres may be estimated.")
                ChecklistRow(
                    .pause, tone: .neutral, title: "Force-quitting pauses auto-detect.",
                    detail: "Swiping DrivePerf away in the app switcher stops it from noticing drives.")
                ChecklistRow(
                    .battery, tone: .neutral, title: "About 2–4% battery per hour of driving with the screen off.")
            }
            Button {
                model.isHowItWorksPresented = true
            } label: {
                Label { Text("How it works") } icon: { DPIcon.info.image.resizable().frame(width: 18, height: 18) }
                    .font(DPFont.body.weight(.semibold))
                    .foregroundStyle(DPColor.accentText)
                    .frame(minHeight: DPLayout.touchMin)
            }
            .buttonStyle(.plain)
        } dock: {
            DPPrimaryButton("Continue") { model.next() }
        }
        .sheet(isPresented: $model.isHowItWorksPresented) { HowItWorksSheet() }
    }
}

private struct HowItWorksSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DPSpace.s4) {
                    Text("Two ways to record. You can switch between them anytime in Settings.")
                        .font(DPFont.body).foregroundStyle(DPColor.text2)
                    DPCardStack {
                        ChecklistRow(
                            .bolt, tone: .accent, title: "Automatic trips",
                            detail: "DrivePerf notices when you set off and decodes each drive when you park.")
                        ChecklistRow(
                            .record, tone: .accent, title: "Manual trips",
                            detail: "Tap Start when you set off and Stop when you park.")
                    }
                    Link("More on driveperf.com ›", destination: URL(string: "https://driveperf.com/how-it-records")!)
                        .font(DPFont.body.weight(.semibold))
                        .foregroundStyle(DPColor.accentText)
                }
                .padding(DPLayout.screenMargin)
            }
            .navigationTitle("How it works")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .close) { dismiss() }
                }
            }
            .dpScreenBackground()
        }
        .presentationDetents([.large])
    }
}

// MARK: - S-ONB-06 Location

private struct LocationPage: View {
    let model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(step: .location, canGoBack: model.canGoBack, onBack: model.back) {
            PermissionArt(icon: .gps, dimmed: model.locationDenied)
            if model.locationDenied {
                OnboardingHeading(
                    title: "Location is off",
                    lead: "Without location, DrivePerf can’t record drives, automatic or manual. You can turn it on "
                        + "in Settings anytime.")
                StatusRow(label: "Location", value: "Never")
            } else {
                OnboardingHeading(
                    title: "Draw the route of every drive",
                    lead: "DrivePerf uses your location while you use the app to record each drive’s route, and to "
                        + "place braking and cornering on the road.")
                DPCardStack {
                    ChecklistRow(.check, title: "Route, distance and stops for every drive")
                    ChecklistRow(.check, title: "Processed on your phone")
                    ChecklistRow(.close, tone: .neutral, title: "Never sold, never shared with insurers")
                }
                Text("You choose about automatic trips in two steps.")
                    .font(DPFont.meta).foregroundStyle(DPColor.text3)
            }
        } dock: {
            if model.locationDenied {
                DPPrimaryButton("Open Settings", icon: .gear) { model.openSettings() }
                DPSecondaryButton("Continue without location") { model.declineLocation() }
            } else {
                DPPrimaryButton("Continue") { Task { await model.continueLocation() } }
                    .disabled(model.isRequesting)
                    .accessibilityLabel("Continue to the location prompt")
                DPSecondaryButton("Not now") { model.declineLocation() }
            }
        }
    }
}

// MARK: - S-ONB-07 Motion

private struct MotionPage: View {
    let model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(step: .motion, canGoBack: model.canGoBack, onBack: model.back) {
            FrictionCircleArt(dimmed: model.motionDenied)
            if model.motionDenied {
                OnboardingHeading(
                    title: "Motion & Fitness is off",
                    lead: "Automatic trips can still run on location alone, with more false trips and slower "
                        + "starts. You can turn it on in Settings anytime.")
                StatusRow(label: "Motion & Fitness", value: "Off")
            } else {
                OnboardingHeading(
                    title: "Know when you’re driving",
                    lead: "Motion & Fitness lets DrivePerf tell driving from walking, so drives start sooner and "
                        + "false trips stay rare.")
                DPCardStack {
                    ChecklistRow(
                        .check, title: "Fewer false trips", detail: "Walks, runs, bikes and transit are left out.")
                    ChecklistRow(.check, title: "Activity data stays on your phone")
                }
            }
        } dock: {
            DPPrimaryButton("Continue") { Task { await model.continueMotion() } }
                .disabled(model.isRequesting)
            if model.motionDenied {
                DPSecondaryButton("Open Settings") { model.openSettings() }
            }
        }
    }
}

private struct FrictionCircleArt: View {
    let dimmed: Bool

    var body: some View {
        ZStack {
            ForEach([0.33, 0.66, 1.0], id: \.self) { scale in
                Circle().stroke(DPColor.text.opacity(0.12), lineWidth: 1).frame(width: 168 * scale)
            }
            Rectangle().fill(DPColor.text.opacity(0.12)).frame(width: 1, height: 168)
            Rectangle().fill(DPColor.text.opacity(0.12)).frame(width: 168, height: 1)
            Circle().fill(DPColor.accent.opacity(dimmed ? 0.1 : 0.3)).frame(width: 70, height: 46).offset(y: 8)
            Circle().fill(dimmed ? DPColor.text3 : .white).frame(width: 8, height: 8).offset(x: 10, y: -14)
        }
        .frame(width: 168, height: 168)
        .accessibilityHidden(true)
    }
}

// MARK: - S-ONB-08 Automatic trips

private struct AutoTripsPage: View {
    @Bindable var model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(step: .autoTrips, canGoBack: model.canGoBack, alignment: .leading, onBack: model.back) {
            PermissionArt(icon: .bolt, dimmed: !model.autoTripsOn).frame(maxWidth: .infinity)
            OnboardingHeading(
                title: "Automatic trips",
                lead: "Record every drive without opening the app. Or keep it manual: tap Start when you set off.",
                alignment: .leading)
            VStack(alignment: .leading, spacing: DPSpace.s3) {
                Label {
                    Text("BACKGROUND LOCATION").font(DPFont.caption.weight(.bold))
                } icon: {
                    DPIcon.gps.image.resizable().frame(width: 16, height: 16)
                }
                .foregroundStyle(DPColor.accentText)
                Text(
                    "DrivePerf collects location data to enable automatic trip recording, even when the app is "
                        + "closed or not in use."
                )
                .font(DPFont.body).foregroundStyle(DPColor.text)
                DefinitionRow(term: "What", text: "Your location and phone motion, during the drives it detects.")
                DefinitionRow(term: "Why", text: "To record each drive’s route and score how smooth it was.")
                DefinitionRow(
                    term: "Where",
                    text: "On your phone unless you turn on backup. Never sold, never shared with insurers.")
            }
            .padding(DPSpace.s4)
            .dpCard()
            .overlay(
                RoundedRectangle(cornerRadius: DPRadius.card, style: .continuous)
                    .strokeBorder(DPColor.accent.opacity(0.5), lineWidth: 1))
            Toggle(isOn: $model.autoTripsOn) {
                VStack(alignment: .leading, spacing: DPSpace.s0_5) {
                    Label { Text("Record drives automatically") } icon: {
                        DPIcon.bolt.image.resizable().frame(width: 18, height: 18)
                    }
                    .font(DPFont.body.weight(.semibold))
                    Text(model.autoTripsOn ? "iOS will ask to allow location “Always”." : "Off until you turn it on")
                        .font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
                }
            }
            .tint(DPColor.accent)
            .padding(DPSpace.s4)
            .dpCard()
            .sensoryFeedback(.selection, trigger: model.autoTripsOn)
            if model.alwaysDeclined {
                Text(
                    "Manual trips it is. Location stays “While Using”. Start drives yourself anytime, and switch "
                        + "automatic trips on later in Settings."
                )
                .font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                .padding(DPSpace.s4)
                .background(DPColor.accentContainer.opacity(0.5), in: RoundedRectangle(cornerRadius: DPRadius.lg))
            }
            Text("You can change this anytime in Settings › Capture.")
                .font(DPFont.meta).foregroundStyle(DPColor.text3)
        } dock: {
            DPPrimaryButton(model.autoTripsOn ? "Continue" : "Continue with manual trips") {
                Task { await model.continueAutoTrips() }
            }
            .disabled(model.isRequesting)
        }
    }
}

private struct DefinitionRow: View {
    let term: String
    let text: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: DPSpace.s3) {
            Text(term).font(DPFont.meta.weight(.semibold)).foregroundStyle(DPColor.text2).frame(width: 52, alignment: .leading)
            Text(text).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - S-ONB-10 Notifications

private struct NotificationsPage: View {
    let model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(step: .notifications, canGoBack: model.canGoBack, onBack: model.back) {
            VStack(spacing: DPSpace.s2) {
                NotificationPreview(title: "Drive decoded.", message: "Thursday morning · Home → Work")
                NotificationPreview(title: "Capture needs a fix", message: "Location is set to While Using.")
                NotificationPreview(title: "Your week, decoded", message: "4 drives · 63 km")
            }
            .opacity(model.notificationsDenied ? 0.4 : 1)
            .accessibilityHidden(true)
            if model.notificationsDenied {
                OnboardingHeading(
                    title: "Notifications are off",
                    lead: "You won’t hear when a drive is decoded or when capture needs a fix. Your drives still "
                        + "record, and you can turn notifications on in Settings anytime.")
                StatusRow(label: "Notifications", value: "Off")
            } else {
                OnboardingHeading(
                    title: "Know when your drive is decoded", lead: "Three kinds of notification, and nothing else.")
                DPCardStack {
                    ChecklistRow(.story, tone: .accent, title: "Drive decoded")
                    ChecklistRow(.info, tone: .accent, title: "Capture problems")
                    ChecklistRow(.sparkle, tone: .accent, title: "Weekly recap")
                    ChecklistRow(.close, tone: .neutral, title: "Never while you drive")
                }
            }
        } dock: {
            DPPrimaryButton("Continue") { Task { await model.continueNotifications() } }
                .disabled(model.isRequesting)
            if !model.notificationsDenied {
                DPSecondaryButton("Not now") { model.skipNotifications() }
            }
        }
    }
}

private struct NotificationPreview: View {
    let title: String
    let message: String

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            LoopGlyph(size: 22).foregroundStyle(DPColor.accent)
                .frame(width: 36, height: 36)
                .background(Color(rgba: 0x0A14_20FF), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(DPFont.bodySecondary.weight(.semibold)).foregroundStyle(DPColor.text)
                Text(message).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            }
            Spacer()
            Text("now").font(DPFont.caption).foregroundStyle(DPColor.text3)
        }
        .padding(DPSpace.s3)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

// MARK: - S-ONB-13 Help improve

private struct HelpImprovePage: View {
    @Bindable var model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(step: .helpImprove, canGoBack: model.canGoBack, alignment: .leading, onBack: model.back) {
            HStack(spacing: DPSpace.s3) {
                GlyphDisc(.insights, tone: .accent, size: 64)
                GlyphDisc(.shield, tone: .accent, size: 64)
            }
            OnboardingHeading(
                title: "Help improve DrivePerf",
                lead: "Two separate choices. Usage statistics stay off unless you turn them on.", alignment: .leading)
            ConsentCard(
                isOn: $model.shareUsageStatistics, title: "Share usage statistics",
                detail: "Which screens and features get used. Never your locations, routes, speeds or car names.")
            ConsentCard(
                isOn: $model.sendCrashReports, title: "Send crash reports",
                detail: "If DrivePerf crashes, the device, OS and app version help us fix it. Never includes locations.")
            Text(
                "Change these anytime in Privacy Centre › Analytics and diagnostics. Nothing is sent before you "
                    + "continue."
            )
            .font(DPFont.meta).foregroundStyle(DPColor.text3)
        } dock: {
            DPPrimaryButton("Continue") { Task { await model.continueHelpImprove() } }
        }
    }
}

struct ConsentCard: View {
    @Binding var isOn: Bool
    let title: String
    let detail: String

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: DPSpace.s0_5) {
                Text(title).font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                Text(detail).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            }
        }
        .tint(DPColor.accent)
        .padding(DPSpace.s4)
        .dpCard()
        .overlay(
            RoundedRectangle(cornerRadius: DPRadius.card, style: .continuous)
                .strokeBorder(isOn ? DPColor.accent : .clear, lineWidth: 1.5))
        .sensoryFeedback(.selection, trigger: isOn)
    }
}

// MARK: - S-ONB-15 Ready

private struct ReadyPage: View {
    let model: OnboardingViewModel

    var body: some View {
        OnboardingScaffold(step: .ready, canGoBack: model.canGoBack, onBack: model.back) {
            LoopMark(size: 128)
            OnboardingHeading(
                title: model.isManualOnly ? "You’re set for manual trips." : "You’re set.",
                lead: model.isManualOnly
                    ? "Tap Start when you set off and Stop when you park. Your first Story appears right after."
                    : "Just drive. Your first Story appears when you park.")
            Label { Text("Pro Preview · day 1 of 14 · no card") } icon: {
                DPIcon.sparkle.image.resizable().frame(width: 14, height: 14)
            }
            .font(DPFont.meta.weight(.semibold))
            .foregroundStyle(DPColor.accentText)
            .padding(.horizontal, DPSpace.s3)
            .padding(.vertical, DPSpace.s1_5)
            .background(DPColor.accentContainer, in: Capsule())
            DPCardStack {
                SummaryRow(title: "Automatic trips", value: model.isManualOnly ? "Off" : "On", on: !model.isManualOnly)
                SummaryRow(
                    title: "Location", value: locationText, on: model.permissions.location.canRecord)
                SummaryRow(
                    title: "Motion & Fitness", value: model.permissions.motion == .authorized ? "On" : "Off",
                    on: model.permissions.motion == .authorized)
                SummaryRow(
                    title: "Notifications", value: model.permissions.notifications == .authorized ? "On" : "Off",
                    on: model.permissions.notifications == .authorized)
            }
        } dock: {
            DPPrimaryButton("Go to Drives") { model.finish() }
        }
    }

    private var locationText: String {
        switch model.permissions.location {
        case .always: "Always"
        case .whenInUse: "While Using"
        default: "Off"
        }
    }
}

private struct SummaryRow: View {
    let title: String
    let value: String
    let on: Bool

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            GlyphDisc(on ? .check : .minus, tone: on ? .positive : .neutral)
            Text(title).font(DPFont.body).foregroundStyle(DPColor.text)
            Spacer()
            Text(value).font(DPFont.body).foregroundStyle(DPColor.text2)
        }
        .padding(.vertical, DPSpace.s3)
        .padding(.horizontal, DPSpace.s4)
        .accessibilityElement(children: .combine)
    }
}

struct StatusRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label).font(DPFont.body).foregroundStyle(DPColor.text)
            Spacer()
            Text(value).font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.warning)
        }
        .padding(DPSpace.s4)
        .dpCard(radius: DPRadius.md)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label): \(value)")
    }
}
