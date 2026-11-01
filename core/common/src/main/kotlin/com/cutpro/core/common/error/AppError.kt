package com.cutpro.core.common.error

sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null
) {
    data class StorageError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class MediaError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class SecurityError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class SerializationError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class ValidationError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class NotFoundError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)
}
