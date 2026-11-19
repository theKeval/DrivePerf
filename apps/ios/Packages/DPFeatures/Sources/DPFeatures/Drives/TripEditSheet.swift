import DPDesignSystem
import DPSharedBridge
import SwiftUI

// S-TRP-02 Edit trip, S-TRP-03 Delete trip and the saved toast (DRIVE-131, F-HIS-02), drawn from the shared presenter's
// state through `TripListViewModel`: every word is already formatted there (decision 3).

/// "Edit" in trip detail's action row (S-TRP-01 → S-TRP-02).
struct TripEditButton: View {
    let trips: TripListViewModel
    let tripId: String

    var body: some View {
        Button {
            trips.edit(.open(tripId: tripId, pane: .main))
        } label: {
            Label {
                Text("Edit").font(DPFont.button)
            } icon: {
                Image(systemName: "pencil")
            }
            .frame(maxWidth: .infinity, minHeight: DPLayout.touchMin)
        }
        .buttonStyle(.glass)
        .accessibilityHint("Change the trip type or car, or delete the drive")
        .accessibilityIdentifier("tripDetail.edit")
    }
}

extension View {
    /// S-TRP-02 over trip [tripId], its saved toast, and leaving the detail once S-TRP-03 deleted the trip.
    func tripEditing(trips: TripListViewModel, tripId: String) -> some View {
        modifier(TripEditing(trips: trips, tripId: tripId))
    }

    /// "Drive deleted" on a list, after S-TRP-03 (DRIVE-131).
    func tripDeletedToast(trips: TripListViewModel) -> some View {
        modifier(TripDeletedToast(trips: trips))
    }
}

private struct TripEditing: ViewModifier {
    let trips: TripListViewModel
    let tripId: String
    @Environment(\.dismiss) private var dismiss

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: Binding(
                get: { trips.editForm(id: tripId) != nil }, set: { if !$0 { trips.edit(.close) } })
            ) {
                TripEditSheet(trips: trips, tripId: tripId)
            }
            .overlay(alignment: .bottom) {
                if let toast = trips.editToast, toast.tripId == tripId {
                    ToastView(text: toast.text, action: toast.undoLabel) { trips.edit(.undo) }
                        .task(id: toast) {
                            try? await Task.sleep(for: .seconds(8))
                            if !Task.isCancelled { trips.edit(.dismissMessage) }
                        }
                }
            }
            .onChange(of: trips.deletedTrip) { _, deleted in
                if deleted?.tripId == tripId { dismiss() }
            }
    }
}

private struct TripDeletedToast: ViewModifier {
    let trips: TripListViewModel

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let deleted = trips.deletedTrip {
                ToastView(text: deleted.message, action: nil, onAction: {})
                    .task(id: deleted) {
                        try? await Task.sleep(for: .seconds(3))
                        if !Task.isCancelled { trips.deletionShown() }
                    }
            }
        }
    }
}

/// A glass capsule at the bottom: "Marked as passenger · scores updated" with "Undo", or "Drive deleted".
private struct ToastView: View {
    let text: String
    let action: String?
    let onAction: () -> Void

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            Text(text).font(DPFont.meta).foregroundStyle(DPColor.text)
            if let action {
                Button(action, action: onAction).font(DPFont.meta.weight(.semibold)).tint(DPColor.accent)
            }
        }
        .padding(.horizontal, DPSpace.s4)
        .frame(minHeight: DPLayout.touchMin)
        .glassEffect(.regular, in: .capsule)
        .padding(.bottom, DPSpace.s4)
        .accessibilityElement(children: .combine)
        .onAppear { AccessibilityNotification.Announcement(text).post() }
    }
}

/// S-TRP-02: a large sheet with × and ✓, whose type and car pickers push inside it.
struct TripEditSheet: View {
    let trips: TripListViewModel
    let tripId: String

    var body: some View {
        NavigationStack {
            if let form = trips.editForm(id: tripId) {
                page(form)
                    .navigationTitle(title(form))
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar { toolbar(form) }
                    .animation(.default, value: form.pane)
            }
        }
        .presentationDetents([.large])
        .alert(
            trips.deleteConfirmation?.title ?? "",
            isPresented: Binding(get: { trips.deleteConfirmation.map { !$0.isDeleting } ?? false }, set: { _ in }),
            presenting: trips.deleteConfirmation
        ) { confirmation in
            Button(confirmation.cancel, role: .cancel) { trips.edit(.cancelDelete) }
            Button(confirmation.confirm, role: .destructive) { trips.edit(.confirmDelete) }
        } message: { confirmation in
            Text(
                [confirmation.message, "\(confirmation.tripTitle) · \(confirmation.tripSubtitle)", confirmation.error]
                    .compactMap { $0 }.joined(separator: "\n\n"))
        }
    }

