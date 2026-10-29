package de.crunchymate.auth

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import de.crunchymate.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Framework-BiometricPrompt mit Biometrie oder Geräte-PIN als Rückfall. */
class BiometricDeviceAuthenticator(
    private val context: Context,
) : DeviceAuthenticator {
    override fun isDeviceSecure(): Boolean = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    override suspend fun authenticate(): Boolean =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            val callback =
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            BiometricPrompt
                .Builder(context)
                .setTitle(context.getString(R.string.auth_adult_title))
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
                .authenticate(signal, context.mainExecutor, callback)
        }
}
