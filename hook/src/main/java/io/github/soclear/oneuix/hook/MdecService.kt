package io.github.soclear.oneuix.hook

import android.app.Application
import android.content.Context
import android.provider.Settings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object MdecService {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun handleHooks(
        bypassSameWifi: Boolean = false,
        unlockMobileNetwork: Boolean = false,
        bypassChinaSim: Boolean = false,
        mdecDeviceType: Int = 0
    ) {
        if (param.packageName != Package.MDEC_SERVICE) return
        val classLoader = param.classLoader

        if (bypassChinaSim) {
            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.SimUtils")
                val method = clazz.getDeclaredMethod("isChinaSIMActive", Context::class.java)
                xposedModule.hook(method).intercept { false }
            }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.SimUtils")
                val method = clazz.getDeclaredMethod("isChinaSimInserted", Context::class.java)
                xposedModule.hook(method).intercept { false }
            }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.preference.TestModeVerityState")
                val method = clazz.getDeclaredMethod("isTestModeVerifyState", Context::class.java)
                xposedModule.hook(method).intercept { true }
            }
        }

        if (bypassSameWifi) {
            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.CommonUtils")
                val method = clazz.getDeclaredMethod("isSameWifiRequiredForCall", Context::class.java)
                xposedModule.hook(method).intercept { false }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.CommonUtils")
                val method = clazz.getDeclaredMethod("setSameWifiNetworkStatus", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val ctx = chain.args[0] as? Context
                    if (ctx != null) {
                        Settings.Global.putInt(ctx.contentResolver, "cmc_same_wifi_network_status", 0)
                    }
                    null
                }
            }.onFailure { xlog(it) }
        }

        if (unlockMobileNetwork) {
            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.SimUtils")
                val method = clazz.getDeclaredMethod("isWifiOnlyDevice", Context::class.java)
                xposedModule.hook(method).intercept { false }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.cmcsettings.view.connectedNetwork.ConnectedNetworkPreference")
                val method = clazz.getDeclaredMethod("isEnablePreference")
                xposedModule.hook(method).intercept { true }
            }.onFailure { xlog(it) }
        }

        runCatching {
            val method = Application::class.java.getDeclaredMethod("onCreate")
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                runCatching {
                    val context = chain.thisObject as? Context ?: return@runCatching
                    if (bypassSameWifi) {
                        Settings.Global.putInt(context.contentResolver, "cmc_same_wifi_network_status", 0)
                    }
                    if (mdecDeviceType != 0) {
                        val targetStr = if (mdecDeviceType == 2) "sd" else "pd"
                        val current = Settings.Global.getString(context.contentResolver, "cmc_device_type")
                        if (current != targetStr) {
                            Settings.Global.putString(context.contentResolver, "cmc_device_type", targetStr)
                        }
                    }
                }.onFailure { xlog(it) }
                result
            }
        }.onFailure { xlog(it) }

        if (mdecDeviceType != 0) {
            hookDeviceType(classLoader, mdecDeviceType)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportCallAndTextOnOtherDevices(mdecDeviceType: Int = 0) {
        handleHooks(
            bypassSameWifi = true,
            unlockMobileNetwork = true,
            bypassChinaSim = true,
            mdecDeviceType = mdecDeviceType
        )
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookDeviceType(classLoader: ClassLoader, mdecDeviceType: Int) {
        val isTablet = (mdecDeviceType == 2)
        val deviceTypeStr = if (isTablet) "sd" else "pd"

        try {
            @Suppress("UNCHECKED_CAST")
            val cmcDeviceTypeEnumClass = classLoader.loadClass(
                "com.samsung.android.mdeccommon.serviceconfig.ServiceConfigEnums\$CMC_DEVICE_TYPE"
            ) as Class<out Enum<*>>
            val targetEnum = java.lang.Enum.valueOf(cmcDeviceTypeEnumClass, deviceTypeStr)
            val unknownEnum = java.lang.Enum.valueOf(cmcDeviceTypeEnumClass, "unknown")

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.DefaultApplicationUtils")
                val method = clazz.getDeclaredMethod("isSamsungMessageInstalled", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    if (chain.args[0] == null) false else chain.proceed()
                }
            }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.serviceconfig.ServiceConfigHelper")
                val method = clazz.getDeclaredMethod("isMsgSyncCapabilitySupported")
                xposedModule.hook(method).intercept { true }
            }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.FeatureUtils")
                val method = clazz.getDeclaredMethod("hasTabletFeature", Context::class.java)
                xposedModule.hook(method).intercept { isTablet }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.CommonUtils")
                val method = clazz.getDeclaredMethod("isTablet")
                xposedModule.hook(method).intercept { isTablet }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.CommonUtils")
                val method = clazz.getDeclaredMethod("getDefaultDeviceType")
                xposedModule.hook(method).intercept { targetEnum }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.CommonUtils")
                val method = clazz.getDeclaredMethod("isValidDeviceType", Context::class.java)
                xposedModule.hook(method).intercept { true }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.serviceconfig.ServiceConfigHelper")
                val method = clazz.getDeclaredMethod("getCmcDeviceType", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val ctx = chain.args[0] as? Context
                    if (ctx == null) unknownEnum else targetEnum
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.serviceconfig.ServiceConfigHelper")
                val isSdMethod = clazz.getDeclaredMethod("isSd", Context::class.java)
                xposedModule.hook(isSdMethod).intercept { chain ->
                    val ctx = chain.args[0] as? Context
                    if (ctx == null) false else isTablet
                }
                val isPdMethod = clazz.getDeclaredMethod("isPd", Context::class.java)
                xposedModule.hook(isPdMethod).intercept { chain ->
                    val ctx = chain.args[0] as? Context
                    if (ctx == null) false else !isTablet
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.cmcsettings.utils.Utils")
                val isTabletMethod = clazz.getDeclaredMethod("isTablet")
                xposedModule.hook(isTabletMethod).intercept { isTablet }

                val getMyDeviceTypeMethod = clazz.getDeclaredMethod("getMyDeviceType", Context::class.java)
                xposedModule.hook(getMyDeviceTypeMethod).intercept { chain ->
                    val ctx = chain.args[0] as? Context
                    if (ctx == null) 0 else if (isTablet) 2 else 1
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.cmcsettings.utils.CMCDatabaseHelper")
                val method = clazz.getDeclaredMethod("myDeviceType", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val ctx = chain.args[0] as? Context
                    if (ctx == null) "" else deviceTypeStr
                }
            }.onFailure { xlog(it) }

            runCatching {
                val devTypeEnumClass = classLoader.loadClass(
                    "com.samsung.android.cmcsetting.CmcSettingManagerConstants\$DeviceType"
                )
                @Suppress("UNCHECKED_CAST")
                val devTypeEnum = java.lang.Enum.valueOf(
                    devTypeEnumClass as Class<out Enum<*>>,
                    if (isTablet) "DEVICE_TYPE_SD" else "DEVICE_TYPE_PD"
                )
                val clazz = classLoader.loadClass("com.samsung.android.cmcsetting.CmcSettingManager")
                val method = clazz.getDeclaredMethod("getOwnDeviceType")
                xposedModule.hook(method).intercept { devTypeEnum }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
