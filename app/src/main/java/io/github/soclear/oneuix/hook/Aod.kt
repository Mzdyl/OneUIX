package io.github.soclear.oneuix.hook

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.log
import io.github.soclear.oneuix.hook.util.logError

object Aod {
    private var timeoutSeconds: Int = 120
    private var f3Instance: Any? = null
    private var aodStartTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var alwaysOnDisplayExInstance: Any? = null

    fun setAutoModeTimeout(lpparam: LoadPackageParam, timeout: Int) {
        timeoutSeconds = timeout.coerceIn(10, 600)
        log("setAutoModeTimeout: timeout=${timeoutSeconds}s")

        when (lpparam.packageName) {
            Package.ANDROID -> { /* 暂时跳过系统框架 */ }
            Package.AOD -> hookAodApp(lpparam)
        }
    }

    private fun hookAodApp(lpparam: LoadPackageParam) {
        findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        hookAodInternal(lpparam)
                    } catch (t: Throwable) {
                        logError("hookAodInternal failed", t)
                    }
                }
            }
        )
    }

    private fun hookAodInternal(lpparam: LoadPackageParam) {
        val f3Class = XposedHelpers.findClass("aod.F3", lpparam.classLoader)
        val jField = f3Class.getDeclaredField("j")
        jField.isAccessible = true
        val alwaysOnDisplayExClass = jField.type

        log("=== AlwaysOnDisplayEx methods ===")
        alwaysOnDisplayExClass.declaredMethods.take(30).forEach { method ->
            val params = method.parameterTypes.joinToString(", ") { it.simpleName }
            log("  ${method.name}($params): ${method.returnType.simpleName}")
        }

        // Hook F3 构造函数
        f3Class.declaredConstructors.forEach { constructor ->
            try {
                XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        f3Instance = param.thisObject
                        log("F3 instance captured")
                    }
                })
            } catch (e: Exception) {}
        }

        // Hook 所有可能控制 AOD 显示的方法
        // A(), B(), D(), b(), d(), g(), i(), o(), p(), y() - 所有带 boolean 参数的方法
        val booleanMethods = alwaysOnDisplayExClass.declaredMethods
            .filter { it.parameterTypes.size == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType }

        booleanMethods.forEach { method ->
            try {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val arg = param.args[0] as Boolean
                        log(">>> ${method.name}($arg)")

                        // 记录实例
                        if (alwaysOnDisplayExInstance == null) {
                            alwaysOnDisplayExInstance = param.thisObject
                        }

                        // 假设 true 表示显示，false 表示隐藏
                        // 或者反过来，需要通过日志确认
                        handleAodStateChange(method.name, arg, alwaysOnDisplayExClass, param.thisObject)
                    }
                })
                log("Hooked ${method.name}(boolean)")
            } catch (e: Exception) {}
        }

        // Hook E(boolean) - 熄屏
        XposedHelpers.findAndHookMethod(
            alwaysOnDisplayExClass, "E",
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val turnOff = param.args[0] as Boolean
                    log("=== E($turnOff) - SCREEN OFF ===")

                    if (turnOff && f3Instance != null) {
                        try {
                            val iField = f3Class.getDeclaredField("i")
                            iField.isAccessible = true
                            val reason = iField.getInt(f3Instance)
                            log("  reason = $reason (7=timeout, 9=pocket, 16=sleep)")

                            if (reason == 7 || reason == 9 || reason == 16) {
                                if (aodStartTime > 0) {
                                    val duration = System.currentTimeMillis() - aodStartTime
                                    val targetTimeout = timeoutSeconds * 1000L
                                    log("  duration = ${duration}ms, target = ${targetTimeout}ms")

                                    if (duration < targetTimeout) {
                                        log("  >>> BLOCKING - too early <<<")
                                        param.result = null
                                        return
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logError("Check reason failed", e)
                        }
                    }
                }
            }
        )
        log("Hooked E(boolean)")

        // Hook F3 的方法来追踪 reason 变化
        val iField = f3Class.getDeclaredField("i")
        iField.isAccessible = true

        f3Class.declaredMethods
            .filter { it.parameterTypes.any { p -> p == Int::class.javaPrimitiveType } }
            .take(5)
            .forEach { method ->
                try {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val reason = iField.getInt(param.thisObject)
                                log("F3.${method.name}() -> i=$reason")
                            } catch (e: Exception) {}
                        }
                    })
                } catch (e: Exception) {}
            }
    }

    private fun handleAodStateChange(methodName: String, show: Boolean, clazz: Class<*>, instance: Any) {
        // 取消之前的定时器
        cancelScheduledScreenOff()

        if (show) {
            // 假设 true = AOD 显示
            aodStartTime = System.currentTimeMillis()
            log("AOD shown via $methodName, scheduling screen off in ${timeoutSeconds}s")

            if (timeoutSeconds < 300) {
                scheduleActiveScreenOff(clazz, instance)
            }
        } else {
            // 假设 false = AOD 隐藏
            aodStartTime = 0
            log("AOD hidden via $methodName")
        }
    }

    private fun scheduleActiveScreenOff(alwaysOnDisplayExClass: Class<*>, instance: Any) {
        timeoutRunnable = Runnable {
            try {
                log(">>> Active screen off triggered after ${timeoutSeconds}s <<<")
                val eMethod = alwaysOnDisplayExClass.getDeclaredMethod("E", Boolean::class.javaPrimitiveType)
                eMethod.isAccessible = true
                eMethod.invoke(instance, true)
            } catch (e: Exception) {
                logError("Active screen off failed", e)
            }
        }
        handler.postDelayed(timeoutRunnable!!, timeoutSeconds * 1000L)
    }

    private fun cancelScheduledScreenOff() {
        timeoutRunnable?.let {
            handler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }
}
