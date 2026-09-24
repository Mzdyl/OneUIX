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
        useChinaCmcServer: Boolean = false,
        fixCmcPushToken: Boolean = false,
        mdecDeviceType: Int = 0
    ) {
        if (param.packageName != Package.MDEC_SERVICE) return
        val classLoader = param.classLoader

        if (bypassChinaSim) {
            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.cmcsettings.utils.Utils")
                val method = clazz.getDeclaredMethod("isChinaSimActiveInGlobalPD", Context::class.java)
                xposedModule.hook(method).intercept { false }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.SimUtils")
                val method = clazz.getDeclaredMethod("isChinaSIMActive", Context::class.java)
                xposedModule.hook(method).intercept { false }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.preference.TestModeVerityState")
                val method = clazz.getDeclaredMethod("isTestModeVerifyState", Context::class.java)
                xposedModule.hook(method).intercept { true }
            }.onFailure { xlog(it) }
        }

        if (useChinaCmcServer) {
            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.CountryUtils")
                val method = clazz.getDeclaredMethod("isChinaDevice")
                xposedModule.hook(method).intercept { true }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.CountryUtils")
                val method = clazz.getDeclaredMethod("isKoreaOrChinaDevice", Context::class.java)
                xposedModule.hook(method).intercept { true }
            }.onFailure { xlog(it) }

            runCatching {
                val factoryClass = classLoader.loadClass("com.samsung.android.mdecservice.push.TypePushFactory")
                val smpPushClass = classLoader.loadClass("com.samsung.android.mdecservice.push.type.SMPPush")
                val method = factoryClass.getDeclaredMethod("createPush", Context::class.java)
                val ctor = smpPushClass.getConstructor(Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val ctx = chain.args.firstOrNull() as? Context
                    if (ctx != null) {
                        ctor.newInstance(ctx)
                    } else {
                        chain.proceed()
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("getDefaultAcsAddrFromDb", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed() as? String
                    if (result.isNullOrEmpty() || result.contains("samsungmdec.com")) {
                        "acs-central-cn1.mdc-prd.cn"
                    } else {
                        result
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("getGlobalEntitlementServiceAddress", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed() as? String
                    if (result.isNullOrEmpty() || result.contains("samsungmdec.com")) {
                        "es-central-cn1.mdc-prd.cn"
                    } else {
                        result
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("getSingleServerInfo", Context::class.java)
                val serverAddrInfoClass = classLoader.loadClass("com.samsung.android.mdeccommon.obj.ServerAddrInfo")
                val setLocalAcsAddrMethod = serverAddrInfoClass.getDeclaredMethod("setLocalAcsAddr", String::class.java)
                val setEsAddrMethod = serverAddrInfoClass.getDeclaredMethod("setEsAddr", String::class.java)
                val getLocalAcsAddrMethod = serverAddrInfoClass.getDeclaredMethod("getLocalAcsAddr")
                val getEsAddrMethod = serverAddrInfoClass.getDeclaredMethod("getEsAddr")

                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (result == null) {
                        val info = serverAddrInfoClass.getConstructor().newInstance()
                        setLocalAcsAddrMethod.invoke(info, "acs-central-cn1.mdc-prd.cn")
                        setEsAddrMethod.invoke(info, "https://es-central-cn1.mdc-prd.cn")
                        info
                    } else {
                        val localAcs = getLocalAcsAddrMethod.invoke(result) as? String
                        val es = getEsAddrMethod.invoke(result) as? String
                        if (localAcs.isNullOrEmpty() || localAcs.contains("samsungmdec.com")) {
                            setLocalAcsAddrMethod.invoke(result, "acs-central-cn1.mdc-prd.cn")
                        }
                        if (es.isNullOrEmpty() || es.contains("samsungmdec.com")) {
                            setEsAddrMethod.invoke(result, "https://es-central-cn1.mdc-prd.cn")
                        }
                        result
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("updateSpecificDefaultAcs", Context::class.java, String::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val addr = chain.args[1] as? String
                    if (addr != null && addr.contains("samsungmdec.com")) {
                        chain.args[1] = "acs-central-cn1.mdc-prd.cn"
                    }
                    chain.proceed()
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("updateGlobalEntitlementServerAddress", Context::class.java, String::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val addr = chain.args[1] as? String
                    if (addr != null && addr.contains("samsungmdec.com")) {
                        chain.args[1] = "es-central-cn1.mdc-prd.cn"
                    }
                    chain.proceed()
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("setLocalAcsAddr", Context::class.java, String::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val addr = chain.args[1] as? String
                    if (addr != null && addr.contains("samsungmdec.com")) {
                        chain.args[1] = "acs-central-cn1.mdc-prd.cn"
                    }
                    chain.proceed()
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("setEsAddr", Context::class.java, String::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val addr = chain.args[1] as? String
                    if (addr != null && addr.contains("samsungmdec.com")) {
                        chain.args[1] = "https://es-central-cn1.mdc-prd.cn"
                    }
                    chain.proceed()
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("getSaInfo", Context::class.java)
                val saInfoClass = classLoader.loadClass("com.samsung.android.mdeccommon.obj.SamsungAccountInfo")
                val setApiServerUrlMethod = saInfoClass.getDeclaredMethod("setApiServerUrl", String::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (result != null) {
                        setApiServerUrlMethod.invoke(result, "cn-auth2.samsungosp.com.cn")
                    }
                    result
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.restapiclient.HttpRequest")
                clazz.declaredMethods.filter { it.name == "setInternalConnectionParam" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        if (chain.args.size >= 4) {
                            val authServerUrl = chain.args[3] as? String
                            if (authServerUrl != null && !authServerUrl.contains(".cn")) {
                                chain.args[3] = "cn-auth2.samsungosp.com.cn"
                            }
                        }
                        chain.proceed()
                    }
                }
            }.onFailure { xlog(it) }
        }

        if (fixCmcPushToken) {
            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("getPushToken", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed() as? String
                    if (result.isNullOrEmpty()) {
                        "0601654ea47c88733b28e4dd16337de73ca565708951ceaddcccf31864bbff88d3a6ead706818c09dfaa5201e359ccc58680"
                    } else {
                        result
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val method = clazz.getDeclaredMethod("getPushType", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed() as? String
                    if (result.isNullOrEmpty() || result == "smp-fcm" || result == "fcm") {
                        "smp-spp"
                    } else {
                        result
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.sdk.smp.Smp")
                val method = clazz.getDeclaredMethod("getPushToken", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed() as? String
                    if (result.isNullOrEmpty()) {
                        "0601654ea47c88733b28e4dd16337de73ca565708951ceaddcccf31864bbff88d3a6ead706818c09dfaa5201e359ccc58680"
                    } else {
                        result
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.sdk.smp.Smp")
                val method = clazz.getDeclaredMethod("getPushType", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed() as? String
                    if (result.isNullOrEmpty() || result == "fcm") {
                        "spp"
                    } else {
                        result
                    }
                }
            }.onFailure { xlog(it) }
        }

        if (bypassSameWifi) {
            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.CommonUtils")
                val method = clazz.getDeclaredMethod("isSameWifiRequiredForCall", Context::class.java)
                xposedModule.hook(method).intercept { false }
            }.onFailure { xlog(it) }

            if (!useChinaCmcServer) {
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
                    if (useChinaCmcServer) {
                        Settings.Global.putInt(context.contentResolver, "cmc_same_wifi_network_status", 1)
                    } else if (bypassSameWifi) {
                        Settings.Global.putInt(context.contentResolver, "cmc_same_wifi_network_status", 0)
                    }
                    if (mdecDeviceType != 0) {
                        val targetStr = if (mdecDeviceType == 2) "sd" else "pd"
                        val current = Settings.Global.getString(context.contentResolver, "cmc_device_type")
                        if (current != targetStr) {
                            Settings.Global.putString(context.contentResolver, "cmc_device_type", targetStr)
                        }
                    }
                    if (useChinaCmcServer) {
                        runCatching {
                            val daoClass = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                            val updateAcs = daoClass.getDeclaredMethod("updateSpecificDefaultAcs", Context::class.java, String::class.java)
                            val updateEs = daoClass.getDeclaredMethod("updateGlobalEntitlementServerAddress", Context::class.java, String::class.java)
                            val setLocal = daoClass.getDeclaredMethod("setLocalAcsAddr", Context::class.java, String::class.java)
                            val setEs = daoClass.getDeclaredMethod("setEsAddr", Context::class.java, String::class.java)
                            updateAcs.invoke(null, context, "acs-central-cn1.mdc-prd.cn")
                            updateEs.invoke(null, context, "es-central-cn1.mdc-prd.cn")
                            setLocal.invoke(null, context, "acs-central-cn1.mdc-prd.cn")
                            setEs.invoke(null, context, "https://es-central-cn1.mdc-prd.cn")
                            val cv = android.content.ContentValues()
                            cv.put("API_SERVER_URL", "cn-auth2.samsungosp.com.cn")
                            context.contentResolver.update(
                                android.net.Uri.parse("content://com.samsung.android.mdecservice.entitlementprovider/sainfo"),
                                cv, null, null
                            )
                        }.onFailure { xlog(it) }
                    }
                    if (fixCmcPushToken) {
                        runCatching {
                            val daoClass = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                            val getPushToken = daoClass.getDeclaredMethod("getPushToken", Context::class.java)
                            val currentToken = getPushToken.invoke(null, context) as? String
                            if (currentToken.isNullOrEmpty()) {
                                val setPushInfo = daoClass.getDeclaredMethod("setPushInfo", Context::class.java, String::class.java, String::class.java)
                                setPushInfo.invoke(null, context, "smp-spp", "0601654ea47c88733b28e4dd16337de73ca565708951ceaddcccf31864bbff88d3a6ead706818c09dfaa5201e359ccc58680")
                            }
                        }.onFailure { xlog(it) }
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
            useChinaCmcServer = true,
            fixCmcPushToken = true,
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
