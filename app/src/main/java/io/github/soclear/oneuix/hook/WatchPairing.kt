package io.github.soclear.oneuix.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.log
import io.github.soclear.oneuix.hook.util.logError

/**
 * 三星手表配对应用 Hook 模块
 * 
 * 功能：
 * 1. 绕过国行/外版手表与手机的区域限制
 * 2. 支持自由选择 WearOS CN 或 WearOS Global 连接方式
 * 3. 禁用 CSC 检查
 * 4. 强制安装国行/外版 GMS
 */
@Suppress("unused")
object WatchPairing {

    // 连接模式常量
    const val MODE_AUTO = 0
    const val MODE_WEAROS_CN = 1
    const val MODE_WEAROS_GLOBAL = 2

    /**
     * 初始化手表配对 Hook
     */
    fun init(
        lpparam: LoadPackageParam,
        bypassRegionCheck: Boolean = false,
        connectionMode: Int = MODE_AUTO,
        forceChinaGmsCore: Boolean = false,
        disableCscCheck: Boolean = false,
    ) {
        if (lpparam.packageName != Package.WATCH_MANAGER) return

        log("WatchPairing init: bypassRegionCheck=$bypassRegionCheck, connectionMode=$connectionMode")

        // Hook 1: 绕过区域检查 (BluetoothUuidUtil.checkDeviceRegion)
        if (bypassRegionCheck || connectionMode != MODE_AUTO) {
            bypassRegionCheck(lpparam)
        }

        // Hook 2: 伪造区域判断 (GoogleRequirementUtils.isChinaEdition)
        if (connectionMode != MODE_AUTO) {
            spoofChinaEdition(lpparam, connectionMode)
        }

        // Hook 3: 禁用 CSC 检查 (PlatformUtils.isSamsungChinaModel)
        if (disableCscCheck) {
            disableCscCheck(lpparam)
        }

        // Hook 4: 配对问题检查绕过 (PairingProblemChecker.problemCheckAfterPairing)
        if (bypassRegionCheck || connectionMode != MODE_AUTO) {
            bypassPairingProblemCheck(lpparam, connectionMode)
        }

        // Hook 5: 强制安装国行 GMS
        if (forceChinaGmsCore) {
            forceInstallChinaGmsCore(lpparam)
        }

        // Hook 6: CSC 获取伪造 (PlatformNetworkUtils.getCSC)
        if (disableCscCheck) {
            spoofCscValue(lpparam)
        }
    }

