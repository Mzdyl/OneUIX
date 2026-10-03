package io.github.soclear.oneuix.util

import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

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

suspend fun setNavigationBarGestureHint(hide: Boolean, cacheDirectory: File): Boolean = runInterruptible(Dispatchers.IO) {
    val hintValue = if (hide) "0" else "1"
    val switchValue = if (hide) "1" else "0"
    val flagsValue = if (hide) "4" else "0"
    try {
        runRootCommand(
            "settings put global navigation_bar_gesture_hint $hintValue && " +
                "settings put global navigationbar_switch_apps_when_hint_hidden $switchValue && " +
                "settings put global navigationbar_splugin_flags $flagsValue",
            outputDirectory = cacheDirectory
        ).isSuccess
    } catch (e: InterruptedException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Failed to set navigation bar gesture hint", e)
        false
    }
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
