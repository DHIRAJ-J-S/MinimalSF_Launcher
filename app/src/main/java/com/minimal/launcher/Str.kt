package com.minimal.launcher

import android.content.Context

/**
 * Text shown to people lives in res/values/strings.xml (and its translations), read through [str].
 * Set from every activity before anything is drawn, so objects without a Context can use it too.
 */
object Str {
    lateinit var app: Context
}

fun str(id: Int, vararg args: Any): String = Str.app.getString(id, *args)
