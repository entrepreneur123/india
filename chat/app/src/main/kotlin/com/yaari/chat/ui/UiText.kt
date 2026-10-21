package com.yaari.chat.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** A translatable message produced outside the UI (ViewModel), resolved in the user's language. */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())

    fun get(context: Context): String = context.getString(id, *args.toTypedArray())

    @Composable
    fun text(): String = stringResource(id, *args.toTypedArray())
}
