package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.log
import io.github.soclear.oneuix.hook.util.logError
import io.github.soclear.oneuix.hook.util.longVersionCode
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File

object QuickShare {
    fun enableGoogleQuickShare(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SHARE_LIVE) {
            return
        }

        afterAttach {
            val configFile = File(filesDir, "HookConfig.json")
            val hookConfig = getHookConfig(configFile) { getHookConfigFromDexKit() }?.let {
                if (it.sepGlobalCheckMethod == null) {
                    configFile.delete()
                    getHookConfig(configFile) { getHookConfigFromDexKit() }
                } else it
            } ?: return@afterAttach

            try {
                val nearbyMethod =
                    DexMethod(hookConfig.nearbyShareSupportedMethod).getMethodInstance(classLoader)
                XposedBridge.hookMethod(nearbyMethod, XC_MethodReplacement.returnConstant(true))

                val moseyMethod =
                    DexMethod(hookConfig.isMoseySupportedMethod).getMethodInstance(classLoader)
                XposedBridge.hookMethod(moseyMethod, XC_MethodReplacement.returnConstant(true))

                hookConfig.sepGlobalCheckMethod?.let { methodStr ->
                    val sepGlobalMethod = DexMethod(methodStr).getMethodInstance(classLoader)
                    XposedBridge.hookMethod(sepGlobalMethod, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val arg0 = param.args.getOrNull(0) as? String
                            val arg1 = param.args.getOrNull(1) as? String
                            if ((arg0 == "sepChinaPublic" && arg1 == "sepGlobal") ||
                                (arg0 == "sepGlobal" && arg1 == "sepChinaPublic")
                            ) {
                                param.result = true
                            }
                        }
                    })
                }

                hookConfig.temporaryModeMethod?.let { methodStr ->
                    val tempMethod = DexMethod(methodStr).getMethodInstance(classLoader)
                    XposedBridge.hookMethod(tempMethod, XC_MethodReplacement.returnConstant(false))
                }

                log("enableGoogleQuickShare hooks applied successfully (sepGlobal=${hookConfig.sepGlobalCheckMethod != null}, tempMode=${hookConfig.temporaryModeMethod != null})")
            } catch (t: Throwable) {
                logError("enableGoogleQuickShare failed", t)
            }
        }
    }

    @Serializable
    private data class QuickShareHookConfig(
        override val versionCode: Long,
        val nearbyShareSupportedMethod: String,
        val isMoseySupportedMethod: String,
        val sepGlobalCheckMethod: String? = null,
        val temporaryModeMethod: String? = null,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): QuickShareHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val nearbyShareSupportedMethod = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "boolean"
                    usingStrings("nearbyShareSupported=", "CapabilitySourceImpl")
                }
            }.singleOrNull() ?: return null

            val isMoseySupportedMethod = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "boolean"
                    usingStrings("isMoseySupported : ", "CapabilitySourceImpl")
                }
            }.singleOrNull() ?: return null

            val sepGlobalCheckMethod = bridge.findMethod {
                matcher {
                    paramTypes("java.lang.String", "java.lang.String", "boolean")
                    returnType = "boolean"
                    addCaller {
                        usingStrings("sepChinaPublic", "sepGlobal")
                    }
                }
            }.firstOrNull()

            val temporaryModeMethod = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "boolean"
                    addCaller {
                        usingStrings("temporaryModeEnabled true, skip updateNearbyShareVisibility")
                    }
                }
            }.firstOrNull {
                val cls = it.toDexMethod().className
                cls != "pf.f" && !cls.contains("CapabilitySource")
            }

            return QuickShareHookConfig(
                versionCode = longVersionCode,
                nearbyShareSupportedMethod = nearbyShareSupportedMethod.toDexMethod().serialize(),
                isMoseySupportedMethod = isMoseySupportedMethod.toDexMethod().serialize(),
                sepGlobalCheckMethod = sepGlobalCheckMethod?.toDexMethod()?.serialize(),
                temporaryModeMethod = temporaryModeMethod?.toDexMethod()?.serialize(),
            )
        }
    }
}
