package com.tracksnatcher.app.domain.model

/** Domain-level error taxonomy, surfaced to the UI as friendly, actionable messages. */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** No network / request timed out. */
    data class Network(val throwable: Throwable? = null) : AppError(cause = throwable)

    /** The recognition service could not identify the ambient audio. */
    data object NoMatch : AppError("Couldn't identify that track")

    /** The user has not linked (or has revoked) the service; re-auth required. */
    data class NotAuthorized(val service: MusicService) : AppError("Sign-in required")

    /** Microphone permission was denied. */
    data object MicPermissionDenied : AppError("Microphone permission needed")

    /** Anything else, wrapped with the original cause for logging. */
    data class Unknown(val throwable: Throwable? = null) : AppError(cause = throwable)
}
