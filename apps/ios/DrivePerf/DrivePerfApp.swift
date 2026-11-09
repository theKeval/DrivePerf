import DPFeatures
import SwiftUI

@main
struct DrivePerfApp: App {
    var body: some Scene {
        WindowGroup {
            HelloView(provider: SharedCoreHelloProvider())
        }
    }
}
