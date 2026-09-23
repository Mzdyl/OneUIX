package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.xlog
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

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init(preference: Preference.SamsungHealth) {
        if (param.packageName != Package.SAMSUNG_HEALTH) return
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

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookAccountCountryCheck(context: Context, classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(ACCOUNT_OPERATION_CLASS)
            setOf("isValidSync", "isLegalCountry", "isSyncLegal").forEach { methodName ->
                clazz.declaredMethods.filter { it.name == methodName }.forEach { method ->
                    xposedModule.hook(method).intercept { true }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
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
                    xposedModule.hook(method).intercept { chain ->
                        val result = chain.proceed() as? String
                        if (result.equals("CN", ignoreCase = true)) {
                            GLOBAL_ACCOUNT_COUNTRY
                        } else {
                            result
                        }
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            clearBlockedAccountState(context)
            val config = context.getHookConfig(
                File(context.filesDir, "SamsungHealthAllowedAccountHookConfig.json")
            ) {
                getAllowedAccountHookConfig()
            } ?: error("Allowed-account result class not found")
            val clazz = classLoader.loadClass(config.allowedAccountResultClass)
            clazz.declaredConstructors.forEach { constructor ->
                xposedModule.hook(constructor).intercept { chain ->
                    if (chain.args.size == 3 && chain.args[0] is Boolean &&
                        chain.args[1] is Boolean && chain.args[2] is Boolean
                    ) {
                        val newArgs = chain.args.toTypedArray()
                        newArgs[0] = true
                        newArgs[1] = true
                        newArgs[2] = false
                        chain.proceed(newArgs)
                    } else {
                        chain.proceed()
                    }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            context.getHookConfig(
                File(context.filesDir, "SamsungHealthRestrictedChinaDialogHookConfig.json")
            ) {
                getRestrictedChinaDialogHookConfig()
            }?.restrictedChinaHandlerMethod?.let {
                hookRestrictedChinaDialog(classLoader, it)
            }
        } catch (t: Throwable) {
            xlog(t)
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
    private data class AllowedAccountHookConfig(
        override val versionCode: Long,
        val allowedAccountResultClass: String,
    ) : HookConfig

    @Serializable
    private data class RestrictedChinaDialogHookConfig(
        override val versionCode: Long,
        val restrictedChinaHandlerMethod: String?,
    ) : HookConfig

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookRestrictedChinaDialog(
        classLoader: ClassLoader,
        methodData: String,
    ) {
        try {
            val method = DexMethod(methodData).getMethodInstance(classLoader)
            val handledCasesField = generateSequence(method.declaringClass) {
                it.superclass
            }.flatMap { it.declaredFields.asSequence() }
                .first { MutableMap::class.java.isAssignableFrom(it.type) }
                .apply { isAccessible = true }
            xposedModule.hook(method).intercept { chain ->
                @Suppress("UNCHECKED_CAST")
                val handledCases = handledCasesField.get(chain.thisObject) as?
                    MutableMap<String, Any?>
                handledCases?.put("SHEALTH#RestrictedChinaCaseHandler", true)
                chain.proceed()
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    private fun Context.getAllowedAccountHookConfig(): AllowedAccountHookConfig? {
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

            return AllowedAccountHookConfig(
                versionCode = longVersionCode,
                allowedAccountResultClass = resultClass.name
            )
        }
    }

    private fun Context.getRestrictedChinaDialogHookConfig(): RestrictedChinaDialogHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val method = bridge.findMethod {
                matcher {
                    returnType = "void"
                    paramCount = 0
                    usingStrings(
                        "SHEALTH#RestrictedChinaCaseHandler",
                        "RESTRICTED_CHINA_CASE_DIALOG",
                        "RestrictedChinaCaseHandler: already handled"
                    )
                }
            }.singleOrNull()

            return RestrictedChinaDialogHookConfig(
                versionCode = longVersionCode,
                restrictedChinaHandlerMethod = method?.toDexMethod()?.serialize()
            )
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
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
                "getStgHealthServerEndPoint" to "https://shealth-api.samsunghealth.com",
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
                    .forEach { method ->
                        xposedModule.hook(method).intercept { endpoint }
                    }
            }
            hookSamsungAccountServer(classLoader, serverRegion)
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
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
                    xposedModule.hook(method).intercept { chain ->
                        if (serverRegion == SERVER_REGION_CHINA) {
                            "https://account.samsung.cn"
                        } else {
                            val country = (chain.args[0] as? String).orEmpty()
                                .uppercase(Locale.US)
                            if (country == "US") {
                                "https://us.account.samsung.com"
                            } else {
                                "https://account.samsung.com"
                            }
                        }
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookCountryFeatures(classLoader: ClassLoader) {
        try {
            val cscFeatureClass = classLoader.loadClass(CSC_FEATURE_CLASS)
            cscFeatureClass.declaredMethods.filter { it.name == "isAllowed" }.forEach { method ->
                xposedModule.hook(method).intercept { true }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            val conditionClass = classLoader.loadClass(COUNTRY_CODE_CONDITION_CLASS)
            conditionClass.declaredMethods
                .filter {
                    it.returnType == Boolean::class.javaPrimitiveType &&
                        it.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
                .forEach { method ->
                    xposedModule.hook(method).intercept { true }
                }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            val responseClass = classLoader.loadClass(KIDS_INIT_STATUS_RESPONSE_CLASS)
            mapOf(
                "getIsBlocked" to false,
                "getIsCountryMatched" to true,
                "getIsSupportedCountry" to true
            ).forEach { (methodName, result) ->
                responseClass.declaredMethods.filter { it.name == methodName }.forEach { method ->
                    xposedModule.hook(method).intercept { result }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
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
                .forEach { method ->
                    xposedModule.hook(method).intercept { true }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
