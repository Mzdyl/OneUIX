package io.github.soclear.oneuix.util

import android.util.Log

private const val TAG = "OneUIX-Shell"

fun putSettings(namespace: String, key: String, value: String): Boolean {
    return try {
        val command = "settings put $namespace $key $value"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to put settings", t)
        false
    }
}

fun getSettings(namespace: String, key: String): String? {
    return try {
        val command = "settings get $namespace $key"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val result = process.inputStream.bufferedReader().use { it.readText().trim() }
        process.waitFor()
        if (result == "null" || result.isEmpty()) null else result
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to get settings", t)
        null
    }
}

fun launchActivity(packageName: String, activityName: String): Boolean {
    return try {
        val command = "am start -n $packageName/$activityName"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to launch activity", t)
        false
    }
}

fun setNavigationBarGestureHint(hide: Boolean): Boolean {
    var allSuccess = true

    val hintValue = if (hide) "0" else "1"
    if (!putSettings("global", "navigation_bar_gesture_hint", hintValue)) {
        allSuccess = false
    }

    val switchValue = if (hide) "1" else "0"
    if (!putSettings("global", "navigationbar_switch_apps_when_hint_hidden", switchValue)) {
        allSuccess = false
    }

    val flagsValue = if (hide) "4" else "0"
    if (!putSettings("global", "navigationbar_splugin_flags", flagsValue)) {
        allSuccess = false
    }

    return allSuccess
}

fun restartSystemUI(): Boolean {
    return try {
        val command = "pkill -f com.android.systemui"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to restart SystemUI", t)
        false
    }
}
