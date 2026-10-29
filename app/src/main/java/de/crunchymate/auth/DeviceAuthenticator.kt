package de.crunchymate.auth

/** Geräte-Authentifizierung hinter einem Interface, damit ViewModels ohne echtes BiometricPrompt testbar bleiben. */
interface DeviceAuthenticator {
    /** Ob das Gerät eine Displaysperre hat; ohne sie gibt es nichts abzufragen. */
    fun isDeviceSecure(): Boolean

    /** Fragt Biometrie oder Geräte-PIN ab; true nur bei Erfolg, bei Abbruch und Fehlschlag false. */
    suspend fun authenticate(): Boolean
}
