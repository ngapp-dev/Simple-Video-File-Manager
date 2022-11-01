package com.ngapp.simplevideofilemanager.utils

import android.os.Build
import kotlin.math.roundToInt

fun haveQ(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
fun haveO(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}
fun haveR(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}

fun getSize(size: Int): String? {
    return when {
        size < 1000 * 1024 -> {
            "${(size / 1024.0 * 100.0).roundToInt() / 100.0} KB"
        }
        size < 1000 * 1048576 -> {
            "${(size / 1048576.0 * 100.0).roundToInt() / 100.0} MB"
        }
        else -> {
            null
        }
    }
}