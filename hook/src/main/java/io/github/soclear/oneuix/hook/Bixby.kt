package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method
import java.util.Locale

object Bixby {
    context(xposedModule: XposedModule)
    fun customWakeup(support: Boolean, bypassRestrictions: Boolean) =
        afterAttachTry(support || bypassRestrictions) {
            val config = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttachTry
            if (bypassRestrictions) {
                config.validationMethods.forEach {
                    val method = DexMethod(it).getMethodInstance(classLoader)
                    xposedModule.hook(method).intercept {
                        if (method.returnType == Boolean::class.javaPrimitiveType) true else 0
                    }
                }
            }
            if (!support) return@afterAttachTry

            config.supportMethods.forEach {
                xposedModule.hook(DexMethod(it).getMethodInstance(classLoader)).intercept { chain ->
                    if (chain.args.isEmpty() || chain.args[0] == "labs_custom_wakeup") true else chain.proceed()
                }
            }
            // 保留中文录音的语言，避免被保存为 en-US 后从设置中消失。
            config.supportedLocalesMethod?.let {
                val method = DexMethod(it).getMethodInstance(classLoader)
                val keywordFields = method.parameterTypes[0].declaredFields
                    .filter { field -> field.type == String::class.java }
                    .onEach { field -> field.isAccessible = true }
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val keyword = chain.args[0]
                    val locales = result as? List<*> ?: return@intercept result
                    if (keyword != null && keywordFields.any { field -> field.get(keyword) == "custom" } &&
                        Locale.CHINA !in locales
                    ) locales + Locale.CHINA else result
                }
            }
            config.getAssetsMethod?.let {
                fixCustomResourceDeployment(DexMethod(it).getMethodInstance(classLoader))
            }
        }

    @Serializable
    private data class BixbyHookConfig(
        override val versionCode: Long,
        val supportMethods: List<String>,
        val validationMethods: List<String>,
        val supportedLocalesMethod: String?,
        val getAssetsMethod: String?,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): BixbyHookConfig {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val supportMethods = bridge.findMethod {
                matcher {
                    declaredClass = "com.samsung.android.bixby.agent.common.util.datamanager.LabsFeatureManager"
                    returnType = "boolean"
                    paramCount(0..1)
                }
            }.filter {
                it.name == "isLabsMenuSupported" && it.paramCount == 0 ||
                    it.name in listOf("isSupported", "isAvailable", "isEnabled", "isLabs") &&
                    it.paramTypeNames == listOf("java.lang.String")
            }
            val validationMethods = if (packageName == Package.BIXBY_AGENT) {
                bridge.findMethod {
                    matcher {
                        declaredClass = "com.samsung.voicewakeup.wwv.WakeupWordValidator"
                        paramCount(4..5)
                    }
                }.filter {
                    val types = it.paramTypeNames
                    it.returnTypeName == "boolean" &&
                        types.count { type -> type == "java.util.Locale" } == 1 &&
                        types.all { type -> type == "java.lang.String" || type == "java.util.Locale" } ||
                        it.returnTypeName == "int" && types == listOf(
                            "android.content.Context", "java.lang.String", "java.util.Locale", "java.lang.String"
                        )
                }
            } else {
                bridge.findMethod {
                    searchPackages("com.samsung.voicewakeup.kwv")
                    matcher {
                        returnType = "boolean"
                        paramTypes(String::class.java, Locale::class.java)
                        usingStrings("WordTypeChecker locale: ")
                    }
                }
            }
            val supportedLocales = if (packageName == Package.BIXBY_WAKEUP) bridge.findMethod {
                matcher {
                    returnType = "java.util.List"
                    paramCount = 1
                    usingStrings("getCustomWakeupSupportedLocaleList")
                }
            }.singleOrNull() else null
            val getAssets = if (packageName == Package.BIXBY_WAKEUP) bridge.findMethod {
                matcher {
                    declaredClass = "com.samsung.android.phoebus.assets.AssetUtils"
                    name = "get"
                    paramTypes("java.lang.String", "com.samsung.android.phoebus.assets.AssetRequestHeader", "java.util.List")
                }
            }.singleOrNull() else null
            return BixbyHookConfig(
                longVersionCode,
                supportMethods.map { it.toDexMethod().serialize() },
                validationMethods.map { it.toDexMethod().serialize() },
                supportedLocales?.toDexMethod()?.serialize(),
                getAssets?.toDexMethod()?.serialize(),
            )
        }
    }

    @SuppressLint("PrivateApi")
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun unlockOfflineProcessing() {
        runCatching {
            val method = param.classLoader.loadClass("android.app.SharedPreferencesImpl")
                .getDeclaredMethod("getString", String::class.java, String::class.java)
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (chain.args[0] != "pref_key_on_device_config_cache" || Build.MODEL.isBlank()) {
                    result
                } else {
                    val cache = (result ?: chain.args[1]) as? String ?: ""
                    if (Build.MODEL in cache.split(',')) cache
                    else if (cache.isBlank()) Build.MODEL else "$cache,${Build.MODEL}"
                }
            }
        }.onFailure { xlog(it) }
    }

    // 中文自定义资源未部署时借用 S25 的部署，固件和模型校验仍使用实际机型。
    context(xposedModule: XposedModule)
    private fun fixCustomResourceDeployment(method: Method) {
        val getMap = method.parameterTypes[1].getDeclaredMethod("getMap")
        xposedModule.hook(method).intercept { chain ->
            val original = chain.proceed()
            try {
                val error = original?.reflect?.call("getError") ?: return@intercept original
                if (error.reflect.call("getCode") != 400 ||
                    (error.reflect.call("getMessage") as? String)?.startsWith("No deployment set") != true
                ) return@intercept original

                val header = chain.args[1] ?: return@intercept original
                @Suppress("UNCHECKED_CAST")
                val headers = getMap.invoke(header) as? MutableMap<String, String>
                    ?: return@intercept original
                val model = headers["device-model"] ?: return@intercept original
                if (headers["client-type"] != "bixby.wakeup" ||
                    !headers["locale"].equals("zh-CN", ignoreCase = true) || model == "SM-S9380"
                ) return@intercept original

                val requests = chain.args[2] as? List<*> ?: return@intercept original
                if (requests.isEmpty() || !requests.all { item ->
                    if (item == null) return@all false
                    val types = item.reflect["resourceType"] as? List<*>
                    item.reflect.call("getResourceModule") in listOf("keyword", "sv") &&
                        !types.isNullOrEmpty() && types.all {
                            it in listOf("custom_normal", "custom_bargein", "custom_acousticecho")
                        }
                }) return@intercept original

                try {
                    headers["device-model"] = "SM-S9380"
                    val retried = chain.proceed()
                    val data = retried?.reflect?.call("getData")
                    val artifacts = data?.reflect?.call("getArtifacts") as? List<*>
                    if (!artifacts.isNullOrEmpty()) retried else original
                } finally {
                    headers["device-model"] = model
                }
            } catch (t: Throwable) {
                xlog(t)
                original
            }
        }
    }
}