    private func title(_ form: TripEditForm) -> String {
        switch form.pane {
        case .main: form.title
        case .type: "Trip type"
        case .car: "Car"
        }
    }

    @ToolbarContentBuilder
    private func toolbar(_ form: TripEditForm) -> some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            if form.pane == .main {
                Button("Cancel", systemImage: "xmark") { trips.edit(.close) }
            } else {
                Button("Back to Edit trip", systemImage: "chevron.left") { trips.edit(.showPane(.main)) }
            }
        }
        ToolbarItem(placement: .confirmationAction) {
            Button("Save", systemImage: "checkmark") { trips.edit(.save) }
                .buttonStyle(.glassProminent)
                .tint(DPColor.accent)
                .accessibilityIdentifier("tripEdit.save")
        }
    }

    @ViewBuilder
    private func page(_ form: TripEditForm) -> some View {
        switch form.pane {
        case .main: mainPage(form)
        case .type: typePage(form)
        case .car: carPage(form)
        }
    }

    private func mainPage(_ form: TripEditForm) -> some View {
        List {
            Section {
                HStack(spacing: DPSpace.s3) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(form.tripTitle).font(DPFont.cardTitle)
                        Text(form.tripSubtitle).font(DPFont.meta).foregroundStyle(DPColor.text2)
                        Text(form.tripStats).font(DPFont.meta).foregroundStyle(DPColor.text2)
                    }
                    Spacer()
                    if let score = form.score { ScoreBadgeView(badge: score) }
                }
                .accessibilityElement(children: .combine)
            }
            Section {
                navRow(title: "Trip type", value: form.typeValue, detail: form.typeDetail) { trips.edit(.showPane(.type)) }
                    .accessibilityIdentifier("tripEdit.type")
                if form.hasCars {
                    navRow(title: "Car", value: form.carValue ?? "", detail: form.carDetail) { trips.edit(.showPane(.car)) }
                        .accessibilityIdentifier("tripEdit.car")
                }
            } footer: {
                Text(form.caption)
            }
            if let banner = form.changedBanner {
                Section { Label(banner, systemImage: "info.circle").font(DPFont.meta) }
            }
            Section {
                Button(role: .destructive) {
                    trips.edit(.askDelete(tripId: form.tripId))
                } label: {
                    Label(form.deleteLabel, systemImage: "trash")
                }
                .accessibilityIdentifier("tripEdit.delete")
            }
        }
    }

    private func navRow(title: String, value: String, detail: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(DPFont.body).foregroundStyle(DPColor.text)
                    if let detail { Text(detail).font(DPFont.meta).foregroundStyle(DPColor.text2) }
                }
                Spacer()
                Text(value).font(DPFont.body).foregroundStyle(DPColor.text2)
                Image(systemName: "chevron.right").font(DPFont.meta).foregroundStyle(DPColor.text3)
            }
            .frame(minHeight: DPLayout.touchMin)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([title, value, detail].compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(.isButton)
    }

    private func typePage(_ form: TripEditForm) -> some View {
        List {
            Section {
                ForEach(form.types) { option in
                    radioRow(
                        title: option.title, detail: option.detail, tag: option.isCurrent ? "Current" : nil,
                        isSelected: option.isSelected, label: option.accessibilityLabel
                    ) { trips.edit(.pickType(option.kind)) }
                }
            } header: {
                Text(form.tripTitle)
            } footer: {
                Text(form.typeFooter)
            }
            if let banner = form.typeBanner {
                Section { Label(banner, systemImage: "person.fill").font(DPFont.meta) }
            }
        }
    }

    private func carPage(_ form: TripEditForm) -> some View {
        List {
            Section {
                ForEach(form.cars) { car in
                    radioRow(
                        title: car.name, detail: car.detail, tag: car.isDefault ? "Default" : nil, isSelected: car.isSelected,
                        label: car.accessibilityLabel
                    ) { trips.edit(.pickCar(vehicleId: car.id)) }
                }
            } header: {
                Text(form.tripTitle)
            } footer: {
                Text(form.carNote)
            }
        }
    }

    private func radioRow(
        title: String, detail: String?, tag: String?, isSelected: Bool, label: String, action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: DPSpace.s3) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(isSelected ? DPColor.accent : DPColor.text3)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(DPFont.body).foregroundStyle(DPColor.text)
                    if let detail { Text(detail).font(DPFont.meta).foregroundStyle(DPColor.text2) }
                }
                Spacer()
                if let tag { TagLabel(text: tag) }
            }
            .frame(minHeight: DPLayout.touchMin)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: isSelected)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}
