package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.quickshare.QuickShareConfig
import io.github.soclear.oneuix.hook.quickshare.QuickShareConfigCache
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.xlog
import java.io.File
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod

object QuickShare {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun enableGoogleQuickShare() {
        if (param.packageName != Package.SHARE_LIVE) {
            return
        }

        afterAttachTry {
            val hookConfig = QuickShareConfigCache.load(
                File(filesDir, "OneUIXQuickShare.json"), longVersionCode
            ) { getHookConfigFromDexKit() }

            fun install(methodName: String?, hooker: XposedInterface.Hooker) {
                if (methodName == null) return
                runCatching {
                    val method = DexMethod(methodName).getMethodInstance(classLoader)
                    xposedModule.hook(method).intercept(hooker)
                }.onFailure { xlog(it) }
            }

            install(hookConfig.nearbyShareSupportedMethod) { true }
            install(hookConfig.isMoseySupportedMethod) { true }
            install(hookConfig.sepGlobalCheckMethod) { chain ->
                val arg0 = chain.args.getOrNull(0) as? String
                val arg1 = chain.args.getOrNull(1) as? String
                if ((arg0 == "sepChinaPublic" && arg1 == "sepGlobal") ||
                    (arg0 == "sepGlobal" && arg1 == "sepChinaPublic")
                ) true else chain.proceed()
            }
            install(hookConfig.temporaryModeMethod) { false }
        }
    }

    private fun Context.getHookConfigFromDexKit(): QuickShareConfig {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val nearbyShareSupportedMethod = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "boolean"
                    usingStrings("nearbyShareSupported=", "CapabilitySourceImpl")
                }
            }.singleOrNull()

            val isMoseySupportedMethod = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "boolean"
                    usingStrings("isMoseySupported : ", "CapabilitySourceImpl")
                }
            }.singleOrNull()

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

            return QuickShareConfig(
                versionCode = longVersionCode,
                nearbyShareSupportedMethod = nearbyShareSupportedMethod?.toDexMethod()?.serialize(),
                isMoseySupportedMethod = isMoseySupportedMethod?.toDexMethod()?.serialize(),
                sepGlobalCheckMethod = sepGlobalCheckMethod?.toDexMethod()?.serialize(),
                temporaryModeMethod = temporaryModeMethod?.toDexMethod()?.serialize(),
            )
        }
    }
}
