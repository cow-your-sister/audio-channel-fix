package com.example.audiochannelfix.utils

import android.util.Log
import com.example.audiochannelfix.BuildConfig
import io.github.libxposed.api.XposedInterface

const val LOG_TAG = "audiochannelfix"

var xposedLog: XposedInterface? = null

private fun xposedLog(priority: Int, msg: String, t: Throwable? = null) {
    xposedLog?.log(priority, LOG_TAG, msg, t)
}

fun logD(msg: String, t: Throwable? = null) {
    if (BuildConfig.DEBUG) {
        Log.d(LOG_TAG, msg, t)
        xposedLog(Log.DEBUG, msg, t)
    }
}

fun logE(msg: String, t: Throwable? = null) {
    Log.e(LOG_TAG, msg, t)
    xposedLog(Log.ERROR, msg, t)
}

fun logI(msg: String, t: Throwable? = null) {
    Log.i(LOG_TAG, msg, t)
    xposedLog(Log.INFO, msg, t)
}

fun logW(msg: String, t: Throwable? = null) {
    Log.w(LOG_TAG, msg, t)
    xposedLog(Log.WARN, msg, t)
}