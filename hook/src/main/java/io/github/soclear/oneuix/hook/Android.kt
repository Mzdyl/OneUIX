package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.os.Bundle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object Android {
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun disableWritingToolkitGlobally() {
        val galaxyAiRestrictionsPackage = "com.samsung.android.knox.galaxyai"
        val writingToolkitKey = "key_writing_toolkit"
        val grayoutKey = "grayout"

        val classLoader = param.classLoader

        try {
            val proxyClass = classLoader.loadClass("com.android.server.enterprise.EDMProxyService")
            proxyClass.declaredMethods.filter {
                it.name == "getApplicationRestrictions"
            }.forEach {
                xposedModule.hook(it).intercept { chain ->
                    val result = chain.proceed()
                    if (chain.args.getOrNull(0) != galaxyAiRestrictionsPackage) {
                        result
                    } else {
                        val restrictions = Bundle(result as? Bundle ?: Bundle.EMPTY)
                        val writingToolkit = Bundle(restrictions.getBundle(writingToolkitKey) ?: Bundle.EMPTY)
                        writingToolkit.putBoolean(grayoutKey, true)
                        restrictions.putBundle(writingToolkitKey, writingToolkit)
                        restrictions
                    }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    @SuppressLint("BlockedPrivateApi")
    context(xposedModule: XposedModule)
    fun setBlockableNotificationChannel() {
        try {
            val notificationChannelClass = NotificationChannel::class.java

            notificationChannelClass.declaredConstructors.forEach {
                xposedModule.hook(it).intercept { chain ->
                    val result = chain.proceed()
                    chain.thisObject.reflect["mBlockableSystem"] = true
                    chain.thisObject.reflect["mImportanceLockedByOEM"] = false
                    chain.thisObject.reflect["mImportanceLockedDefaultApp"] = false
                    result
                }
            }

            notificationChannelClass
                .getDeclaredMethod("setBlockable", Boolean::class.javaPrimitiveType)
                .let { xposedModule.hook(it) }
                .intercept { chain ->
                    val newArgs = chain.args.toTypedArray()
                    newArgs[0] = true
                    chain.proceed(newArgs)
                }

            notificationChannelClass
                .getDeclaredMethod("setImportanceLockedByOEM", Boolean::class.javaPrimitiveType)
                .let { xposedModule.hook(it) }
                .intercept { chain ->
                    val newArgs = chain.args.toTypedArray()
                    newArgs[0] = false
                    chain.proceed(newArgs)
                }

            notificationChannelClass
                .getDeclaredMethod("setImportanceLockedByCriticalDeviceFunction", Boolean::class.javaPrimitiveType)
                .let { xposedModule.hook(it) }
                .intercept { chain ->
                    val newArgs = chain.args.toTypedArray()
                    newArgs[0] = false
                    chain.proceed(newArgs)
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }


    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun setMaxNeverKilledAppNum(num: Int) {
        try {
            param.classLoader.loadClass("com.android.server.am.DynamicHiddenApp").reflect["MAX_NEVERKILLEDAPP_NUM"] =
                num
        } catch (t: Throwable) {
            xlog(t)
        }
    }


    // 解除国行/港版对 GMS（含 FCM 推送）的网络限制
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun liftFcmNetworkLimit() {
        try {
            param.classLoader
                .loadClass("com.android.server.alarm.GmsAlarmManager")
                .constructors
                .forEach {
                    xposedModule.hook(it).intercept { chain ->
                        val result = chain.proceed()
                        chain.thisObject.reflect["isChinaMode"] = false
                        chain.thisObject.reflect["isHongKongMode"] = false
                        result
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    // 禁用每 72 小时验证锁屏密码
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun disablePinVerifyPer72h() {
        try {
            param.classLoader
                .loadClass("com.android.server.locksettings.LockSettingsStrongAuth")
                .declaredMethods
                .filter { it.name == "rescheduleStrongAuthTimeoutAlarm" }
                .forEach {
                    xposedModule.hook(it).intercept { null }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    // 移除充电器时禁止亮屏
    // PowerManagerService.updateIsPoweredLocked 在插拔充电器时会调用 wakePowerGroupLocked 点亮屏幕，
    // 唤醒理由字符串为 "android.server.power:PLUGGED:" + mIsPowered。
    // 拔出充电器时 mIsPowered 为 false，拦截该次唤醒即可（插入仍正常亮屏）。
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun disableScreenWakeOnPowerUnplugged() {
        try {
            param.classLoader
                .loadClass("com.android.server.power.PowerManagerService")
                .declaredMethods
                .filter { it.name == "wakePowerGroupLocked" }
                .forEach {
                    xposedModule.hook(it).intercept { chain ->
                        if (chain.args.getOrNull(3) == "android.server.power:PLUGGED:false") {
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    // 禁用 ASKS 限制
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun disableAsksRestriction() {
        try {
            val asksManagerClass = param.classLoader
                .loadClass("com.android.server.asks.ASKSManager")
            val policyClass = param.classLoader
                .loadClass("com.android.server.asks.Policy")
            runCatching { policyClass.reflect["mIsTargetList"] = ArrayList<Any>() }
            runCatching { policyClass.reflect["mTargetList"] = ArrayList<Any>() }
            runCatching { policyClass.reflect["mTargetModelList"] = ArrayList<Any>() }
            runCatching { policyClass.reflect["mTargetModelPolicy"] = ArrayList<Any>() }
            runCatching { policyClass.reflect["mModelCheckList"] = ArrayList<Any>() }

            asksManagerClass.declaredMethods
                .filter { it.name == "isBlockTarget" || it.name == "hasBlockPolicy" }
                .forEach {
                    xposedModule.hook(it).intercept { false }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    // FCM 强制唤醒
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun fcmFix() {
        try {
            val amsClass = param.classLoader
                .loadClass("com.android.server.am.ActivityManagerService")
            amsClass.declaredMethods
                .filter { it.name == "broadcastIntentLocked" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        for (i in chain.args.indices) {
                            val arg = chain.args[i]
                            if (arg is android.content.Intent) {
                                val action = arg.action
                                if (action != null && (
                                    action == "com.google.firebase.INSTANCE_ID_EVENT" ||
                                    action == "com.google.firebase.MESSAGING_EVENT" ||
                                    action.endsWith(".android.c2dm.intent.RECEIVE")
                                )) {
                                    if (chain.args.isNotEmpty() && chain.args[0] is Int) {
                                        chain.args[0] = 0
                                    }
                                }
                                break
                            }
                        }
                        chain.proceed()
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
