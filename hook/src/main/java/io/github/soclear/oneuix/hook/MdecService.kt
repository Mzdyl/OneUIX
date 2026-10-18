package io.github.soclear.oneuix.hook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.UserManager
import android.provider.Settings
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog
import java.util.concurrent.atomic.AtomicBoolean

object MdecService {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun handleHooks(
        bypassSameWifi: Boolean = false,
        unlockMobileNetwork: Boolean = false,
        bypassChinaSim: Boolean = false,
        useChinaCmcServer: Boolean = false,
        fixCmcPushToken: Boolean = false,
        enableVirtualLanP2p: Boolean = false,
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
                val clazz = classLoader.loadClass("com.samsung.android.mdeccommon.utils.CountryUtils")
                clazz.declaredMethods.filter { it.name == "getDeviceCountryIsoCode" }.forEach {
                    xposedModule.hook(it).intercept { "CN" }
                }
                clazz.declaredMethods.filter { it.name == "getCountryCode" }.forEach {
                    xposedModule.hook(it).intercept { "CN" }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.sdk.smp.common.util.DeviceInfoUtil")
                clazz.declaredMethods.filter { it.name == "isCN" }.forEach {
                    xposedModule.hook(it).intercept { true }
                }
                clazz.declaredMethods.filter { it.name == "getCountryIsoCode" }.forEach {
                    xposedModule.hook(it).intercept { "CN" }
                }
                clazz.declaredMethods.filter { it.name == "isSupportCountryCode" }.forEach {
                    xposedModule.hook(it).intercept { true }
                }
                clazz.declaredMethods.filter { it.name == "hasSppForceActivationFeature" }.forEach {
                    xposedModule.hook(it).intercept { true }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.sdk.smp.push.PushHelper")
                clazz.declaredMethods.filter { it.name == "handleSppDeactivateEvent" }.forEach {
                    xposedModule.hook(it).intercept { null }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.sdk.smp.common.preference.PrefManager")
                clazz.declaredMethods.filter { it.name == "isSppDeactivated" }.forEach {
                    xposedModule.hook(it).intercept { false }
                }
                clazz.declaredMethods.filter { it.name == "isSmpDeactivated" }.forEach {
                    xposedModule.hook(it).intercept { false }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.sdk.smp.push.PushHelperFeature")
                clazz.declaredMethods.filter { it.name == "getRegisterPushType" }.forEach {
                    xposedModule.hook(it).intercept { "spp" }
                }
                clazz.declaredMethods.filter { it.name == "isHKMO" }.forEach {
                    xposedModule.hook(it).intercept { false }
                }
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
                        val newArgs = chain.args.toTypedArray()
                        newArgs[1] = "acs-central-cn1.mdc-prd.cn"
                        return@intercept chain.proceed(newArgs)
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
                        val newArgs = chain.args.toTypedArray()
                        newArgs[1] = "es-central-cn1.mdc-prd.cn"
                        return@intercept chain.proceed(newArgs)
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
                        val newArgs = chain.args.toTypedArray()
                        newArgs[1] = "acs-central-cn1.mdc-prd.cn"
                        return@intercept chain.proceed(newArgs)
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
                        val newArgs = chain.args.toTypedArray()
                        newArgs[1] = "https://es-central-cn1.mdc-prd.cn"
                        return@intercept chain.proceed(newArgs)
                    }
                    chain.proceed()
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                val saInfoClass = classLoader.loadClass("com.samsung.android.mdeccommon.obj.SamsungAccountInfo")
                val setApiServerUrlMethod = saInfoClass.getDeclaredMethod("setApiServerUrl", String::class.java)
                val method = clazz.getDeclaredMethod("getSaInfo", Context::class.java)
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (result != null) {
                        setApiServerUrlMethod.invoke(result, "cn-auth2.samsungosp.com.cn")
                    }
                    result
                }

                clazz.declaredMethods.filter { it.name == "setSaInfoWithCmcDeviceId" }.forEach { m ->
                    xposedModule.hook(m).intercept { chain ->
                        val saInfo = chain.args.getOrNull(1)
                        if (saInfo != null) {
                            setApiServerUrlMethod.invoke(saInfo, "cn-auth2.samsungosp.com.cn")
                        }
                        chain.proceed()
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val receiverClass = classLoader.loadClass("com.samsung.android.mdecservice.authentication.SaBroadcastReceiver")
                receiverClass.declaredMethods.filter { it.name == "onReceive" }.forEach { m ->
                    xposedModule.hook(m).intercept { chain ->
                        val intent = chain.args.getOrNull(1) as? Intent
                        if (intent != null) {
                            val apiServerUrl = intent.getStringExtra("api_server_url")
                            if (apiServerUrl != null && !apiServerUrl.contains(".cn")) {
                                intent.putExtra("api_server_url", "cn-auth2.samsungosp.com.cn")
                            }
                            val serverUrl = intent.getStringExtra("server_url")
                            if (serverUrl != null && !serverUrl.contains(".cn")) {
                                intent.putExtra("server_url", "cn-auth2.samsungosp.com.cn")
                            }
                            val authUrl = intent.getStringExtra("auth_server_url")
                            if (authUrl != null && !authUrl.contains(".cn")) {
                                intent.putExtra("auth_server_url", "cn-auth2.samsungosp.com.cn")
                            }
                        }
                        chain.proceed()
                    }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val clazz = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.restapiclient.HttpRequest")
                clazz.declaredMethods.filter { it.name == "setInternalConnectionParam" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        if (chain.args.size >= 4) {
                            val authServerUrl = chain.args[3] as? String
                            if (authServerUrl != null && !authServerUrl.contains(".cn")) {
                                val newArgs = chain.args.toTypedArray()
                                newArgs[3] = "cn-auth2.samsungosp.com.cn"
                                return@intercept chain.proceed(newArgs)
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
                    val ctx = chain.args.firstOrNull() as? Context
                    val sppInstalled = ctx?.let {
                        runCatching { it.packageManager.getPackageInfo("com.sec.spp.push", 0) }.isSuccess
                    } ?: false
                    if (result == "0601654ea47c88733b28e4dd16337de73ca565708951ceaddcccf31864bbff88d3a6ead706818c09dfaa5201e359ccc58680") {
                        null
                    } else if (!sppInstalled && result.isNullOrEmpty()) {
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
                    val ctx = chain.args.firstOrNull() as? Context
                    val sppInstalled = ctx?.let {
                        runCatching { it.packageManager.getPackageInfo("com.sec.spp.push", 0) }.isSuccess
                    } ?: false
                    if (result == "0601654ea47c88733b28e4dd16337de73ca565708951ceaddcccf31864bbff88d3a6ead706818c09dfaa5201e359ccc58680") {
                        null
                    } else if (!sppInstalled && result.isNullOrEmpty()) {
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

        if (bypassSameWifi || enableVirtualLanP2p) {
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
                        val status = if (useChinaCmcServer || enableVirtualLanP2p) 1 else 0
                        Settings.Global.putInt(ctx.contentResolver, "cmc_same_wifi_network_status", status)
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
            val clazz = classLoader.loadClass("com.samsung.android.mdecservice.nms.database.provider.TelephonyStorageAdapter")
            val insertMethod = clazz.getDeclaredMethod("insertToAndroidDB", Uri::class.java, ContentValues::class.java)
            xposedModule.hook(insertMethod).intercept { chain ->
                val uri = chain.args[0] as? Uri
                val cv = chain.args[1] as? ContentValues
                if (uri != null && cv != null && uri.toString().contains("call")) {
                    val subComp = cv.getAsString("subscription_component_name")
                    if (subComp == null || subComp.contains("mdecservice")) {
                        cv.put("subscription_component_name", "com.android.phone/com.android.services.telephony.TelephonyConnectionService")
                    }
                    val subId = cv.getAsString("subscription_id")
                    val newSubId = if (subId == "2") "CMC_1" else "CMC_0"
                    cv.put("subscription_id", newSubId)
                }
                chain.proceed()
            }

            val updateMethod = clazz.getDeclaredMethod("update", Uri::class.java, ContentValues::class.java, String::class.java, Array<String>::class.java)
            xposedModule.hook(updateMethod).intercept { chain ->
                val uri = chain.args[0] as? Uri
                val cv = chain.args[1] as? ContentValues
                if (uri != null && cv != null && uri.toString().contains("call")) {
                    if (cv.containsKey("subscription_component_name")) {
                        val subComp = cv.getAsString("subscription_component_name")
                        if (subComp == null || subComp.contains("mdecservice")) {
                            cv.put("subscription_component_name", "com.android.phone/com.android.services.telephony.TelephonyConnectionService")
                        }
                    }
                    if (cv.containsKey("subscription_id")) {
                        val subId = cv.getAsString("subscription_id")
                        val newSubId = if (subId == "2") "CMC_1" else "CMC_0"
                        cv.put("subscription_id", newSubId)
                    }
                }
                chain.proceed()
            }
        }.onFailure { xlog(it) }

        runCatching {
            val daoClass = classLoader.loadClass("com.samsung.android.cmcsettings.db.dao.SecondaryDeviceDao_Impl")
            daoClass.declaredMethods.filter { it.name == "getDeviceActiveStatus" || it.name == "getCallActiveStatus" }.forEach { m ->
                xposedModule.hook(m).intercept { 1 }
            }
            daoClass.declaredMethods.filter { it.name == "addConnectedDevice" }.forEach { m ->
                xposedModule.hook(m).intercept { chain ->
                    val model = chain.args.firstOrNull()
                    if (model != null) {
                        try {
                            model.javaClass.getDeclaredMethod("setDevice_active", Int::class.javaPrimitiveType).invoke(model, 1)
                            model.javaClass.getDeclaredMethod("setCall_active", Int::class.javaPrimitiveType).invoke(model, 1)
                        } catch (_: Throwable) {}
                    }
                    chain.proceed()
                }
            }
            val modelClass = classLoader.loadClass("com.samsung.android.cmcsettings.db.entity.SecondaryDeviceModel")
            modelClass.declaredMethods.filter { it.name == "getDevice_active" || it.name == "getCall_active" }.forEach { m ->
                xposedModule.hook(m).intercept { 1 }
            }
        }.onFailure { xlog(it) }

        runCatching {
            val listenerClass = classLoader.loadClass("com.samsung.android.mdecservice.nms.client.config.SamsungAccountManager\$1")
            val method = listenerClass.getDeclaredMethod(
                "onRecvSamsungAccountInfo",
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java
            )
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                val listenerObj = chain.thisObject
                runCatching {
                    val samField = listenerObj.javaClass.getDeclaredField("this$0").apply { isAccessible = true }
                    val sam = samField.get(listenerObj) ?: return@runCatching
                    val samClass = sam.javaClass
                    val saAccessTokenField = samClass.getDeclaredField("mSaAccessToken").apply { isAccessible = true }
                    val saIdField = samClass.getDeclaredField("mSaId").apply { isAccessible = true }
                    val saAuthUrlField = samClass.getDeclaredField("mSaAuthUrl").apply { isAccessible = true }
                    val entitlementManField = samClass.getDeclaredField("mEntitlementProfileMan").apply { isAccessible = true }
                    val nmsClientManField = samClass.getDeclaredField("mNmsClientMan").apply { isAccessible = true }

                    val accountId = chain.args.getOrNull(0) as? String ?: ""
                    val accessToken = chain.args.getOrNull(1) as? String ?: ""
                    var authUrl = chain.args.getOrNull(3) as? String ?: ""

                    if (accessToken.isNotEmpty()) {
                        saAccessTokenField.set(sam, accessToken)
                    }
                    if (accountId.isNotEmpty()) {
                        saIdField.set(sam, accountId)
                    }
                    if (useChinaCmcServer && (authUrl.isEmpty() || !authUrl.contains(".cn"))) {
                        authUrl = "cn-auth2.samsungosp.com.cn"
                    } else if (authUrl.isEmpty()) {
                        val ctx = runCatching {
                            samClass.getDeclaredField("mContext").apply { isAccessible = true }.get(sam) as? Context
                        }.getOrNull()
                        if (ctx != null) {
                            runCatching {
                                val daoClass = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                                val getSaInfoMethod = daoClass.getDeclaredMethod("getSaInfo", Context::class.java)
                                val saInfo = getSaInfoMethod.invoke(null, ctx)
                                if (saInfo != null) {
                                    val api = saInfo.javaClass.getDeclaredMethod("getApiServerUrl").invoke(saInfo) as? String
                                    if (!api.isNullOrEmpty()) authUrl = api
                                }
                            }
                        }
                        if (authUrl.isEmpty() && useChinaCmcServer) {
                            authUrl = "cn-auth2.samsungosp.com.cn"
                        }
                    }
                    if (authUrl.isNotEmpty()) {
                        saAuthUrlField.set(sam, authUrl)
                    }

                    val entitlementMan = entitlementManField.get(sam)
                    if (entitlementMan != null) {
                        val isReadyMethod = entitlementMan.javaClass.getDeclaredMethod("isReadyToSync")
                        val isReady = isReadyMethod.invoke(entitlementMan) as? Boolean ?: false
                        if (!isReady && accessToken.isNotEmpty()) {
                            xlog("OneUIX: SamsungAccountManager onRecvSamsungAccountInfo - waking up EntitlementProfileManager")
                            val nmsClientMan = nmsClientManField.get(sam)
                            if (nmsClientMan != null) {
                                val onSaChangedMethod = nmsClientMan.javaClass.getDeclaredMethod(
                                    "onSamsungAccountInfoChanged",
                                    String::class.java,
                                    String::class.java
                                )
                                onSaChangedMethod.invoke(nmsClientMan, accessToken, authUrl)
                            }
                            entitlementMan.javaClass.getDeclaredMethod("updateEntitlementState").invoke(entitlementMan)
                        }
                    }
                }.onFailure { xlog(it) }
                result
            }
        }.onFailure { xlog(it) }

        runCatching {
            val samClass = classLoader.loadClass("com.samsung.android.mdecservice.nms.client.config.SamsungAccountManager")
            val isSaInfoReadyMethod = samClass.getDeclaredMethod("isSaInfoReady")
            xposedModule.hook(isSaInfoReadyMethod).intercept { chain ->
                val original = chain.proceed() as? Boolean ?: false
                if (original) {
                    true
                } else {
                    val sam = chain.thisObject
                    val saAccessTokenField = sam.javaClass.getDeclaredField("mSaAccessToken").apply { isAccessible = true }
                    val saIdField = sam.javaClass.getDeclaredField("mSaId").apply { isAccessible = true }
                    val saAuthUrlField = sam.javaClass.getDeclaredField("mSaAuthUrl").apply { isAccessible = true }
                    val token = saAccessTokenField.get(sam) as? String
                    val id = saIdField.get(sam) as? String
                    var authUrl = saAuthUrlField.get(sam) as? String
                    if (useChinaCmcServer && (authUrl.isNullOrEmpty() || !authUrl.contains(".cn"))) {
                        authUrl = "cn-auth2.samsungosp.com.cn"
                        saAuthUrlField.set(sam, authUrl)
                    } else if (authUrl.isNullOrEmpty()) {
                        val ctx = runCatching {
                            sam.javaClass.getDeclaredField("mContext").apply { isAccessible = true }.get(sam) as? Context
                        }.getOrNull()
                        if (ctx != null) {
                            runCatching {
                                val daoClass = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                                val getSaInfoMethod = daoClass.getDeclaredMethod("getSaInfo", Context::class.java)
                                val saInfo = getSaInfoMethod.invoke(null, ctx)
                                if (saInfo != null) {
                                    val api = saInfo.javaClass.getDeclaredMethod("getApiServerUrl").invoke(saInfo) as? String
                                    if (!api.isNullOrEmpty()) authUrl = api
                                }
                            }
                        }
                        if (authUrl.isNullOrEmpty() && useChinaCmcServer) {
                            authUrl = "cn-auth2.samsungosp.com.cn"
                        }
                        if (!authUrl.isNullOrEmpty()) {
                            saAuthUrlField.set(sam, authUrl)
                        }
                    }
                    !token.isNullOrEmpty() && !id.isNullOrEmpty() && !authUrl.isNullOrEmpty()
                }
            }
        }.onFailure { xlog(it) }


        runCatching {
            val method = Application::class.java.getDeclaredMethod("onCreate")
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                runCatching {
                    val context = chain.thisObject as? Context ?: return@runCatching
                    if (useChinaCmcServer || enableVirtualLanP2p) {
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
                    // Ensure Samsung Push Service (SPP) is started and active
                    runCatching {
                        val sppIntent = Intent().apply {
                            component = ComponentName("com.sec.spp.push", "com.sec.spp.push.PushClientService")
                        }
                        context.startService(sppIntent)
                    }.onFailure { xlog(it) }

                    if (fixCmcPushToken) {
                        runCatching {
                            val sppInstalled = runCatching {
                                context.packageManager.getPackageInfo("com.sec.spp.push", 0)
                            }.isSuccess
                            if (!sppInstalled) {
                                val daoClass = classLoader.loadClass("com.samsung.android.mdecservice.entitlement.provider.dao.EntitlementProviderDao")
                                val getPushToken = daoClass.getDeclaredMethod("getPushToken", Context::class.java)
                                val currentToken = getPushToken.invoke(null, context) as? String
                                if (currentToken.isNullOrEmpty()) {
                                    val setPushInfo = daoClass.getDeclaredMethod("setPushInfo", Context::class.java, String::class.java, String::class.java)
                                    setPushInfo.invoke(null, context, "smp-spp", "0601654ea47c88733b28e4dd16337de73ca565708951ceaddcccf31864bbff88d3a6ead706818c09dfaa5201e359ccc58680")
                                }
                            }
                        }.onFailure { xlog(it) }
                    }
                    migrateCallLogsWhenUnlocked(context)
                }.onFailure { xlog(it) }
                result
            }
        }.onFailure { xlog(it) }

        if (mdecDeviceType != 0) {
            hookDeviceType(classLoader, mdecDeviceType)
        }
    }

    context(xposedModule: XposedModule)
    private fun migrateCallLogsWhenUnlocked(context: Context) {
        val userManager = context.getSystemService(UserManager::class.java) ?: return
        if (userManager.isUserUnlocked) {
            migrateCallLogs(context)
            return
        }
        val pending = AtomicBoolean(true)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_UNLOCKED && pending.compareAndSet(true, false)) {
                    context.unregisterReceiver(this)
                    migrateCallLogs(context)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), Context.RECEIVER_NOT_EXPORTED)
        if (userManager.isUserUnlocked && pending.compareAndSet(true, false)) {
            context.unregisterReceiver(receiver)
            migrateCallLogs(context)
        }
    }

    context(xposedModule: XposedModule)
    private fun migrateCallLogs(context: Context) {
        Thread({
            runCatching {
                val component = "${Package.PHONE}/com.android.services.telephony.TelephonyConnectionService"
                val cv0 = ContentValues().apply {
                    put("subscription_id", "CMC_0")
                    put("subscription_component_name", component)
                }
                context.contentResolver.update(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    cv0,
                    "subscription_component_name = ? AND (subscription_id = ? OR subscription_id = ?)",
                    arrayOf(Package.MDEC_SERVICE, "1", "0")
                )
                val cv1 = ContentValues().apply {
                    put("subscription_id", "CMC_1")
                    put("subscription_component_name", component)
                }
                context.contentResolver.update(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    cv1,
                    "subscription_component_name = ? AND subscription_id = ?",
                    arrayOf(Package.MDEC_SERVICE, "2")
                )
            }.onFailure { xlog(it) }
        }, "OneUIX-CallLogMigration").start()
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
