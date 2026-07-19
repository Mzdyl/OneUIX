package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.log
import io.github.soclear.oneuix.hook.util.logError
import java.lang.reflect.Modifier
import java.util.Locale

object SamsungHealth {
    const val SERVER_REGION_DEFAULT = 0
    const val SERVER_REGION_CHINA = 1
    const val SERVER_REGION_GLOBAL = 2

    private const val ACCOUNT_OPERATION_CLASS =
        "com.samsung.android.sdk.healthdata.privileged.AccountOperation"
    private const val ACCOUNT_SERVER_API_UTIL_CLASS =
        "com.samsung.android.sdk.util.AccountServerApiUtil"
    private const val SERVER_API_UTIL_CLASS =
        "com.samsung.android.service.health.util.ServerApiUtil"
    private const val COUNTRY_CODE_CONDITION_CLASS =
        "com.samsung.android.app.shealth.home.settings.define.CountryCodeCondition"
    private const val CSC_FEATURE_CLASS =
        "com.samsung.android.app.shealth.config.HCscFeature"
    private const val KIDS_INIT_STATUS_RESPONSE_CLASS =
        "com.samsung.android.app.shealth.home.watchsettings.viewmodel.KidsInitStatusResponse"
    private const val ACCESSORY_INFO_CLASS =
        "com.samsung.android.app.shealth.sensor.accessory.service.data.accessoryinfo.AccessoryInfoInternal"

    fun init(loadPackageParam: LoadPackageParam, preference: Preference.SamsungHealth) {
        if (loadPackageParam.packageName != Package.SAMSUNG_HEALTH) return
        afterAttach {
            if (preference.bypassAccountCountryCheck) {
                hookAccountCountryCheck(classLoader)
            }
            if (preference.serverRegion != SERVER_REGION_DEFAULT) {
                hookServerRegion(classLoader, preference.serverRegion)
            }
            if (preference.unlockCountryFeatures) {
                hookCountryFeatures(classLoader)
            }
            if (preference.unlockAccessoryProfiles) {
                hookAccessoryProfiles(classLoader)
            }
        }
    }

    private fun hookAccountCountryCheck(classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(ACCOUNT_OPERATION_CLASS)
            setOf("isValidSync", "isLegalCountry", "isSyncLegal").forEach { methodName ->
                XposedBridge.hookAllMethods(
                    clazz,
                    methodName,
                    XC_MethodReplacement.returnConstant(true)
                )
            }
            log("SamsungHealth account country checks bypassed")
        } catch (t: Throwable) {
            logError("SamsungHealth account country bypass failed", t)
        }
    }

    private fun hookServerRegion(classLoader: ClassLoader, serverRegion: Int) {
        val endpoints = when (serverRegion) {
            SERVER_REGION_CHINA -> mapOf(
                "getCloudServerEndPoint" to "https://api.samsungcloudcn.com",
                "getE2eeServerEndPoint" to "https://api.samsungcloudcn.com",
                "getPrdHealthServerEndPoint" to "https://health-api.samsunghealthcn.com",
                "getStgHealthServerEndPoint" to "https://health-api.samsunghealthcn.com",
                "getPrdKnowledgeServerEndPoint" to
                    "https://api.samsunghealthcn.com/knowledge-ws/v1.3/",
                "getStgKnowledgeServerEndPoint" to
                    "https://api-stg.samsungknowledge.cn/knowledge-ws/v1.3/"
            )

            SERVER_REGION_GLOBAL -> mapOf(
                "getCloudServerEndPoint" to "https://api.samsungcloud.com",
                "getE2eeServerEndPoint" to "https://api.samsungcloud.com",
                "getPrdHealthServerEndPoint" to "https://shealth-api.samsunghealth.com",
                "getStgHealthServerEndPoint" to "https://shealth-stg-api.samsunghealth.com",
                "getPrdKnowledgeServerEndPoint" to
                    "https://api.samsungknowledge.com/knowledge-ws/v1.3/",
                "getStgKnowledgeServerEndPoint" to
                    "https://api-stg.samsungknowledge.com/knowledge-ws/v1.3/"
            )

            else -> return
        }

        try {
            val clazz = classLoader.loadClass(SERVER_API_UTIL_CLASS)
            endpoints.forEach { (methodName, endpoint) ->
                clazz.declaredMethods
                    .filter {
                        it.name == methodName && Modifier.isStatic(it.modifiers) &&
                            it.parameterCount == 0 && it.returnType == String::class.java
                    }
                    .forEach {
                        XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(endpoint))
                    }
            }
            hookSamsungAccountServer(classLoader, serverRegion)
            log("SamsungHealth server region forced to $serverRegion")
        } catch (t: Throwable) {
            logError("SamsungHealth server region hook failed", t)
        }
    }

    private fun hookSamsungAccountServer(classLoader: ClassLoader, serverRegion: Int) {
        try {
            val clazz = classLoader.loadClass(ACCOUNT_SERVER_API_UTIL_CLASS)
            clazz.declaredMethods
                .filter {
                    it.name == "getSaUrl" && Modifier.isStatic(it.modifiers) &&
                        it.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                        it.returnType == String::class.java
                }
                .forEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any {
                            if (serverRegion == SERVER_REGION_CHINA) {
                                return "https://account.samsung.cn"
                            }
                            val country = (param.args[0] as? String).orEmpty()
                                .uppercase(Locale.US)
                            return if (country == "US") {
                                "https://us.account.samsung.com"
                            } else {
                                "https://account.samsung.com"
                            }
                        }
                    })
                }
        } catch (t: Throwable) {
            logError("SamsungHealth account server hook failed", t)
        }
    }

    private fun hookCountryFeatures(classLoader: ClassLoader) {
        try {
            val cscFeatureClass = classLoader.loadClass(CSC_FEATURE_CLASS)
            XposedBridge.hookAllMethods(
                cscFeatureClass,
                "isAllowed",
                XC_MethodReplacement.returnConstant(true)
            )
        } catch (t: Throwable) {
            logError("SamsungHealth CSC feature hook failed", t)
        }

        try {
            val conditionClass = classLoader.loadClass(COUNTRY_CODE_CONDITION_CLASS)
            conditionClass.declaredMethods
                .filter {
                    it.returnType == Boolean::class.javaPrimitiveType &&
                        it.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
                .forEach {
                    XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(true))
                }
            log("SamsungHealth country features unlocked")
        } catch (t: Throwable) {
            logError("SamsungHealth country condition hook failed", t)
        }

        try {
            val responseClass = classLoader.loadClass(KIDS_INIT_STATUS_RESPONSE_CLASS)
            mapOf(
                "getIsBlocked" to false,
                "getIsCountryMatched" to true,
                "getIsSupportedCountry" to true
            ).forEach { (methodName, result) ->
                XposedBridge.hookAllMethods(
                    responseClass,
                    methodName,
                    XC_MethodReplacement.returnConstant(result)
                )
            }
        } catch (t: Throwable) {
            logError("SamsungHealth watch country status hook failed", t)
        }
    }

    private fun hookAccessoryProfiles(classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(ACCESSORY_INFO_CLASS)
            clazz.declaredMethods
                .filter {
                    Modifier.isStatic(it.modifiers) &&
                        it.returnType == Boolean::class.javaPrimitiveType &&
                        it.parameterTypes.contentEquals(
                            arrayOf(Int::class.javaPrimitiveType)
                        )
                }
                .forEach {
                    XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(true))
                }
            log("SamsungHealth accessory profiles unlocked")
        } catch (t: Throwable) {
            logError("SamsungHealth accessory profile hook failed", t)
        }
    }
}
