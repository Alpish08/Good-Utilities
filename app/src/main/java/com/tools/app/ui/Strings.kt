package com.tools.app.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * The app's text, in the phone's language (or the one picked for Good Utilities in Settings › Apps › Language).
 * English lives in res/values/strings*.xml; French and Turkish in values-fr / values-tr. A language change recreates
 * the screens, so reading through the application's resources is always current.
 */
object Strings {
    lateinit var app: Context
}

/** The string [id], formatted with [args] when there are any (so a plain "50%" is never run through the formatter). */
fun t(@StringRes id: Int, vararg args: Any?): String =
    if (args.isEmpty()) Strings.app.resources.getString(id) else Strings.app.resources.getString(id, *args)

/** The plural [id] for [count] (French and Turkish count differently from English), formatted with [args]. */
fun tp(@PluralsRes id: Int, count: Int, vararg args: Any?): String =
    Strings.app.resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any?>(count) else args))
