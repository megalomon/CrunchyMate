package de.crunchymate.core

import androidx.annotation.StringRes
import de.crunchymate.R
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.ErrorCode

// Kein else-Zweig: ein neuer ErrorCode soll die Kompilierung brechen
@StringRes
fun ErrorCode.messageRes(): Int =
    when (this) {
        ErrorCode.NETWORK -> R.string.error_network
        ErrorCode.RATE_LIMITED -> R.string.error_rate_limited
        ErrorCode.AUTH -> R.string.error_auth
        ErrorCode.PARSE -> R.string.error_parse
        ErrorCode.DATABASE -> R.string.error_database
        ErrorCode.CANCELLED -> R.string.error_cancelled
    }

@StringRes
fun Throwable.messageRes(): Int =
    when (this) {
        is CoreException.Network -> ErrorCode.NETWORK.messageRes()
        is CoreException.RateLimited -> ErrorCode.RATE_LIMITED.messageRes()
        is CoreException.Auth -> ErrorCode.AUTH.messageRes()
        is CoreException.Parse -> ErrorCode.PARSE.messageRes()
        is CoreException.Database -> ErrorCode.DATABASE.messageRes()
        is CoreException.Cancelled -> ErrorCode.CANCELLED.messageRes()
        else -> R.string.error_unknown
    }
