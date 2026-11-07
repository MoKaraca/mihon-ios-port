package eu.kanade.tachiyomi.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

/**
 * Native iOS Biometric & Device Passcode Authenticator using Apple's `LocalAuthentication` (`LAContext`).
 * Supports Face ID, Touch ID, and Passcode fallback for Mihon's "Require authentication" security lock.
 */
object IosBiometricAuthenticator {

    @OptIn(ExperimentalForeignApi::class)
    fun isBiometricAvailable(): Boolean {
        val context = LAContext()
        return context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthentication,
            error = null,
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    suspend fun authenticate(reason: String = "Unlock Mihon"): Boolean = suspendCancellableCoroutine { cont ->
        val context = LAContext()
        val policy = if (context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)) {
            LAPolicyDeviceOwnerAuthenticationWithBiometrics
        } else {
            LAPolicyDeviceOwnerAuthentication
        }

        if (!context.canEvaluatePolicy(policy, error = null)) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        context.evaluatePolicy(
            policy = policy,
            localizedReason = reason,
        ) { success, _ ->
            if (cont.isActive) {
                cont.resume(success)
            }
        }
    }
}

