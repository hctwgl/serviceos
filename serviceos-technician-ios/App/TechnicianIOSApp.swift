import SwiftUI

@main
struct TechnicianIOSApp: App {
    @State private var store = TechnicianAppStore()

    var body: some Scene {
        WindowGroup {
            TechnicianRootView(store: store)
                .task { await store.bootstrapIfNeeded() }
        }
    }
}
