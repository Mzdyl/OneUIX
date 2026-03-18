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
import io.github.soclear.oneuix.hook.util.log
import io.github.soclear.oneuix.hook.util.logError


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
            logError("setBlockableNotificationChannel failed", t)
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
            logError("setMaxNeverKilledAppNum failed", t)
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
            logError("disablePinVerifyPer72h failed", t)
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
                        logError("ASKS field access error", e)
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

            log("ASKS restriction disabled")
        } catch (t: Throwable) {
            logError("Failed to disable ASKS restriction", t)
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

            log("GMS/FCM restriction bypassed")
        } catch (t: Throwable) {
            logError("Failed to bypass GMS restriction", t)
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
                                        log("Forced FCM to awake: $action")
                                        // 移除广播限制标志 (第一个参数通常是 flags)
                                        if (param.args.isNotEmpty() && param.args[0] is Int) {
                                            param.args[0] = 0
                                        }
                                    }
                                    break
                                }
                            }
                        } catch (e: Throwable) {
                            logError("FCM broadcast hook error", e)
                        }
                    }
                }
            )

            log("FCM force wake enabled")
        } catch (t: Throwable) {
            logError("Failed to enable FCM force wake", t)
        }
    }

    // 谷歌即圈即搜 (ContextualSearch)
    fun enableGoogleSearch(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        try {
            // 1. Hook ContextualSearchManagerService
            val contextualSearchClass = findClass(
                "com.android.server.contextualsearch.ContextualSearchManagerService",
                loadPackageParam.classLoader
            )

            // 阻止更新黑名单
            XposedBridge.hookAllMethods(
                contextualSearchClass,
                "updateDenylist",
                DO_NOTHING
            )

            // 绕过权限检查
            XposedBridge.hookAllMethods(
                contextualSearchClass,
                "enforceOverridingPermission",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null  // 跳过权限检查
                    }
                }
            )

            // 修改上下文搜索包名为 Google 搜索
            XposedBridge.hookAllMethods(
                contextualSearchClass,
                "getContextualSearchPackageName",
                XC_MethodReplacement.returnConstant("com.google.android.googlequicksearchbox")
            )

            // 2. Hook HighRefreshRateDenylist (确保流畅体验)
            try {
                val highRefreshRateClass = findClass(
                    "com.android.server.wm.HighRefreshRateDenylist",
                    loadPackageParam.classLoader
                )
                XposedBridge.hookAllMethods(
                    highRefreshRateClass,
                    "updateDenylist",
                    DO_NOTHING
                )
            } catch (_: Throwable) {
                // 可能不存在，忽略
            }

            // 3. 解锁 WiFi 服务工厂测试权限
            try {
                val wifiServiceClass = findClass(
                    "com.samsung.android.server.wifi.SemWifiServiceImpl",
                    loadPackageParam.classLoader
                )
                XposedBridge.hookAllMethods(
                    wifiServiceClass,
                    "enforceFactoryTestPermission",
                    DO_NOTHING
                )
            } catch (_: Throwable) {
                // 可能不存在，忽略
            }

            log("Google Search (Circle to Search) enabled")
        } catch (t: Throwable) {
            logError("Failed to enable Google Search", t)
        }
    }
}
