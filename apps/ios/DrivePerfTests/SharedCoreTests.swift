import XCTest
@testable import DrivePerf

/// The app still reaches the Kotlin core through the static framework (DRIVE-59). Profile › About shows this line.
@MainActor
final class SharedCoreTests: XCTestCase {
    func testSharedKotlinCoreAnswers() {
        let content = SharedCoreHelloProvider().hello()

        XCTAssertEqual(content.title, "Hello DrivePerf")
        XCTAssertTrue(content.subtitle.hasPrefix("Shared core running on "))
    }
}
