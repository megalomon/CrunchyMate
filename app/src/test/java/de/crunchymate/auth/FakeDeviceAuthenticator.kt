package de.crunchymate.auth

class FakeDeviceAuthenticator(
    private val secure: Boolean = false,
    private val succeeds: Boolean = true,
) : DeviceAuthenticator {
    var prompts = 0

    override fun isDeviceSecure() = secure

    override suspend fun authenticate(): Boolean {
        prompts++
        return succeeds
    }
}
