import ActivityKit
import Foundation

struct CaptureActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var startedAt: Date
        var distanceKm: Double
    }

    var mode: String
}
