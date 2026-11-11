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
            courseDeg: boxed(s.courseDeg), courseAccDeg: boxed(s.courseAccDeg))
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

    // MARK: Bytes

    /// `KotlinByteArray` has no bulk accessor in the Objective-C export, so this copies byte by byte. Fine for the
    /// ~1 s capture batches; slow for whole-file reads of a long trip (reported to DRIVE-62).
    static func kotlin(_ data: Data) -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(data.count))
        data.withUnsafeBytes { raw in
            for (index, byte) in raw.bindMemory(to: Int8.self).enumerated() {
                array.set(index: Int32(index), value: byte)
            }
        }
        return array
    }

    static func swift(_ array: KotlinByteArray) -> Data {
        let count = Int(array.size)
        var data = Data(count: count)
        data.withUnsafeMutableBytes { raw in
            let bytes = raw.bindMemory(to: Int8.self)
            for index in 0..<count { bytes[index] = array.get(index: Int32(index)) }
        }
        return data
    }

    private static func boxed(_ value: Double?) -> KotlinDouble? {
        value.map { KotlinDouble(double: $0) }
    }
}
