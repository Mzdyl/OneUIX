package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.log
import io.github.soclear.oneuix.hook.util.logError
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
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
    private const val RECOVERABLE_ACCOUNT_OPERATION_CLASS =
        "com.samsung.android.app.shealth.data.recoverable.RecoverableAccountOperationKt"
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
    private const val PERMANENT_PREFERENCES_MAIN = "permanent_sharedpreferences_main"
    private const val PERMANENT_PREFERENCES_REMOTE = "permanent_sharedpreferences_remote"
    private const val BLOCKED_ACCOUNT_KEY = "sam_is_blocked_by_not_allowed_account"
    private const val LEGACY_BLOCKED_ACCOUNT_KEY = "sam_is_not_allowed_account_state"
    private const val GLOBAL_ACCOUNT_COUNTRY = "US"

    fun init(loadPackageParam: LoadPackageParam, preference: Preference.SamsungHealth) {
        if (loadPackageParam.packageName != Package.SAMSUNG_HEALTH) return
        afterAttach {
            if (preference.bypassAccountCountryCheck) {
                hookAccountCountryCheck(this, classLoader)
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

    private fun hookAccountCountryCheck(context: Context, classLoader: ClassLoader) {
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

        try {
            val clazz = classLoader.loadClass(RECOVERABLE_ACCOUNT_OPERATION_CLASS)
            clazz.declaredMethods
                .filter {
                    Modifier.isStatic(it.modifiers) &&
                        it.returnType == String::class.java &&
                        it.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
                .forEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if ((param.result as? String).equals("CN", ignoreCase = true)) {
                                param.result = GLOBAL_ACCOUNT_COUNTRY
                            }
                        }
                    })
                }
            log("SamsungHealth China account country treated as global")
        } catch (t: Throwable) {
            logError("SamsungHealth China account country hook failed", t)
        }

        try {
            clearBlockedAccountState(context)
            val config = context.getHookConfig(
                File(context.filesDir, "SamsungHealthHookConfig.json")
            ) {
                getAccountRestrictionHookConfig()
            } ?: error("Allowed-account result class not found")
            val clazz = classLoader.loadClass(config.allowedAccountResultClass)
            XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args.size != 3 || param.args[0] !is Boolean ||
                        param.args[1] !is Boolean || param.args[2] !is Boolean
                    ) {
                        return
                    }
                    param.args[0] = true
                    param.args[1] = true
                    param.args[2] = false
                }
            })
            hookRestrictedChinaDialog(classLoader, config)
            log("SamsungHealth blocked China account state bypassed")
        } catch (t: Throwable) {
            logError("SamsungHealth blocked China account bypass failed", t)
        }
    }

    private fun clearBlockedAccountState(context: Context) {
        listOf(PERMANENT_PREFERENCES_MAIN, PERMANENT_PREFERENCES_REMOTE).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .remove(BLOCKED_ACCOUNT_KEY)
                .remove(LEGACY_BLOCKED_ACCOUNT_KEY)
                .apply()
        }
    }

    @Serializable
    private data class SamsungHealthHookConfig(
        override val versionCode: Long,
        val allowedAccountResultClass: String,
        val restrictedChinaHandlerMethod: String,
    ) : HookConfig

    private fun hookRestrictedChinaDialog(
        classLoader: ClassLoader,
        config: SamsungHealthHookConfig,
    ) {
        val method = DexMethod(config.restrictedChinaHandlerMethod)
            .getMethodInstance(classLoader)
        val handledCasesField = generateSequence(method.declaringClass) {
            it.superclass
        }.flatMap { it.declaredFields.asSequence() }
            .first { MutableMap::class.java.isAssignableFrom(it.type) }
            .apply { isAccessible = true }
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                @Suppress("UNCHECKED_CAST")
                val handledCases = handledCasesField.get(param.thisObject) as?
                    MutableMap<String, Any?> ?: return
                handledCases["SHEALTH#RestrictedChinaCaseHandler"] = true
            }
        })
    }

    private fun Context.getAccountRestrictionHookConfig(): SamsungHealthHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val resultClass = bridge.findClass {
                matcher {
                    usingStrings(
                        "ResultForAllowedAccount(isSuccessForChecking=",
                        ", isAllowedAccount=",
                        ", isRemovingServerDataNeeded="
                    )
                }
            }.singleOrNull() ?: return null
            val restrictedChinaHandlerMethod = bridge.findMethod {
                matcher {
                    returnType = "void"
                    paramCount = 0
                    usingStrings(
                        "SHEALTH#RestrictedChinaCaseHandler",
                        "RESTRICTED_CHINA_CASE_DIALOG",
                        "RestrictedChinaCaseHandler: already handled"
                    )
                }
            }.singleOrNull() ?: return null
            return SamsungHealthHookConfig(
                versionCode = longVersionCode,
                allowedAccountResultClass = resultClass.name,
                restrictedChinaHandlerMethod =
                    restrictedChinaHandlerMethod.toDexMethod().serialize()
            )
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
