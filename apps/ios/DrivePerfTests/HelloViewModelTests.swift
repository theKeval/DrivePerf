import DPSharedBridge
import XCTest
@testable import DPFeatures
@testable import DrivePerf

private struct FakeProvider: HelloProviding {
    func hello() -> HelloContent { HelloContent(title: "T", subtitle: "S") }
}

@MainActor
final class HelloViewModelTests: XCTestCase {
    func testShowsWhatTheProviderReturns() {
        let viewModel = HelloViewModel(provider: FakeProvider())

        XCTAssertEqual(viewModel.content, HelloContent(title: "T", subtitle: "S"))
    }

    func testSharedKotlinCoreAnswers() {
        let content = SharedCoreHelloProvider().hello()

        XCTAssertEqual(content.title, "Hello DrivePerf")
        XCTAssertTrue(content.subtitle.hasPrefix("Shared core running on "))
    }
}
