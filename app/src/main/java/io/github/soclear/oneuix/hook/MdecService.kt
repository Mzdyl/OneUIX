package io.github.soclear.oneuix.hook

import android.app.Application
import android.content.Context
import android.provider.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.logError

object MdecService {
    fun supportCallAndTextOnOtherDevices(
        loadPackageParam: XC_LoadPackage.LoadPackageParam,
        mdecDeviceType: Int = 0
    ) {
        if (loadPackageParam.packageName != Package.MDEC_SERVICE) return
        val classLoader = loadPackageParam.classLoader

        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.mdeccommon.utils.SimUtils",
                classLoader,
                "isChinaSIMActive",
                Context::class.java,
                XC_MethodReplacement.returnConstant(false)
            )
        } catch (ignored: Throwable) {
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.mdeccommon.utils.SimUtils",
                classLoader,
                "isChinaSimInserted",
                Context::class.java,
                XC_MethodReplacement.returnConstant(false)
            )
        } catch (ignored: Throwable) {
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.mdeccommon.preference.TestModeVerityState",
                classLoader,
                "isTestModeVerifyState",
                Context::class.java,
                XC_MethodReplacement.returnConstant(true)
            )
        } catch (ignored: Throwable) {
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.mdeccommon.utils.CommonUtils",
                classLoader,
                "isSameWifiRequiredForCall",
                Context::class.java,
                XC_MethodReplacement.returnConstant(false)
            )
        } catch (t: Throwable) {
            logError("supportCallAndTextOnOtherDevices: isSameWifiRequiredForCall hook failed", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.mdeccommon.utils.CommonUtils",
                classLoader,
                "setSameWifiNetworkStatus",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val ctx = param.args[0] as? Context ?: return
                        Settings.Global.putInt(ctx.contentResolver, "cmc_same_wifi_network_status", 0)
                        param.result = null
                    }
                }
            )
        } catch (t: Throwable) {
            logError("supportCallAndTextOnOtherDevices: setSameWifiNetworkStatus hook failed", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.mdeccommon.utils.SimUtils",
                classLoader,
                "isWifiOnlyDevice",
                Context::class.java,
                XC_MethodReplacement.returnConstant(false)
            )
        } catch (t: Throwable) {
            logError("supportCallAndTextOnOtherDevices: isWifiOnlyDevice hook failed", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.cmcsettings.view.connectedNetwork.ConnectedNetworkPreference",
                classLoader,
                "isEnablePreference",
                XC_MethodReplacement.returnConstant(true)
            )
        } catch (t: Throwable) {
            logError("supportCallAndTextOnOtherDevices: isEnablePreference hook failed", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val context = param.thisObject as? Context ?: return
                            Settings.Global.putInt(context.contentResolver, "cmc_same_wifi_network_status", 0)
                            if (mdecDeviceType != 0) {
                                val targetStr = if (mdecDeviceType == 2) "sd" else "pd"
                                val current = Settings.Global.getString(context.contentResolver, "cmc_device_type")
                                if (current != targetStr) {
                                    Settings.Global.putString(context.contentResolver, "cmc_device_type", targetStr)
                                }
                            }
                        } catch (t: Throwable) {
                            logError("supportCallAndTextOnOtherDevices: sync Settings.Global failed", t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            logError("supportCallAndTextOnOtherDevices: Application.onCreate hook failed", t)
        }

        if (mdecDeviceType != 0) {
            hookDeviceType(classLoader, mdecDeviceType)
        }
    }

    private fun hookDeviceType(classLoader: ClassLoader, mdecDeviceType: Int) {
        val isTablet = (mdecDeviceType == 2)
        val deviceTypeStr = if (isTablet) "sd" else "pd"

        try {
            @Suppress("UNCHECKED_CAST")
            val cmcDeviceTypeEnumClass = XposedHelpers.findClass(
                "com.samsung.android.mdeccommon.serviceconfig.ServiceConfigEnums\$CMC_DEVICE_TYPE",
                classLoader
            ) as Class<out Enum<*>>
            val targetEnum = java.lang.Enum.valueOf(
                cmcDeviceTypeEnumClass,
                deviceTypeStr
            )
            val unknownEnum = java.lang.Enum.valueOf(
                cmcDeviceTypeEnumClass,
                "unknown"
            )

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.utils.DefaultApplicationUtils",
                    classLoader,
                    "isSamsungMessageInstalled",
                    Context::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (param.args[0] == null) {
                                param.result = false
                            }
                        }
                    }
                )
            } catch (ignored: Throwable) {
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.serviceconfig.ServiceConfigHelper",
                    classLoader,
                    "isMsgSyncCapabilitySupported",
                    XC_MethodReplacement.returnConstant(true)
                )
            } catch (ignored: Throwable) {
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.utils.FeatureUtils",
                    classLoader,
                    "hasTabletFeature",
                    Context::class.java,
                    XC_MethodReplacement.returnConstant(isTablet)
                )
            } catch (t: Throwable) {
                logError("hookDeviceType: hasTabletFeature hook failed", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.utils.CommonUtils",
                    classLoader,
                    "isTablet",
                    XC_MethodReplacement.returnConstant(isTablet)
                )
            } catch (t: Throwable) {
                logError("hookDeviceType: CommonUtils.isTablet hook failed", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.utils.CommonUtils",
                    classLoader,
                    "getDefaultDeviceType",
                    XC_MethodReplacement.returnConstant(targetEnum)
                )
            } catch (t: Throwable) {
                logError("hookDeviceType: getDefaultDeviceType hook failed", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.utils.CommonUtils",
                    classLoader,
                    "isValidDeviceType",
                    Context::class.java,
                    XC_MethodReplacement.returnConstant(true)
                )
            } catch (t: Throwable) {
                logError("hookDeviceType: isValidDeviceType hook failed", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.serviceconfig.ServiceConfigHelper",
                    classLoader,
                    "getCmcDeviceType",
                    Context::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val ctx = param.args[0] as? Context
                            param.result = if (ctx == null) unknownEnum else targetEnum
                        }
                    }
                )
            } catch (t: Throwable) {
                logError("hookDeviceType: getCmcDeviceType hook failed", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.serviceconfig.ServiceConfigHelper",
                    classLoader,
                    "isSd",
                    Context::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val ctx = param.args[0] as? Context
                            param.result = if (ctx == null) false else isTablet
                        }
                    }
                )
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.mdeccommon.serviceconfig.ServiceConfigHelper",
                    classLoader,
                    "isPd",
                    Context::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val ctx = param.args[0] as? Context
                            param.result = if (ctx == null) false else !isTablet
                        }
                    }
                )
            } catch (t: Throwable) {
                logError("hookDeviceType: isSd/isPd hook failed", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.cmcsettings.utils.Utils",
                    classLoader,
                    "isTablet",
                    XC_MethodReplacement.returnConstant(isTablet)
                )
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.cmcsettings.utils.Utils",
                    classLoader,
                    "getMyDeviceType",
                    Context::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val ctx = param.args[0] as? Context
                            param.result = if (ctx == null) 0 else if (isTablet) 2 else 1
                        }
                    }
                )
            } catch (t: Throwable) {
                logError("hookDeviceType: cmcsettings Utils hook failed", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.cmcsettings.utils.CMCDatabaseHelper",
                    classLoader,
                    "myDeviceType",
                    Context::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val ctx = param.args[0] as? Context
                            param.result = if (ctx == null) "" else deviceTypeStr
                        }
                    }
                )
            } catch (t: Throwable) {
                logError("hookDeviceType: CMCDatabaseHelper.myDeviceType hook failed", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.samsung.android.cmcsetting.CmcSettingManager",
                    classLoader,
                    "getOwnDeviceType",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val devTypeEnumClass = XposedHelpers.findClass(
                                    "com.samsung.android.cmcsetting.CmcSettingManagerConstants\$DeviceType",
                                    classLoader
                                )
                                @Suppress("UNCHECKED_CAST")
                                val devTypeEnum = java.lang.Enum.valueOf(
                                    devTypeEnumClass as Class<out Enum<*>>,
                                    if (isTablet) "DEVICE_TYPE_SD" else "DEVICE_TYPE_PD"
                                )
                                param.result = devTypeEnum
                            } catch (ignored: Throwable) {
                            }
                        }
                    }
                )
            } catch (ignored: Throwable) {
            }
        } catch (t: Throwable) {
            logError("hookDeviceType failed", t)
        }
    }
}
