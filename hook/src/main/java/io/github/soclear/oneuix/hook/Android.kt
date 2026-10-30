package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.content.Intent
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object Android {
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
        // Legacy ASKS (One UI 5 / Android 13 及以下)
        runCatching {
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
        }

        // Modern ASKS (One UI 6+ / Android 14, 15, 16)
        runCatching {
            val asksServiceClass = param.classLoader
                .loadClass("com.android.server.asks.ASKSManagerService")
            asksServiceClass.declaredMethods
                .filter {
                    it.name == "isTargetPackage" ||
                    it.name == "isUnknownApps" ||
                    it.name == "isSuspiciousMsgTarget" ||
                    it.name == "isBlockTarget" ||
                    it.name == "hasBlockPolicy"
                }
                .forEach {
                    xposedModule.hook(it).intercept { false }
                }

            asksServiceClass.declaredMethods
                .filter { it.name == "checkFollowingLegitimateWay" }
                .forEach {
                    xposedModule.hook(it).intercept { true }
                }

            asksServiceClass.declaredMethods
                .filter { it.name == "checkASKSTarget" }
                .forEach {
                    xposedModule.hook(it).intercept { emptyArray<String>() }
                }
        }
    }

    // FCM 强制唤醒
    context(xposedModule: XposedModule, param: XposedModuleInterface.SystemServerStartingParam)
    fun fcmFix() {
        fun hookBroadcast(clazz: Class<*>) {
            clazz.declaredMethods
                .filter { it.name == "broadcastIntentLocked" || it.name == "broadcastIntentLockedTraced" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        for (arg in chain.args) {
                            if (arg is Intent) {
                                val action = arg.action
                                if (action != null && (
                                    action == "com.google.firebase.INSTANCE_ID_EVENT" ||
                                    action == "com.google.firebase.MESSAGING_EVENT" ||
                                    action.endsWith(".android.c2dm.intent.RECEIVE")
                                )) {
                                    arg.addFlags(
                                        Intent.FLAG_RECEIVER_FOREGROUND or
                                        Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                                    )
                                }
                                break
                            }
                        }
                        chain.proceed()
                    }
                }
        }

        try {
            hookBroadcast(param.classLoader.loadClass("com.android.server.am.ActivityManagerService"))
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            hookBroadcast(param.classLoader.loadClass("com.android.server.am.BroadcastController"))
        } catch (_: Throwable) {}
    }
}
