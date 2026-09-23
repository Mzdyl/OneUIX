package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.xlog
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

object PhotoRetouching {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun noAIWatermark() {
        if (param.packageName != Package.PHOTO_RETOUCHING) return
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() }
            if (hookConfig != null) {
                try {
                    val methodInstance =
                        DexMethod(hookConfig.saveWatermarkMethod).getMethodInstance(classLoader)
                    xposedModule.hook(methodInstance).intercept { null }
                } catch (t: Throwable) {
                    xlog(t)
                }
            }
        }
    }

    /**
     * 启用涂鸦生图功能
     * 原理：
     * 1. 移除 Build.PRODUCT 中的中国区后缀 (zc, zm, zcx, zcw, ctc)
     * 2. Hook SemSystemProperties.get 返回港版区域码 (TGY)
     * 3. Hook SemSystemProperties.getCountryIso 返回 HK
     */
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun enableSketch() {
        if (param.packageName != Package.PHOTO_RETOUCHING) return

        val classLoader = param.classLoader

        // ============== Hook 1: SemSystemProperties.get(String) ==============
        try {
            val semSystemPropertiesClass = classLoader.loadClass("android.os.SemSystemProperties")
            semSystemPropertiesClass.declaredMethods
                .filter { it.name == "get" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val key = chain.args.getOrNull(0) as? String
                        when (key) {
                            "ro.csc.countryiso_code" -> "HK"
                            "ro.csc.sales_code" -> "TGY"
                            "ro.csc.country_code" -> "Hong Kong"
                            else -> chain.proceed()
                        }
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }

        // ============== Hook 2: SemSystemProperties.getCountryIso() ==============
        try {
            val semSystemPropertiesClass = classLoader.loadClass("android.os.SemSystemProperties")
            semSystemPropertiesClass.declaredMethods
                .filter { it.name == "getCountryIso" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { "HK" }
                }
        } catch (t: Throwable) {
            xlog(t)
        }

        // ============== Hook 3: 移除 Build.PRODUCT 后缀 ==============
        try {
            val buildClass = classLoader.loadClass("android.os.Build")
            val productField = buildClass.getDeclaredField("PRODUCT").apply { isAccessible = true }
            val product = productField.get(null) as? String
            if (product != null) {
                val suffixes = listOf("zc", "zm", "zcx", "zcw", "ctcx")
                var modifiedProduct = product
                for (suffix in suffixes) {
                    if (product.endsWith(suffix, ignoreCase = true)) {
                        modifiedProduct = product.substring(0, product.length - suffix.length)
                        break
                    }
                }
                if (modifiedProduct != product) {
                    productField.set(null, modifiedProduct)
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
        
    @Serializable
    private data class PhotoRetouchingHookConfig(
        override val versionCode: Long,
        val saveWatermarkMethod: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): PhotoRetouchingHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val saveWatermarkMethodUsingStrings = listOf(
                "SPE_CommonUtil",
                "getWatermarkBitmap : requiredSize = ",
                "saveWatermark : canvas shortAxis = ",
            )
            val saveWatermarkMethod = bridge.findClass {
                excludePackages(
                    "android",
                    "androidx",
                    "appfunctions_aggregated_deps",
                    "co",
                    "com",
                    "io",
                    "kotlin",
                    "org"
                )
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    usingStrings = saveWatermarkMethodUsingStrings
                }
            }.findMethod {
                matcher {
                    usingStrings = saveWatermarkMethodUsingStrings
                }
            }.singleOrNull() ?: return null

            return PhotoRetouchingHookConfig(
                versionCode = longVersionCode,
                saveWatermarkMethod = saveWatermarkMethod.toDexMethod().serialize(),
            )
        }
    }
}
