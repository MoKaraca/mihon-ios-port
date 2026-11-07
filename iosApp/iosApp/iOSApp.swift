import SwiftUI
import BackgroundTasks
import MihonShared

@main
struct MihonIOSApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var showPrivacyBlur: Bool = false

    init() {
        registerBackgroundTasks()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                ContentView()
                    .ignoresSafeArea()
                    .onOpenURL { incomingUrl in
                        _ = MihonIosBridge.shared.handleIncomingUrl(url: incomingUrl.absoluteString)
                    }

                if showPrivacyBlur {
                    PrivacyBlurOverlay()
                        .transition(.opacity)
                }
            }
            .onChange(of: scenePhase) { newPhase in
                switch newPhase {
                case .active:
                    withAnimation(.easeOut(duration: 0.15)) {
                        showPrivacyBlur = false
                    }
                case .inactive:
                    withAnimation(.easeIn(duration: 0.10)) {
                        showPrivacyBlur = true
                    }
                case .background:
                    showPrivacyBlur = true
                    MihonIosBridge.shared.onAppDidEnterBackground()
                    scheduleLibraryRefreshTask()
                @unknown default:
                    break
                }
            }
        }
    }

    private func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: "app.mihon.libraryUpdate",
            using: nil
        ) { task in
            task.setTaskCompleted(success: true)
            scheduleLibraryRefreshTask()
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: "app.mihon.backup",
            using: nil
        ) { task in
            _ = MihonIosBridge.shared.stateStore.exportBackupBytes()
            task.setTaskCompleted(success: true)
        }
    }

    private func scheduleLibraryRefreshTask() {
        let request = BGAppRefreshTaskRequest(identifier: "app.mihon.libraryUpdate")
        request.earliestBeginDate = Date(timeIntervalSinceNow: 60 * 60 * 6) // Every 6 hours
        try? BGTaskScheduler.shared.submit(request)
    }
}

private struct PrivacyBlurOverlay: View {
    var body: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()
            VStack(spacing: 12) {
                Text("M")
                    .font(.system(size: 48, weight: .heavy, design: .rounded))
                Text("Mihon")
                    .font(.headline)
            }
        }
    }
}

