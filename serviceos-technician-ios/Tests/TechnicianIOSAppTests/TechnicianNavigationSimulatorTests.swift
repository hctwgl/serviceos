import Foundation
import ServiceOSCoreClient
import XCTest
@testable import TechnicianIOS

final class TechnicianNavigationSimulatorTests: XCTestCase {
    func testNavigationRequiresBothKnownPageAndEveryCapability() {
        let navigation = MeNavigation(
            contextId: "TECHNICIAN|NETWORK|n1",
            portal: .technician,
            contextVersion: "v1",
            navigationCatalogVersion: "page-registry-v16",
            items: [
                item(pageId: "TECHNICIAN.TASK.LIST", requiredCapabilities: ["task.readAssigned"]),
                item(pageId: "TECHNICIAN.SCHEDULE", requiredCapabilities: ["appointment.read", "task.readAssigned"]),
                item(pageId: "TECHNICIAN.UNKNOWN", requiredCapabilities: []),
            ],
            asOf: Date(timeIntervalSince1970: 1_800_000_000)
        )
        let capabilities = MeCapabilities(
            contextId: "TECHNICIAN|NETWORK|n1",
            portal: .technician,
            capabilityCodes: ["task.readAssigned"],
            contextVersion: "v1",
            asOf: Date(timeIntervalSince1970: 1_800_000_000)
        )

        XCTAssertEqual(
            TechnicianAppTab.visibleTabs(navigation: navigation, capabilities: capabilities),
            [.taskFeed]
        )
    }

    func testNavigationFailsClosedWhenCapabilityIsMissing() {
        let navigation = MeNavigation(
            contextId: "TECHNICIAN|NETWORK|n1",
            portal: .technician,
            contextVersion: "v1",
            navigationCatalogVersion: "page-registry-v16",
            items: [item(pageId: "TECHNICIAN.TASK.LIST", requiredCapabilities: ["task.readAssigned"])],
            asOf: Date(timeIntervalSince1970: 1_800_000_000)
        )
        let capabilities = MeCapabilities(
            contextId: "TECHNICIAN|NETWORK|n1",
            portal: .technician,
            capabilityCodes: [],
            contextVersion: "v1",
            asOf: Date(timeIntervalSince1970: 1_800_000_000)
        )

        XCTAssertTrue(TechnicianAppTab.visibleTabs(navigation: navigation, capabilities: capabilities).isEmpty)
    }

    private func item(pageId: String, requiredCapabilities: [String]) -> MeNavigationItem {
        MeNavigationItem(
            pageId: pageId,
            routeKey: pageId.lowercased(),
            title: pageId,
            order: 1,
            section: "main",
            requiredCapabilities: requiredCapabilities
        )
    }
}
