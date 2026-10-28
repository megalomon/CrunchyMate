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
        ErrorCode.UNSUPPORTED_SCHEMA -> R.string.error_unsupported_schema
        ErrorCode.STORAGE -> R.string.error_storage
        ErrorCode.INCOMPLETE -> R.string.error_incomplete
        ErrorCode.INTERNAL -> R.string.error_internal
        ErrorCode.CANCELLED -> R.string.error_cancelled
    }

val CoreException.code: ErrorCode
    get() =
        when (this) {
            is CoreException.Network -> ErrorCode.NETWORK
            is CoreException.RateLimited -> ErrorCode.RATE_LIMITED
            is CoreException.Auth -> ErrorCode.AUTH
            is CoreException.Parse -> ErrorCode.PARSE
            is CoreException.Database -> ErrorCode.DATABASE
            is CoreException.UnsupportedSchema -> ErrorCode.UNSUPPORTED_SCHEMA
            is CoreException.Storage -> ErrorCode.STORAGE
            is CoreException.Incomplete -> ErrorCode.INCOMPLETE
            is CoreException.Internal -> ErrorCode.INTERNAL
            is CoreException.Cancelled -> ErrorCode.CANCELLED
        }

@StringRes
fun Throwable.messageRes(): Int =
    when (this) {
        is CoreException -> code.messageRes()
        else -> R.string.error_unknown
    }
