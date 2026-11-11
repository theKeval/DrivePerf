import DPSharedBridge
import DrivePerfShared
import Foundation

// Field-by-field conversion between the Swift mirrors in DPSharedBridge and the Kotlin types exported by
// DrivePerfShared. Both modules use the same names, so every type here is module-qualified.

enum KotlinMapping {
    // MARK: Samples → Kotlin

    static func kotlin(_ v: DPSharedBridge.Vec3) -> DrivePerfShared.Vec3 {
        DrivePerfShared.Vec3(x: v.x, y: v.y, z: v.z)
    }

    static func kotlin(_ q: DPSharedBridge.Quat) -> DrivePerfShared.Quat {
        DrivePerfShared.Quat(w: q.w, x: q.x, y: q.y, z: q.z)
    }

    static func kotlin(_ p: DPSharedBridge.GeoPoint) -> DrivePerfShared.GeoPoint {
        DrivePerfShared.GeoPoint(lat: p.lat, lng: p.lng)
    }

    static func kotlin(_ s: DPSharedBridge.LocationSample) -> DrivePerfShared.LocationSample {
        DrivePerfShared.LocationSample(
            tMonoNs: s.tMonoNs, wallMs: s.wallMs, lat: s.lat, lng: s.lng, altM: boxed(s.altM), hAccM: boxed(s.hAccM),
            vAccM: boxed(s.vAccM), speedMps: boxed(s.speedMps), speedAccMps: boxed(s.speedAccMps),
            courseDeg: boxed(s.courseDeg), courseAccDeg: boxed(s.courseAccDeg), provider: s.provider.map(kotlin),
            ageMs: s.ageMs.map { KotlinLong(longLong: $0) })
    }

    static func kotlin(_ p: DPSharedBridge.FixProvider) -> DrivePerfShared.FixProvider {
        switch p {
        case .gnss: .gnss
        case .fused: .fused
        case .network: .network
        case .accessory: .accessory
        case .simulated: .simulated
        }
    }

    static func kotlin(_ s: DPSharedBridge.MotionSample) -> DrivePerfShared.MotionSample {
        DrivePerfShared.MotionSample(
            tMonoNs: s.tMonoNs, userAccelG: kotlin(s.userAccelG), gravityUp: kotlin(s.gravityUp),
            rotRateDps: kotlin(s.rotRateDps), attitude: s.attitude.map(kotlin))
    }

    static func kotlin(_ s: DPSharedBridge.RecordedAccelSample) -> DrivePerfShared.RecordedAccelSample {
        DrivePerfShared.RecordedAccelSample(tMonoNs: s.tMonoNs, accelG: kotlin(s.accelG))
    }

    static func kotlin(_ s: DPSharedBridge.ActivitySample) -> DrivePerfShared.ActivitySample {
        DrivePerfShared.ActivitySample(
            tMonoNs: s.tMonoNs, wallMs: s.wallMs, type: kotlin(s.type), confidence: kotlin(s.confidence))
    }

    static func kotlin(_ type: DPSharedBridge.ActivityType) -> DrivePerfShared.ActivityType {
        switch type {
        case .stationary: .stationary
        case .walking: .walking
        case .running: .running
        case .cycling: .cycling
        case .automotive: .automotive
        case .unknown: .unknown
        }
    }

    static func kotlin(_ confidence: DPSharedBridge.ActivityConfidence) -> DrivePerfShared.ActivityConfidence {
        switch confidence {
        case .low: .low
        case .medium: .medium
        case .high: .high
        }
    }

    static func kotlin(_ c: DPSharedBridge.MotionCapability) -> DrivePerfShared.MotionCapability {
        DrivePerfShared.MotionCapability(hasGyroscope: c.hasGyroscope, maxRateHz: Int32(clamping: c.maxRateHz))
    }

    static func kotlin(_ p: DPSharedBridge.PowerStatus) -> DrivePerfShared.PowerStatus {
        DrivePerfShared.PowerStatus(
            batteryPct: p.batteryPct.map { KotlinInt(int: Int32(clamping: $0)) }, isCharging: p.isCharging,
            lowPowerMode: p.lowPowerMode, thermal: kotlin(p.thermal))
    }

    static func kotlin(_ t: DPSharedBridge.ThermalState) -> DrivePerfShared.ThermalState {
        switch t {
        case .nominal: .nominal
        case .fair: .fair
        case .serious: .serious
        case .critical: .critical
        }
    }

