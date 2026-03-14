package io.github.soclear.oneuix.hook

import android.app.NotificationChannel
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XC_MethodReplacement.DO_NOTHING
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setBooleanField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.XposedHelpers.setStaticIntField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package


object Android {
    fun setBlockableNotificationChannel() {
        try {
            hookAllConstructors(NotificationChannel::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    setBooleanField(param.thisObject, "mBlockableSystem", true)
                    setBooleanField(param.thisObject, "mImportanceLockedDefaultApp", false)
                }
            })

            findAndHookMethod(
                NotificationChannel::class.java,
                "setBlockable",
                Boolean::class.javaPrimitiveType,
                DO_NOTHING
            )

            findAndHookMethod(
                NotificationChannel::class.java,
                "setImportanceLockedByCriticalDeviceFunction",
                Boolean::class.javaPrimitiveType,
                DO_NOTHING
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

    // ASKS 策略移除
    fun disableAsksRestriction(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        try {
            val asksManagerClass = findClass(
                "android.content.pm.ASKSManager",
                loadPackageParam.classLoader
            )

            // Hook 构造函数，清空受限包列表和 PID 映射
            hookAllConstructors(asksManagerClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val restrictedPackages = getObjectField(param.thisObject, "mASKSRestrictedPackages")
                        if (restrictedPackages != null) {
                            setObjectField(param.thisObject, "mASKSRestrictedPackages", null)
                        }
                        val pidMap = getObjectField(param.thisObject, "mASKSPidMap")
                        if (pidMap != null) {
                            setObjectField(param.thisObject, "mASKSPidMap", null)
                        }
                    } catch (e: Throwable) {
                        XposedBridge.log("OneUIX: ASKS field access error - ${e.message}")
                    }
                }
            })

            // 阻止更新受限包列表
            XposedBridge.hookAllMethods(
                asksManagerClass,
                "updateRestrictedTargetPackages",
                DO_NOTHING
            )

            // 阻止通过 PID 添加受限包
            XposedBridge.hookAllMethods(
                asksManagerClass,
                "addPackageWithPid",
                DO_NOTHING
            )

            // isBlockTarget 始终返回 false
            XposedBridge.hookAllMethods(
                asksManagerClass,
                "isBlockTarget",
                XC_MethodReplacement.returnConstant(false)
            )

            // hasBlockPolicy 始终返回 false
            XposedBridge.hookAllMethods(
                asksManagerClass,
                "hasBlockPolicy",
                XC_MethodReplacement.returnConstant(false)
            )

            XposedBridge.log("OneUIX: ASKS restriction disabled")
        } catch (t: Throwable) {
            XposedBridge.log("OneUIX: Failed to disable ASKS restriction")
            XposedBridge.log(t)
        }
    }

    // 系统分区签名校验绕过 (Android 15)
    fun disableSignVerification(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        if (Build.VERSION.SDK_INT < 35) return

        try {
            val apkSignatureVerifierClass = findClass(
                "android.util.apk.ApkSignatureVerifier",
                loadPackageParam.classLoader
            )

            // 修改 getMinimumSignatureSchemeVersionForTargetSdk 返回 1 (V1 签名)
            XposedBridge.hookAllMethods(
                apkSignatureVerifierClass,
                "getMinimumSignatureSchemeVersionForTargetSdk",
                XC_MethodReplacement.returnConstant(1)
            )

            XposedBridge.log("OneUIX: Signature verification disabled (Android 15)")
        } catch (t: Throwable) {
            XposedBridge.log("OneUIX: Failed to disable signature verification")
            XposedBridge.log(t)
        }
    }

    // 共享用户安装校验绕过 (Android 15)
    fun disableShareUserCheck(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        if (Build.VERSION.SDK_INT < 35) return

        try {
            val packageImplClass = findClass(
                "com.android.internal.pm.parsing.pkg.PackageImpl",
                loadPackageParam.classLoader
            )

            // isLeavingSharedUser 始终返回 true
            XposedBridge.hookAllMethods(
                packageImplClass,
                "isLeavingSharedUser",
                XC_MethodReplacement.returnConstant(true)
            )

            XposedBridge.log("OneUIX: Shared user check disabled (Android 15)")
        } catch (t: Throwable) {
            XposedBridge.log("OneUIX: Failed to disable shared user check")
            XposedBridge.log(t)
        }
    }

    // GMS/FCM 限制绕过
    fun allowGms(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        try {
            val gmsAlarmManagerClass = findClass(
                "com.android.server.alarm.GmsAlarmManager",
                loadPackageParam.classLoader
            )

            // 伪装为非中国区设备
            XposedBridge.hookAllMethods(
                gmsAlarmManagerClass,
                "isChinaMode",
                XC_MethodReplacement.returnConstant(false)
            )

            // 伪装为港版设备
            XposedBridge.hookAllMethods(
                gmsAlarmManagerClass,
                "isHongKongMode",
                XC_MethodReplacement.returnConstant(true)
            )

            // 绕过网络活动检查
            XposedBridge.hookAllMethods(
                gmsAlarmManagerClass,
                "checkActiveNet",
                XC_MethodReplacement.returnConstant(false)
            )

            // 绕过 Google 网络检查
            XposedBridge.hookAllMethods(
                gmsAlarmManagerClass,
                "checkGoogleNetwork",
                XC_MethodReplacement.returnConstant(false)
            )

            // 绕过 GMS 网络限制
            XposedBridge.hookAllMethods(
                gmsAlarmManagerClass,
                "setGmsNetWorkAllow",
                XC_MethodReplacement.returnConstant(false)
            )

            // 强制返回正常网络状态 (HTTP OK)
            XposedBridge.hookAllMethods(
                gmsAlarmManagerClass,
                "getNetworkStatus",
                XC_MethodReplacement.returnConstant(200)
            )

            XposedBridge.log("OneUIX: GMS/FCM restriction bypassed")
        } catch (t: Throwable) {
            XposedBridge.log("OneUIX: Failed to bypass GMS restriction")
            XposedBridge.log(t)
        }
    }

    // FCM 强制唤醒
    fun fcmFix(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        try {
            val amsClass = findClass(
                "com.android.server.am.ActivityManagerService",
                loadPackageParam.classLoader
            )

            XposedBridge.hookAllMethods(
                amsClass,
                "broadcastIntentLocked",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            // intent 参数位置可能在不同的重载中不同
                            // 尝试找到 Intent 参数
                            for (i in param.args.indices) {
                                val arg = param.args[i]
                                if (arg is android.content.Intent) {
                                    val action = arg.action
                                    if (action != null && (
                                        action == "com.google.firebase.INSTANCE_ID_EVENT" ||
                                        action == "com.google.firebase.MESSAGING_EVENT" ||
                                        action.endsWith(".android.c2dm.intent.RECEIVE")
                                    )) {
                                        XposedBridge.log("OneUIX: Forced FCM to awake: $action")
                                        // 移除广播限制标志 (第一个参数通常是 flags)
                                        if (param.args.isNotEmpty() && param.args[0] is Int) {
                                            param.args[0] = 0
                                        }
                                    }
                                    break
                                }
                            }
                        } catch (e: Throwable) {
                            XposedBridge.log("OneUIX: FCM broadcast hook error - ${e.message}")
                        }
                    }
                }
            )

            XposedBridge.log("OneUIX: FCM force wake enabled")
        } catch (t: Throwable) {
            XposedBridge.log("OneUIX: Failed to enable FCM force wake")
            XposedBridge.log(t)
        }
    }
}
