package io.github.soclear.oneuix.hook.util

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

object SamsungFeature {
    private const val SEM_CSC_FEATURE = "com.samsung.android.feature.SemCscFeature"
    private const val SEM_FLOATING_FEATURE = "com.samsung.android.feature.SemFloatingFeature"

    fun overrideCscString(
        loadPackageParam: LoadPackageParam,
        label: String,
        resolver: (key: String, defaultValue: String?) -> String?,
    ) {
        hookStringFeature(SEM_CSC_FEATURE, loadPackageParam, label, resolver)
    }

    fun overrideCscBoolean(
        loadPackageParam: LoadPackageParam,
        label: String,
        resolver: (key: String, defaultValue: Boolean?) -> Boolean?,
    ) {
        hookBooleanFeature(SEM_CSC_FEATURE, loadPackageParam, label, resolver)
    }

    fun overrideFloatingBoolean(
        loadPackageParam: LoadPackageParam,
        label: String,
        resolver: (key: String, defaultValue: Boolean?) -> Boolean?,
    ) {
        hookBooleanFeature(SEM_FLOATING_FEATURE, loadPackageParam, label, resolver)
    }

    private fun hookStringFeature(
        className: String,
        loadPackageParam: LoadPackageParam,
        label: String,
        resolver: (key: String, defaultValue: String?) -> String?,
    ) {
        var lastError: Throwable? = null
        var hooked = false

        val singleArgCallback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args[0] as? String ?: return
                resolver(key, null)?.let { param.result = it }
            }
        }

        val doubleArgCallback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args[0] as? String ?: return
                val defaultValue = param.args[1] as? String
                resolver(key, defaultValue)?.let { param.result = it }
            }
        }

        try {
            findAndHookMethod(
                className,
                loadPackageParam.classLoader,
                "getString",
                String::class.java,
                singleArgCallback
            )
            hooked = true
        } catch (t: Throwable) {
            lastError = t
        }

        try {
            findAndHookMethod(
                className,
                loadPackageParam.classLoader,
                "getString",
                String::class.java,
                String::class.java,
                doubleArgCallback
            )
            hooked = true
        } catch (t: Throwable) {
            lastError = t
        }

        if (!hooked) {
            logError("$label failed", lastError)
        }
    }

    private fun hookBooleanFeature(
        className: String,
        loadPackageParam: LoadPackageParam,
        label: String,
        resolver: (key: String, defaultValue: Boolean?) -> Boolean?,
    ) {
        var lastError: Throwable? = null
        var hooked = false

        val singleArgCallback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args[0] as? String ?: return
                resolver(key, null)?.let { param.result = it }
            }
        }

        val doubleArgCallback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args[0] as? String ?: return
                val defaultValue = param.args[1] as? Boolean
                resolver(key, defaultValue)?.let { param.result = it }
            }
        }

        try {
            findAndHookMethod(
                className,
                loadPackageParam.classLoader,
                "getBoolean",
                String::class.java,
                singleArgCallback
            )
            hooked = true
        } catch (t: Throwable) {
            lastError = t
        }

        try {
            findAndHookMethod(
                className,
                loadPackageParam.classLoader,
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                doubleArgCallback
            )
            hooked = true
        } catch (t: Throwable) {
            lastError = t
        }

        if (!hooked) {
            logError("$label failed", lastError)
        }
    }
}