    static func kotlin(_ w: DPSharedBridge.WakeEvent) -> DrivePerfShared.WakeEvent {
        DrivePerfShared.WakeEvent(kind: kotlin(w.kind), tMonoNs: w.tMonoNs, wallMs: w.wallMs, sourceDetail: w.sourceDetail)
    }

    static func kotlin(_ k: DPSharedBridge.WakeKind) -> DrivePerfShared.WakeKind {
        switch k {
        case .parkedExit: .parkedExit
        case .moveSignal: .moveSignal
        case .activityVehicle: .activityVehicle
        case .manual: .manual
        case .relaunch: .relaunch
        }
    }

    static func kotlin(_ r: DPSharedBridge.FileResult) -> DrivePerfShared.FileResult {
        switch r {
        case .ok: .ok
        case .diskFull: .diskFull
        case .unavailable: .unavailable
        case .ioError: .ioError
        }
    }

    static func kotlin(_ f: DPSharedBridge.StoredFile) -> DrivePerfShared.StoredFile {
        DrivePerfShared.StoredFile(sizeBytes: f.sizeBytes, sha256: f.sha256, compressed: f.compressed)
    }

    // MARK: Kotlin → Swift

    static func swift(_ p: DrivePerfShared.GeoPoint) -> DPSharedBridge.GeoPoint {
        DPSharedBridge.GeoPoint(lat: p.lat, lng: p.lng)
    }

    static func swift(_ k: DrivePerfShared.TripFileKind) -> DPSharedBridge.TripFileKind {
        switch k {
        case .r0: .r0
        case .r1: .r1
        case .preview: .preview
        }
    }

    static func swift(_ s: DrivePerfShared.RecorderStatus) -> DPSharedBridge.RecorderStatus {
        DPSharedBridge.RecorderStatus(
            state: swift(s.state), tripId: s.tripId, isManual: s.isManual, elapsedS: s.elapsedS.rounded(.down),
            distanceM: s.distanceM, autoDetectEnabled: s.autoDetectEnabled, startFailed: s.startFailed)
    }

    static func swift(_ state: DrivePerfShared.RecorderState) -> DPSharedBridge.RecorderState {
        switch state {
        case .idle: .idle
        case .candidate: .candidate
        case .recording: .recording
        case .paused: .paused
        case .finishing: .finishing
        }
    }

    /// A listed trip as the drive list shows it. Recording and discarded trips are never listed (§1.7 of the data
    /// model); a manual trip under 500 m or 120 s is "Short trip" (§1.6). Sample counts are not kept by the core.
    static func summary(_ trip: DrivePerfShared.Trip) -> TripSummary? {
        let distance = trip.distanceM?.doubleValue ?? 0
        let duration = trip.durationS?.doubleValue ?? 0
        let status: DPSharedBridge.TripStatus
        switch trip.status {
        case .interrupted: status = .interrupted
        case .finalised:
            let short = distance < shortTripM || duration < shortTripS
            status = trip.source == .manual && short ? .short : .finalised
        case .recording, .discarded: return nil
        }
        let started = Date(timeIntervalSince1970: TimeInterval(trip.startedAt) / 1000)
        let ended = trip.endedAt.map { Date(timeIntervalSince1970: TimeInterval($0.int64Value) / 1000) }
        return TripSummary(
            id: trip.id, source: trip.source == .manual ? .manual : .auto, status: status, startedAt: started,
            endedAt: ended ?? started.addingTimeInterval(duration), distanceM: distance, samples: SampleCounts(),
            kind: swift(trip.kind), correction: trip.correction.map(swift),
            correctedAt: trip.correctedAt.map { Date(wallMs: $0.int64Value) }, question: question(trip),
            quality: trip.quality.map(swift), stats: stats(trip))
    }

    private static let shortTripM = 500.0
    private static let shortTripS = 120.0

    // MARK: Bytes

    /// Bulk copies through the framework's `Bytes` helper (`:shared:ios-export`); `KotlinByteArray` itself only has
    /// per-byte accessors, too slow for the core's whole-file reads of R0.
    static func kotlin(_ data: Data) -> KotlinByteArray {
        Bytes.shared.fromData(data: data)
    }

    static func swift(_ array: KotlinByteArray) -> Data {
        Bytes.shared.toData(bytes: array)
    }

    private static func boxed(_ value: Double?) -> KotlinDouble? {
        value.map { KotlinDouble(double: $0) }
    }
}
