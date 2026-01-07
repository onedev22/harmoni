package com.amurayada.music.utils

import android.content.Context
import android.content.ContextWrapper
import com.amurayada.music.MainActivity

fun Context.findMainActivity(): MainActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is MainActivity) return context
        context = context.baseContext
    }
    return null
}
