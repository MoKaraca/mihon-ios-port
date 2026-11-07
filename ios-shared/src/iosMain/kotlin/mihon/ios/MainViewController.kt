package mihon.ios

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import eu.kanade.tachiyomi.security.IosBiometricAuthenticator
import kotlinx.coroutines.launch
import mihon.ios.domain.MihonIosStateStore
import mihon.ios.ui.MihonIosApp
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import tachiyomi.core.common.preference.IosPreferenceStore
import tachiyomi.core.common.storage.IosStorageFolderProvider

/**
 * Singleton holder for the iOS application state store so Swift/UIKit lifecycle hooks,
 * deep links (`mihon://` / `tachiyomi://`), OAuth callbacks, and BGTaskScheduler jobs
 * can interact directly with the shared Kotlin Multiplatform layer.
 */
object MihonIosBridge {
    val folderProvider = IosStorageFolderProvider()
    val preferenceStore = IosPreferenceStore()
    val stateStore = MihonIosStateStore(
        preferenceStore = preferenceStore,
        backupDirPathProvider = { folderProvider.path() },
    )

    /**
     * Handles incoming deep links and tracker OAuth callback URLs from SwiftUI `onOpenURL`.
     */
    fun handleIncomingUrl(url: String): Boolean {
        return stateStore.trackManager.handleOAuthCallbackUrl(url)
    }

    /**
     * Invoked when the iOS app enters background so biometric lock can engage when configured.
     */
    fun onAppDidEnterBackground() {
        stateStore.lockAppIfRequired()
    }
}

/**
 * Entry point called by Swift's `ContentView.swift` (`UIViewControllerRepresentable`) to host
 * the Mihon Compose Multiplatform UI inside the native iOS app.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val scope = rememberCoroutineScope()
    val keepScreenOn by MihonIosBridge.stateStore.keepScreenOn.collectAsState()

    LaunchedEffect(keepScreenOn) {
        UIApplication.sharedApplication.idleTimerDisabled = keepScreenOn
    }

    MihonIosApp(
        stateStore = MihonIosBridge.stateStore,
        onRequestBiometricUnlock = { callback ->
            scope.launch {
                val unlocked = IosBiometricAuthenticator.authenticate("Unlock Mihon")
                callback(unlocked)
            }
        },
    )
}

