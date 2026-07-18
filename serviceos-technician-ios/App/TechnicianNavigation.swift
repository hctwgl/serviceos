import ServiceOSCoreClient
import TechnicianIOSFoundation

enum TechnicianAppTab: String, CaseIterable, Hashable, Identifiable {
    case taskFeed = "TECHNICIAN.TASK.LIST"
    case schedule = "TECHNICIAN.SCHEDULE"
    case sync = "TECHNICIAN.SYNC.SUMMARY"
    case me = "TECHNICIAN.ME"

    var id: String { rawValue }

    var defaultTitle: String {
        switch self {
        case .taskFeed: "任务"
        case .schedule: "日程"
        case .sync: "同步"
        case .me: "我的"
        }
    }

    var systemImage: String {
        switch self {
        case .taskFeed: "list.bullet.rectangle"
        case .schedule: "calendar"
        case .sync: "arrow.triangle.2.circlepath"
        case .me: "person.crop.circle"
        }
    }

    static func visibleTabs(for session: TechnicianSession) -> [TechnicianAppTab] {
        let capabilities = Set(session.capabilities.capabilityCodes)
        let allowedPageIDs = Set(session.navigation.items.lazy
            .filter { item in item.requiredCapabilities.allSatisfy(capabilities.contains) }
            .map(\.pageId))
        return allCases.filter { allowedPageIDs.contains($0.rawValue) }
    }

    func title(in session: TechnicianSession) -> String {
        session.navigation.items.first(where: { $0.pageId == rawValue })?.title ?? defaultTitle
    }
}
