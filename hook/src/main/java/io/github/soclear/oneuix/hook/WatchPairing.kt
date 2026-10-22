package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog

object WatchPairing {
    const val MODE_NONE = 0
    const val MODE_WEAROS_CN = 1
    const val MODE_WEAROS_GLOBAL = 2

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init(
        bypassRegionCheck: Boolean,
        connectionMode: Int,
        supplementChinaWearOsGms: Boolean,
    ) {
        if (param.packageName != Package.WATCH_MANAGER) return

        xlog("OneUIX: WatchPairing init: bypassRegionCheck=$bypassRegionCheck, connectionMode=$connectionMode")

        if (bypassRegionCheck) {
            bypassRegionCheck()
            disableCscCheck()
            bypassPairingProblemCheck()
            spoofCscValue()
        }

        if (connectionMode != MODE_NONE) {
            spoofChinaEdition(connectionMode)
        }

        if (connectionMode == MODE_WEAROS_CN && supplementChinaWearOsGms) {
            supplementChinaWearOsGms()
        }

        if (connectionMode == MODE_WEAROS_GLOBAL) {
            bypassWatchStatusCheck()
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun bypassRegionCheck() {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.twatchmanager.connectionmanager.util.BluetoothUuidUtil")
                    .getDeclaredMethod(
                        "checkDeviceRegion",
                        android.content.Context::class.java,
                        android.bluetooth.BluetoothDevice::class.java
                    )
            ).intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun spoofChinaEdition(connectionMode: Int) {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.global.utils.GoogleRequirementUtils")
                    .getDeclaredMethod("isChinaEdition", android.content.Context::class.java)
            ).intercept { chain ->
                when (connectionMode) {
                    MODE_WEAROS_CN -> true
                    MODE_WEAROS_GLOBAL -> false
                    else -> chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun disableCscCheck() {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.global.utils.PlatformUtils")
                    .getDeclaredMethod("isSamsungChinaModel")
            ).intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun bypassPairingProblemCheck() {
        try {
            val problemClass = param.classLoader.loadClass(
                $$"com.samsung.android.app.watchmanager.setupwizard.pairing.PairingProblemChecker$Problem"
            )
            val wearableDeviceClass = param.classLoader.loadClass(
                "com.samsung.android.app.twatchmanager.connectionmanager.define.WearableDevice"
            )
            val bluetoothDeviceClass =
                param.classLoader.loadClass("android.bluetooth.BluetoothDevice")
            val fragmentActivityClass =
                param.classLoader.loadClass("androidx.fragment.app.FragmentActivity")

            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.watchmanager.setupwizard.pairing.PairingProblemChecker")
                    .getDeclaredMethod(
                        "problemCheckAfterPairing",
                        wearableDeviceClass,
                        bluetoothDeviceClass,
                        Boolean::class.javaPrimitiveType,
                        fragmentActivityClass
                    )
            ).intercept { chain ->
                val result = chain.proceed()
                if (result?.toString() == "WEAR_OS_NOT_SUPPORTED_PHONE") {
                    problemClass.reflect["NO_PROBLEM"]
                } else {
                    result
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun supplementChinaWearOsGms() {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.watchmanager.setupwizard.downloadinstall.HMConnectFragment")
                    .getDeclaredMethod("makePackageListToDownload")
            ).intercept { chain ->
                val result = chain.proceed()
                val packages = result as? Set<*> ?: return@intercept result
                val newPackages = packages.toMutableSet()
                newPackages.add("com.google.android.wearable.app.cn")
                newPackages
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun spoofCscValue() {
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.samsung.android.app.twatchmanager.util.PlatformNetworkUtils")
                    .getDeclaredMethod("getCSC")
            ).intercept { "TGY" }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    /**
     * 国行表走「全球版配对」时，Galaxy Wearable 侧还有三道闸门会把流程打回“必须重置手表”。
     * 它们都在 com.samsung.android.app.twatchmanager.connectionmanager.communication.WatchDataManager 里
     * （手机与手表之间 BLE 握手协议的处理者）：
     *
     *   闸门 1  onWatchStatusResp(isLocked, isNewPhoneAction, isLogin, isChina, isKids)
     *           手表上报 isChina=true（手表的 cn.google 系统特征）→ 手机立刻 RESET_ONLY 并 return，
     *           连账号校验都不会进行。这是国行表最先撞上的一道墙。
     *   闸门 2  onWatchAccountVerification(status, accountType, previousWatchAddress, watchBRAddress)
     *           全球版流程用 Google 账号数据向手表校验；国行表无 GMS / 无 com.google 账号，
     *           必回 AccountMode.WATCH_NOT_LOGIN → handleAccountNotVerified → RESET_ONLY。
     *   闸门 3  isBLRPossibleDevice(watchAddress)
     *           查 Galaxy Wearable 的 settings 表 PLUGIN_DATA_com.samsung.wearable.watchuniteplugin，
     *           国行表该记录为 [] → 判定“插件不支持 bond 丢失恢复”→ RESET_ONLY，
     *           从而拿不到 BOND_LOSS_RECOVERY（保留数据、不重置）这条官方恢复通道。
     *
     * 三关全部放行后，流程会自然落到：
     *   - 地址与本机一致  → isBLRPossibleDevice 通过 → onBondLessRecoveryPossible() → BLR 模式（保留数据）
     *   - 地址与本机不一致 → onUserVerified() → 正常换机传输流程（同样不重置）
     * 两者都不需要重置手表。
     */
    /**
     * 合成三星手表的 manufacturer data（WatchMFFormat 格式）。
     *
     * 背景：手机端 BT 缓存的扫描记录被清（Galaxy Wearable 走重配对流程时会自行 removeBond），
     * BluetoothDevice.semGetManufacturerData() 返回 null → ManufacturerUtil.getManufacturerData() 返回 null
     * → WatchMFFormat 构造 NPE("Attempt to get length of null array")
     * → WearableDevice.deviceId 为空 → updateCategoryName() 查不到 category
     * → rule = b7.l.b(category) 为 null → 后续 BLE 阶段 NPE。
     *
     * 这里补一份 Galaxy Watch9 的等价数据：
     *   deviceId = 17213 (0x433D, 大端) → support_devices_info 表里对应 "Galaxy Watch9"
     *   purpose  = 3 → DeviceMode.AC_SUPPORT_PHONE_SWITCHING_MODE（isSupportFeatureBasedScenario 的原生通过值）
     *   type     = 0xFF(-1) → hasDeviceId() = true
     *
     * 布局（bArr[2] != 0xFF，因此不会触发 addHeaderFromScanResult 的加头分支，索引即最终索引）：
     *   [4]=version [6]=serviceId [8]=pluginId [9]=purpose [10]=type
     *   [13..14]=deviceId [15]=deviceCategory [16]=osVersion
     */
    private fun buildWatch9MfData(): ByteArray {
        val mf = ByteArray(20)
        mf[0] = 19
        mf[1] = 0xFF.toByte()
        mf[2] = 0x75            // 117：避开 addHeaderFromScanResult 的触发条件 bArr[2]==-1
        mf[3] = 0
        mf[4] = 1               // version
        mf[5] = 0
        mf[6] = 2               // serviceId = 2
        mf[7] = 0
        mf[8] = 1               // pluginId = 1  → mIsPluginDevice = true
        mf[9] = 3               // purpose = 3     → AC_SUPPORT_PHONE_SWITCHING_MODE
        mf[10] = 0xFF.toByte()  // type = -1       → hasDeviceId() = true
        mf[11] = 0
        mf[12] = 0
        mf[13] = 0x43           // deviceId 高字节
        mf[14] = 0x3D           // deviceId 低字节 → 17213 → "Galaxy Watch9"
        mf[15] = 8              // deviceCategory
        mf[16] = 1              // osVersion
        return mf
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun bypassWatchStatusCheck() {
        // 闸门 -1（数据层）：BT 缓存被清后 semGetManufacturerData() 返回 null，
        //   导致 WatchMFFormat NPE → deviceId/category 全空 → rule=null → 后续全链路 NPE。
        //   这里在取不到真实数据时补一份 Galaxy Watch9 的等价 MF 数据。
        try {
            val muClass = param.classLoader.loadClass(
                "com.samsung.android.app.twatchmanager.connectionmanager.util.ManufacturerUtil"
            )
            xposedModule.hook(
                muClass.getDeclaredMethod(
                    "getManufacturerData",
                    android.bluetooth.BluetoothDevice::class.java
                )
            ).intercept { chain ->
                val real = chain.proceed() as? ByteArray
                if (real != null && real.isNotEmpty()) {
                    xlog("OneUIX: getManufacturerData real=${real.size}B")
                    real
                } else {
                    val fake = buildWatch9MfData()
                    xlog("OneUIX: getManufacturerData null -> synthesized ${fake.size}B (Watch9 id=17213 purpose=3)")
                    fake
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        val wdClass = try {
            param.classLoader.loadClass(
                "com.samsung.android.app.twatchmanager.connectionmanager.define.WearableDevice"
            )
        } catch (t: Throwable) {
            xlog(t)
            null
        }

        // 闸门 0（最前置、纯本地判定）：WearableDevice.isSupportFeatureBasedScenario()
        //   return mode == AC_SUPPORT_PHONE_SWITCHING_MODE || mode == AC_AUTO_SWITCHING_MODE
        //   mode 由手表的 device profile(rule) 决定；国行表不是 AC_* → false，
        //   于是 WatchTransferRequesterImpl.startModeChangeProcess 直接走 else 分支 resetOnlyNeeded()，
        //   连 BLE 握手都不会发起（日志特征：status 从 START 直接跳到 WATCH_RESET_POPUP_NEEDED，无任何 BLE 日志）。
        //   这一关不放行，后面三关根本没有机会被调用。
        try {
            if (wdClass != null) {
                xposedModule.hook(wdClass.getDeclaredMethod("isSupportFeatureBasedScenario"))
                    .intercept { chain ->
                        val real = runCatching { chain.proceed() as? Boolean }.getOrNull()
                        xlog("OneUIX: isSupportFeatureBasedScenario real=$real -> true (forced)")
                        true
                    }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        val wdmClass = try {
            param.classLoader.loadClass(
                "com.samsung.android.app.twatchmanager.connectionmanager.communication.WatchDataManager"
            )
        } catch (t: Throwable) {
            xlog(t)
            return
        }

        // 闸门 1：手表状态响应 —— 强制放行（关键是把 isChina 压成 false）
        try {
            val m = wdmClass.getDeclaredMethod(
                "onWatchStatusResp",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            xposedModule.hook(m).intercept { chain ->
                val args = chain.args.toTypedArray()
                if (args.size == 5) {
                    xlog(
                        "OneUIX: onWatchStatusResp in locked=${args[0]} newPhone=${args[1]} " +
                            "login=${args[2]} china=${args[3]} kids=${args[4]} -> all pass"
                    )
                    args[0] = false // isLocked
                    args[1] = true  // isNewPhoneAction
                    args[2] = true  // isLogin
                    args[3] = false // isChina  ← 国行表自报 cn.google，这里压掉
                    args[4] = false // isKids
                    chain.proceed(args)
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 闸门 2：账号验证 —— 一律当作 VERIFIED（国行表没有 Google 账号，否则必回 WATCH_NOT_LOGIN）
        val verifiedOrdinal = try {
            val enumClass = param.classLoader.loadClass(
                "com.samsung.android.app.twatchmanager.connectionmanager.communication.WatchDataManager\$AccountMode"
            )
            @Suppress("UNCHECKED_CAST")
            (enumClass.enumConstants as Array<Enum<*>>).first { it.name == "VERIFIED" }.ordinal
        } catch (t: Throwable) {
            xlog(t)
            2
        }
        try {
            val m = wdmClass.getDeclaredMethod(
                "onWatchAccountVerification",
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                String::class.java,
            )
            xposedModule.hook(m).intercept { chain ->
                val args = chain.args.toTypedArray()
                if (args.size == 4) {
                    xlog(
                        "OneUIX: onWatchAccountVerification status=${args[0]} prev=${args[2]} " +
                            "br=${args[3]} -> force VERIFIED($verifiedOrdinal)"
                    )
                    args[0] = verifiedOrdinal
                    chain.proceed(args)
                } else {
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 闸门 3：BLR 记录检查 —— 强制通过，让流程走到 onBondLessRecoveryPossible()
        try {
            val m = wdmClass.getDeclaredMethod("isBLRPossibleDevice", String::class.java)
            xposedModule.hook(m).intercept { chain ->
                xlog("OneUIX: isBLRPossibleDevice(${chain.args.firstOrNull()}) -> true (forced)")
                true
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
