package com.shilapi.xcertplay.i18n

import android.content.Context
import androidx.annotation.StringRes

/** Deferred text for background state and validation that has no Android Context. */
data class UiText(@param:StringRes val resource: Int, val arguments: List<Any> = emptyList()) {
    fun resolve(context: Context): String =
        if (arguments.isEmpty()) context.getString(resource)
        else
            context.getString(
                resource,
                *arguments.map { if (it is UiText) it.resolve(context) else it }.toTypedArray(),
            )
}

interface LocalizedFailure {
    val uiText: UiText?
}

class UiException(override val uiText: UiText) :
    IllegalArgumentException(uiText.toString()), LocalizedFailure

fun requireUi(value: Boolean, message: () -> UiText) {
    if (!value) throw UiException(message())
}

class UiStateException(override val uiText: UiText) :
    IllegalStateException(uiText.toString()), LocalizedFailure

class UiIOException(override val uiText: UiText) :
    java.io.IOException(uiText.toString()), LocalizedFailure

fun checkUi(value: Boolean, message: () -> UiText) {
    if (!value) throw UiStateException(message())
}

fun errorUi(message: UiText): Nothing = throw UiException(message)

/** Preserve platform/provider diagnostic details when there is no app-owned message. */
fun Throwable.userMessage(context: Context): String =
    (this as? LocalizedFailure)?.uiText?.resolve(context) ?: message ?: javaClass.simpleName
