import DPSharedBridge
import Observation

@MainActor
@Observable
public final class HelloViewModel {
    public private(set) var content: HelloContent

    public init(provider: any HelloProviding) {
        content = provider.hello()
    }
}
