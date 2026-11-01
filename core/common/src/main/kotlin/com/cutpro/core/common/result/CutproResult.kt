package com.cutpro.core.common.result

import com.cutpro.core.common.error.AppError

sealed interface CutproResult<out T> {
    data class Success<out T>(val data: T) : CutproResult<T>
    data class Error(val error: AppError) : CutproResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw error.cause ?: IllegalStateException(error.message)
    }

    fun <R> map(transform: (T) -> R): CutproResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    fun <R> flatMap(transform: (T) -> CutproResult<R>): CutproResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }

    fun onSuccess(action: (T) -> Unit): CutproResult<T> {
        if (this is Success) action(data)
        return this
    }

    fun onError(action: (AppError) -> Unit): CutproResult<T> {
        if (this is Error) action(error)
        return this
    }
}