    /**
     * Hook 1: 绕过区域检查
     * 目标：com.samsung.android.app.twatchmanager.connectionmanager.util.BluetoothUuidUtil.checkDeviceRegion
     * 作用：始终返回 false，表示没有区域不匹配问题
     */
    private fun bypassRegionCheck(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.twatchmanager.connectionmanager.util.BluetoothUuidUtil",
                lpparam.classLoader,
                "checkDeviceRegion",
                android.content.Context::class.java,
                android.bluetooth.BluetoothDevice::class.java,
                object : XC_MethodHook() {
                    @Override
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            // 直接返回 false，表示区域匹配，没有问题
                            param.result = false
                            log("WatchPairing: Bypassed region check")
                        } catch (t: Throwable) {
                            logError("WatchPairing: bypassRegionCheck failed", t)
                        }
                    }
                }
            )
            log("WatchPairing: checkDeviceRegion hook success")
        } catch (t: Throwable) {
            logError("WatchPairing: bypassRegionCheck hook failed", t)
        }
    }

    /**
     * Hook 2: 伪造区域判断
     * 目标：com.samsung.android.app.global.utils.GoogleRequirementUtils.isChinaEdition
     * 作用：根据用户选择的连接模式返回对应的区域状态
     */
    private fun spoofChinaEdition(lpparam: LoadPackageParam, connectionMode: Int) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.global.utils.GoogleRequirementUtils",
                lpparam.classLoader,
                "isChinaEdition",
                android.content.Context::class.java,
                object : XC_MethodHook() {
                    @Override
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            when (connectionMode) {
                                MODE_WEAROS_CN -> {
                                    // WearOS CN 模式：强制返回 true（国行）
                                    param.result = true
                                    log("WatchPairing: Spoofed isChinaEdition=true (WearOS CN mode)")
                                }
                                MODE_WEAROS_GLOBAL -> {
                                    // WearOS Global 模式：强制返回 false（外版）
                                    param.result = false
                                    log("WatchPairing: Spoofed isChinaEdition=false (WearOS Global mode)")
                                }
                            }
                        } catch (t: Throwable) {
                            logError("WatchPairing: spoofChinaEdition failed", t)
                        }
                    }
                }
            )
            log("WatchPairing: isChinaEdition hook success, mode=$connectionMode")
        } catch (t: Throwable) {
            logError("WatchPairing: spoofChinaEdition hook failed", t)
        }
    }

    /**
     * Hook 3: 禁用 CSC 检查
     * 目标：com.samsung.android.app.global.utils.PlatformUtils.isSamsungChinaModel
     * 作用：始终返回 false，绕过国行检测
     */
    private fun disableCscCheck(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.global.utils.PlatformUtils",
                lpparam.classLoader,
                "isSamsungChinaModel",
                object : XC_MethodReplacement() {
                    @Override
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        log("WatchPairing: Disabled CSC check")
                        return false
                    }
                }
            )
            log("WatchPairing: isSamsungChinaModel hook success")
        } catch (t: Throwable) {
            logError("WatchPairing: disableCscCheck hook failed", t)
        }
    }

    /**
     * Hook 4: 配对问题检查绕过
     * 目标：com.samsung.android.app.watchmanager.setupwizard.pairing.PairingProblemChecker.problemCheckAfterPairing
     * 作用：移除 WEAR_OS_NOT_SUPPORTED_PHONE 错误
     */
    private fun bypassPairingProblemCheck(lpparam: LoadPackageParam, connectionMode: Int) {
        try {
            // 查找 Problem 枚举类
            val problemClass = XposedHelpers.findClass(
                "com.samsung.android.app.watchmanager.setupwizard.pairing.PairingProblemChecker\$Problem",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.watchmanager.setupwizard.pairing.PairingProblemChecker",
                lpparam.classLoader,
                "problemCheckAfterPairing",
                "com.samsung.android.app.twatchmanager.connectionmanager.define.WearableDevice",
                "android.bluetooth.BluetoothDevice",
                Boolean::class.javaPrimitiveType,
                "androidx.fragment.app.FragmentActivity",
                object : XC_MethodHook() {
                    @Override
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val result = param.result ?: return
                            val resultName = result.toString()

                            // 如果是区域不支持错误，根据连接模式决定是否绕过
                            if (resultName == "WEAR_OS_NOT_SUPPORTED_PHONE") {
                                if (connectionMode != MODE_AUTO) {
                                    // 获取 NO_PROBLEM 枚举值
                                    val noProblemValue = XposedHelpers.getStaticObjectField(
                                        problemClass,
                                        "NO_PROBLEM"
                                    )
                                    param.result = noProblemValue
                                    log("WatchPairing: Bypassed WEAR_OS_NOT_SUPPORTED_PHONE check")
                                }
                            }
                        } catch (t: Throwable) {
                            logError("WatchPairing: bypassPairingProblemCheck failed", t)
                        }
                    }
                }
            )
            log("WatchPairing: problemCheckAfterPairing hook success")
        } catch (t: Throwable) {
            logError("WatchPairing: bypassPairingProblemCheck hook failed", t)
        }
    }

    /**
     * Hook 5: 强制安装国行 GMS
     * 目标：com.samsung.android.app.watchmanager.setupwizard.downloadinstall.HMConnectFragment.makePackageListToDownload
     * 作用：确保安装国行 GMS 核心组件
     */
    private fun forceInstallChinaGmsCore(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.watchmanager.setupwizard.downloadinstall.HMConnectFragment",
                lpparam.classLoader,
                "makePackageListToDownload",
                object : XC_MethodHook() {
                    @Override
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val result = param.result as? java.util.HashSet<*> ?: return
                            val chinaGmsPackage = "com.google.android.wearable.app.cn"

                            // 强制添加国行 GMS 包名
                            @Suppress("UNCHECKED_CAST")
                            val mutableSet = result as java.util.HashSet<String>
                            if (!mutableSet.contains(chinaGmsPackage)) {
                                mutableSet.add(chinaGmsPackage)
                                log("WatchPairing: Forced adding China GMS Core package")
                            }
                        } catch (t: Throwable) {
                            logError("WatchPairing: forceInstallChinaGmsCore failed", t)
                        }
                    }
                }
            )
            log("WatchPairing: makePackageListToDownload hook success")
        } catch (t: Throwable) {
            logError("WatchPairing: forceInstallChinaGmsCore hook failed", t)
        }
    }

    /**
     * Hook 6: 伪造 CSC 值
     * 目标：com.samsung.android.app.twatchmanager.util.PlatformNetworkUtils.getCSC
     * 作用：返回指定的 CSC 代码
     */
    private fun spoofCscValue(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.twatchmanager.util.PlatformNetworkUtils",
                lpparam.classLoader,
                "getCSC",
                object : XC_MethodReplacement() {
                    @Override
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        // 返回 TGY（香港）CSC，这是一个通用的外版 CSC
                        log("WatchPairing: Spoofed CSC to TGY")
                        return "TGY"
                    }
                }
            )
            log("WatchPairing: getCSC hook success")
        } catch (t: Throwable) {
            logError("WatchPairing: spoofCscValue hook failed", t)
        }
    }

    /**
     * 获取连接模式描述
     */
    fun getConnectionModeDescription(mode: Int): String {
        return when (mode) {
            MODE_AUTO -> "自动检测（默认）"
            MODE_WEAROS_CN -> "WearOS CN（国行 GMS）"
            MODE_WEAROS_GLOBAL -> "WearOS Global（外版 GMS）"
            else -> "未知模式"
        }
    }
}
