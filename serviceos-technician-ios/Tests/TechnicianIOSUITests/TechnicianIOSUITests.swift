import XCTest

final class TechnicianIOSUITests: XCTestCase {
    @MainActor
    func testSignedOutShellSupportsAccessibilityContentSize() throws {
        let app = XCUIApplication()
        app.launchEnvironment["SERVICEOS_UI_TEST_RESET_SESSION"] = "1"
        app.launchArguments += [
            "-UIPreferredContentSizeCategoryName",
            "UICTContentSizeCategoryAccessibilityXXXL",
        ]
        app.launch()

        XCTAssertTrue(app.buttons["technician.login"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons["technician.login"].label, "企业账号登录")
        XCTAssertTrue(app.staticTexts["technician.environment"].exists)
        XCTAssertTrue(app.staticTexts["ServiceOS 师傅端"].exists)
    }
}
