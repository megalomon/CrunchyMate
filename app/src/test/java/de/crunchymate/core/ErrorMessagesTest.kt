package de.crunchymate.core

import de.crunchymate.R
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessagesTest {
    @Test
    fun errorMessages_mapEveryErrorCode() {
        val resources = ErrorCode.entries.map { it.messageRes() }
        assertEquals(ErrorCode.entries.size, resources.toSet().size)
        assertEquals(false, R.string.error_unknown in resources)
    }

    @Test
    fun throwable_mapsCoreExceptionsToTheirCode() {
        assertEquals(ErrorCode.NETWORK.messageRes(), CoreException.Network("x").messageRes())
        assertEquals(ErrorCode.RATE_LIMITED.messageRes(), CoreException.RateLimited(5L).messageRes())
        assertEquals(ErrorCode.AUTH.messageRes(), CoreException.Auth("x").messageRes())
        assertEquals(ErrorCode.PARSE.messageRes(), CoreException.Parse("x").messageRes())
        assertEquals(ErrorCode.DATABASE.messageRes(), CoreException.Database("x").messageRes())
        assertEquals(ErrorCode.CANCELLED.messageRes(), CoreException.Cancelled().messageRes())
        assertEquals(R.string.error_unknown, IllegalStateException().messageRes())
    }
}
