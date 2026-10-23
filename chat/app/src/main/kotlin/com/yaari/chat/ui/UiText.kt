package com.yaari.chat.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** A translatable message produced outside the UI (ViewModel), resolved in the user's language (a UiText argument too). */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())

    fun get(context: Context): String = context.getString(id, *args.map { if (it is UiText) it.get(context) else it }.toTypedArray())

    @Composable
    fun text(): String = stringResource(id, *args.map { if (it is UiText) it.text() else it }.toTypedArray())
}
