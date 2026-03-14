package io.github.soclear.oneuix.hook.util

import android.app.AndroidAppHelper
import android.app.Application
import android.content.Context
import android.content.Context.CONTEXT_IGNORE_SECURITY
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File

private const val TAG = "OneUIX"

/**
 * 兼容 Xposed 和非 Xposed 环境的日志函数
 * 在 Xposed 环境中使用 XposedBridge.log，否则使用 android.util.Log
 */
fun log(message: String) {
    try {
        XposedBridge.log("$TAG: $message")
    } catch (_: NoClassDefFoundError) {
        Log.d(TAG, message)
    } catch (_: Throwable) {
        Log.d(TAG, message)
    }
}

fun logError(message: String, t: Throwable? = null) {
    val fullMessage = if (t != null) "$message - ${t.message}" else message
    try {
        XposedBridge.log("$TAG: $fullMessage")
        if (t != null) XposedBridge.log(t)
    } catch (_: NoClassDefFoundError) {
        Log.e(TAG, fullMessage, t)
    } catch (_: Throwable) {
        Log.e(TAG, fullMessage, t)
    }
}

fun getSystemContext(): Context {
    val activityThreadClass = findClass("android.app.ActivityThread", null)
    val currentActivityThread = callStaticMethod(activityThreadClass, "currentActivityThread")
    return callMethod(currentActivityThread, "getSystemContext") as Context
}

fun Context.createCurrentContext(): Context = createPackageContext(
    AndroidAppHelper.currentPackageName(),
    CONTEXT_IGNORE_SECURITY
)

fun getCurrentSharedPreferences(name: String): SharedPreferences = getSystemContext()
    .createCurrentContext()
    .getSharedPreferences(name, MODE_PRIVATE)

fun LoadPackageParam.getSharedPreferences(name: String): SharedPreferences = getSystemContext()
    .createPackageContext(packageName, CONTEXT_IGNORE_SECURITY)
    .getSharedPreferences(name, MODE_PRIVATE)

fun getPackageVersionCode(name: String = AndroidAppHelper.currentPackageName()): Long =
    getSystemContext().packageManager.getPackageInfo(name, 0).longVersionCode

val Context.longVersionCode get() = packageManager.getPackageInfo(packageName, 0).longVersionCode

fun afterAttach(action: Context.() -> Unit) {
    val callback = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            action(param.args[0] as Context)
        }
    }
    findAndHookMethod(Application::class.java, "attach", Context::class.java, callback)
}

// 向宿主添加资源，路径为apk文件路径。例如添加模块的资源
fun addAssetPath(path: String) = afterAttach {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val loader = ResourcesLoader()
        val moduleFile = File(path)
        val parcelFileDescriptor = ParcelFileDescriptor.open(
            moduleFile,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
        val provider = ResourcesProvider.loadFromApk(parcelFileDescriptor)
        loader.addProvider(provider)
        resources.addLoaders(loader)
    } else {
        callMethod(assets, "addAssetPath", path)
    }
}

fun xlog(string: String) {
    val result = "\n\n////////////////\n\n////////////////\n\n$string\n\n////////////////\n\n"
    log(result)
}

/**
 * 通过 shell 命令写入 settings 数据库
 * @param namespace 命名空间: system, secure, global
 * @param key 设置项名称
 * @param value 设置值
 * @return 是否执行成功
 */
fun putSettings(namespace: String, key: String, value: String): Boolean {
    return try {
        val command = "settings put $namespace $key $value"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (t: Throwable) {
        log("Failed to put settings - ${t.message}")
        false
    }
}

/**
 * 通过 shell 命令读取 settings 数据库
 * @param namespace 命名空间: system, secure, global
 * @param key 设置项名称
 * @return 设置值，失败返回 null
 */
fun getSettings(namespace: String, key: String): String? {
    return try {
        val command = "settings get $namespace $key"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val result = process.inputStream.bufferedReader().readText().trim()
        if (result == "null" || result.isEmpty()) null else result
    } catch (t: Throwable) {
        log("Failed to get settings - ${t.message}")
        null
    }
}

/**
 * 启动 Activity
 * @param packageName 包名
 * @param activityName Activity 名称
 * @return 是否执行成功
 */
fun launchActivity(packageName: String, activityName: String): Boolean {
    return try {
        val command = "am start -n $packageName/$activityName"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (t: Throwable) {
        log("Failed to launch activity - ${t.message}")
        false
    }
}

/**
 * 设置导航栏手势提示条显示/隐藏
 * 需要修改三个 Settings.Global 参数:
 * - navigation_bar_gesture_hint: 0=隐藏, 1=显示
 * - navigationbar_switch_apps_when_hint_hidden: 1=启用滑动切换应用
 * - navigationbar_splugin_flags: 4=启用导航栏插件功能
 *
 * @param hide true=隐藏手势提示条, false=显示
 * @return 是否全部设置成功
 */
fun setNavigationBarGestureHint(hide: Boolean): Boolean {
    var allSuccess = true

    // 设置1: 隐藏手势提示条
    val hintValue = if (hide) "0" else "1"
    if (!putSettings("global", "navigation_bar_gesture_hint", hintValue)) {
        log("Failed to set navigation_bar_gesture_hint")
        allSuccess = false
    }

    // 设置2: 提示隐藏时切换应用
    val switchValue = if (hide) "1" else "0"
    if (!putSettings("global", "navigationbar_switch_apps_when_hint_hidden", switchValue)) {
        log("Failed to set navigationbar_switch_apps_when_hint_hidden")
        allSuccess = false
    }

    // 设置3: 导航栏插件标志
    val flagsValue = if (hide) "4" else "0"
    if (!putSettings("global", "navigationbar_splugin_flags", flagsValue)) {
        log("Failed to set navigationbar_splugin_flags")
        allSuccess = false
    }

    if (allSuccess) {
        log("Navigation bar gesture hint ${if (hide) "hidden" else "shown"}")
    }

    return allSuccess
}

/**
 * 重启 SystemUI
 * @return 是否执行成功
 */
fun restartSystemUI(): Boolean {
    return try {
        val command = "pkill -f com.android.systemui"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val exitCode = process.waitFor()
        log("Restart SystemUI: ${if (exitCode == 0) "success" else "failed"}")
        exitCode == 0
    } catch (t: Throwable) {
        log("Failed to restart SystemUI - ${t.message}")
        false
    }
}
