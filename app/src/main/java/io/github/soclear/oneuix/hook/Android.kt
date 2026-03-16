package io.github.soclear.oneuix.hook

import android.app.NotificationChannel
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement.DO_NOTHING
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.setBooleanField
import de.robv.android.xposed.XposedHelpers.setStaticIntField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package


object Android {
    fun setBlockableNotificationChannel() {
        try {
            val notificationChannelClass = NotificationChannel::class.java

            hookAllConstructors(notificationChannelClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    setBooleanField(param.thisObject, "mBlockableSystem", true)
                    setBooleanField(param.thisObject, "mImportanceLockedByOEM", false)
                    setBooleanField(param.thisObject, "mImportanceLockedDefaultApp", false)
                }
            })

            findAndHookMethod(
                notificationChannelClass,
                "setBlockable",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = true
                    }
                }
            )

            val unlockHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = false
                }
            }

            findAndHookMethod(
                notificationChannelClass,
                "setImportanceLockedByOEM",
                Boolean::class.javaPrimitiveType,
                unlockHook
            )

            findAndHookMethod(
                notificationChannelClass,
                "setImportanceLockedByCriticalDeviceFunction",
                Boolean::class.javaPrimitiveType,
                unlockHook
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }


    fun setMaxNeverKilledAppNum(loadPackageParam: LoadPackageParam, num: Int) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        try {
            val clazz = findClass(
                "com.android.server.am.DynamicHiddenApp",
                loadPackageParam.classLoader
            )
            setStaticIntField(clazz, "MAX_NEVERKILLEDAPP_NUM", num)
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    // 禁用每 72 小时验证锁屏密码
    fun disablePinVerifyPer72h(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        try {
            XposedBridge.hookAllMethods(
                findClass(
                    "com.android.server.locksettings.LockSettingsStrongAuth",
                    loadPackageParam.classLoader
                ),
                "rescheduleStrongAuthTimeoutAlarm",
                DO_NOTHING
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
