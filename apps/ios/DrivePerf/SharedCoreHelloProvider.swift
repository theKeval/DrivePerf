import DPSharedBridge
import DrivePerfShared

/// Calls the shared Kotlin core: `DrivePerfCore.hello()` from `:shared:di`, exported by `:shared:ios-export`.
struct SharedCoreHelloProvider: HelloProviding {
    func hello() -> HelloContent {
        let message = DrivePerfCore.shared.hello()
        return HelloContent(title: message.title, subtitle: message.subtitle)
    }
}
