package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.logError
import io.github.soclear.oneuix.hook.util.longVersionCode
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod

object QuickShare {
    fun enableGoogleQuickShare(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SHARE_LIVE) {
            return
        }

        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttach
            try {
                val nearbyMethod =
                    DexMethod(hookConfig.nearbyShareSupportedMethod).getMethodInstance(classLoader)
                XposedBridge.hookMethod(nearbyMethod, XC_MethodReplacement.returnConstant(true))

                val moseyMethod =
                    DexMethod(hookConfig.isMoseySupportedMethod).getMethodInstance(classLoader)
                XposedBridge.hookMethod(moseyMethod, XC_MethodReplacement.returnConstant(true))
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

            return QuickShareHookConfig(
                versionCode = longVersionCode,
                nearbyShareSupportedMethod = nearbyShareSupportedMethod.toDexMethod().serialize(),
                isMoseySupportedMethod = isMoseySupportedMethod.toDexMethod().serialize(),
            )
        }
    }
}
