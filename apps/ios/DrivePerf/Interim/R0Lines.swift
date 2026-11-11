import DPSharedBridge
import Foundation

/// The interim R0 capture log: CSV lines, one per sample, prefixed by the stream (`L` location, `M` motion, `A`
/// activity). The shared core replaces it with the real R0 codec; until then a trip's raw samples stay readable.
enum R0Lines {
    static func header(tripId: String, source: TripSource) -> Data {
        Data(
            """
            # DrivePerf interim R0 v1 trip=\(tripId) source=\(source.rawValue)
            # L,t_mono_ns,wall_ms,lat,lng,alt_m,h_acc_m,v_acc_m,speed_mps,speed_acc_mps,course_deg,course_acc_deg
            # M,t_mono_ns,ax_g,ay_g,az_g,gx,gy,gz,rx_dps,ry_dps,rz_dps,qw,qx,qy,qz
            # A,t_mono_ns,wall_ms,type,confidence

            """.utf8)
    }

    static func resumed(at date: Date) -> Data {
        Data("# resumed wall_ms=\(date.wallMs)\n".utf8)
    }

    static func encode(_ samples: [LocationSample]) -> Data {
        var text = ""
        for s in samples {
            text += "L,\(s.tMonoNs),\(s.wallMs),\(s.lat),\(s.lng),\(f(s.altM)),\(f(s.hAccM)),\(f(s.vAccM)),"
            text += "\(f(s.speedMps)),\(f(s.speedAccMps)),\(f(s.courseDeg)),\(f(s.courseAccDeg))\n"
        }
        return Data(text.utf8)
    }

    static func encode(_ samples: [MotionSample]) -> Data {
        var text = ""
        text.reserveCapacity(samples.count * 160)
        for s in samples {
            let a = s.userAccelG
            let g = s.gravityUp
            let r = s.rotRateDps
            let q = s.attitude
            text += "M,\(s.tMonoNs),\(a.x),\(a.y),\(a.z),\(g.x),\(g.y),\(g.z),\(r.x),\(r.y),\(r.z),"
            text += "\(f(q?.w)),\(f(q?.x)),\(f(q?.y)),\(f(q?.z))\n"
        }
        return Data(text.utf8)
    }

    static func encode(_ samples: [ActivitySample]) -> Data {
        Data(
            samples.map { "A,\($0.tMonoNs),\($0.wallMs),\($0.type.rawValue),\($0.confidence.rawValue)\n" }.joined().utf8)
    }

    private static func f(_ value: Double?) -> String { value.map { "\($0)" } ?? "" }
}
