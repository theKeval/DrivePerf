import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// Root: onboarding, or the shell with the in-drive gate on top (D1, screen-map.md §2.2).
public struct AppRootView: View {
    @State private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase

    public init(dependencies: AppDependencies) {
        _model = State(initialValue: AppModel(dependencies: dependencies))
    }

    public var body: some View {
        Group {
            switch model.route {
            case .onboarding:
                if let onboarding = model.onboarding { OnboardingFlowView(model: onboarding) }
            case .shell:
                ShellView(model: model)
            }
        }
        .task { await model.capture.observe() }
        .task { await model.capture.observeMovement() }
        .task(id: model.capture.heldContent) {
            // Content waits behind the lock: check once a second whether it can open (S-DRV-05 ticks itself while it
            // shows; this covers the time it does not).
            while model.capture.heldContent != nil, !Task.isCancelled {
                model.capture.tick(now: Date())
                try? await Task.sleep(for: .seconds(1))
            }
        }
        .task(id: scenePhase) {
            if scenePhase == .active { await model.capture.refreshReadiness() }
        }
        .task { await model.trips.observe() }
        .sheet(isPresented: $model.isSelfTestPresented) {
            NavigationStack {
                SelfTestView(model: model.profile)
                    .task { await model.profile.runSelfTest() }
            }
        }
    }
}

/// S-NAV-01: four tabs (Drives · Routes · Insights · Garage) on the floating Liquid Glass tab bar, capture status in
/// the bottom accessory on every tab, Profile behind the avatar.
struct ShellView: View {
    enum TabID: Hashable { case drives, routes, insights, garage }

    let model: AppModel
    @State private var selection: TabID = .drives
    @State private var isProfilePresented = false
    @State private var isStatusPresented = false

    var body: some View {
        @Bindable var capture = model.capture
        TabView(selection: $selection) {
            Tab(value: TabID.drives) {
                DrivesHomeView(
                    trips: model.trips, capture: model.capture, onProfile: { isProfilePresented = true },
                    onCaptureStatus: { isStatusPresented = true })
            } label: {
                Label { Text("Drives") } icon: { Image(dp: .drives) }
            }
            Tab(value: TabID.routes) {
                TabPlaceholderView(
                    title: "Routes", icon: .routes,
                    message: "Routes you drive often appear here after a few drives.",
                    onProfile: { isProfilePresented = true })
            } label: {
                Label { Text("Routes") } icon: { Image(dp: .routes) }
            }
            Tab(value: TabID.insights) {
                TabPlaceholderView(
                    title: "Insights", icon: .insights,
                    message: "Your week at a glance appears here once you’ve driven.",
                    onProfile: { isProfilePresented = true })
            } label: {
                Label { Text("Insights") } icon: { Image(dp: .insights) }
            }
            Tab(value: TabID.garage) {
                TabPlaceholderView(
                    title: "Garage", icon: .garage, message: "Add your car to tint your Stories and see per-car stats.",
                    onProfile: { isProfilePresented = true })
            } label: {
                Label { Text("Garage") } icon: { Image(dp: .garage) }
            }
        }
        .tint(DPColor.accent)
        .tabBarMinimizeBehavior(.onScrollDown)
        .tabViewBottomAccessory {
            CaptureStatusAccessory(capture: model.capture, onOpenStatus: { isStatusPresented = true })
        }
        .sheet(isPresented: $isProfilePresented) {
            ProfileSheet(model: model.profile)
        }
        .sheet(isPresented: $isStatusPresented) {
            CaptureStatusSheet(capture: model.capture)
        }
        // The watchdog notification opens the status sheet, which says what to fix (S-LIVE-10 → S-HOME-02).
        .onChange(of: model.statusRequests.isPending, initial: true) { _, pending in
            guard pending, capture.cover == nil else { return }
            model.statusRequests.consume()
            isStatusPresented = true
        }
        .sheet(item: $capture.startProblem) { problem in
            StartProblemSheet(problem: problem, capture: model.capture)
        }
        .fullScreenCover(item: Binding(get: { capture.cover }, set: { if $0 == nil { capture.dismissFinished() } })) {
            cover in
            switch cover {
            case .recording: InDriveView(capture: model.capture)
            case .held: HeldContentView(capture: model.capture)
            case .decoding: DecodingDriveView(carName: capture.carName, onBack: capture.dismissFinished)
            case .finished(let outcome):
                DriveFinishedView(outcome: outcome, carName: capture.carName, onDone: capture.dismissFinished)
            }
        }
        .preferredColorScheme(.dark)
    }
}

/// The glass avatar at the trailing edge of every tab root (S-NAV-01). Local mode has no name, so it shows the
/// Profile glyph.
struct ProfileAvatarButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            DPIcon.profile.image.resizable().frame(width: 20, height: 20)
        }
        .accessibilityLabel("Profile")
    }
}

/// Root of the tabs whose content arrives with later epics (Routes DRIVE-154, Insights DRIVE-141, Garage DRIVE-173).
struct TabPlaceholderView: View {
    let title: String
    let icon: DPIcon
    let message: String
    let onProfile: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: DPSpace.s4) {
                    GlyphDisc(icon, tone: .accent, size: 72)
                    Text(message)
                        .font(DPFont.body)
                        .foregroundStyle(DPColor.text2)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(DPSpace.s8)
                .dpCard()
                .padding(DPLayout.screenMargin)
            }
            .dpScreenBackground()
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { ProfileAvatarButton(action: onProfile) }
            }
        }
    }
}
