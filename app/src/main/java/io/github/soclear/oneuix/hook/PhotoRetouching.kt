package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.log
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

object PhotoRetouching {
    fun noAIWatermark() = afterAttach {
        val hookConfig = getHookConfig { getHookConfigFromDexKit() }
        if (hookConfig != null) {
            val methodInstance =
                DexMethod(hookConfig.saveWatermarkMethod).getMethodInstance(classLoader)
            XposedBridge.hookMethod(methodInstance, XC_MethodReplacement.DO_NOTHING)
        }
    }

    /**
     * 启用涂鸦生图功能
     * 原理：
     * 1. 移除 Build.PRODUCT 中的中国区后缀 (zc, zm, zcx, zcw, ctc)
     * 2. Hook SemFloatingFeature.getString 返回港版区域码 (TGY)
     * 3. Hook SemFloatingFeature.isFeatureEnabled 返回 true
     */
    fun enableSketch(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.PHOTO_RETOUCHING) return

        // 移除中国区设备型号后缀
        removeChinaRegionSuffix()

        val classLoader = loadPackageParam.classLoader

        // Hook SemFloatingFeature.getString 返回港版区域码
        try {
            val semFloatingFeatureClass = findClass(
                "com.samsung.android.feature.SemFloatingFeature",
                classLoader
            )
            findAndHookMethod(
                semFloatingFeatureClass,
                "getString",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        // 返回港版区域码
                        if (key.contains("ContextualSearch", ignoreCase = true) ||
                            key.contains("SketchToImage", ignoreCase = true) ||
                            key.contains("AiDrawing", ignoreCase = true) ||
                            key == "SEC_FLOATING_FEATURE_CONTEXT_CONFIG_COUNTRY_CODE"
                        ) {
                            param.result = "TGY" // 港版
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            log("enableSketch: Failed to hook SemFloatingFeature.getString - ${t.message}")
        }

        // Hook SemFloatingFeature.isFeatureEnabled 返回 true
        try {
            val semFloatingFeatureClass = findClass(
                "com.samsung.android.feature.SemFloatingFeature",
                classLoader
            )
            findAndHookMethod(
                semFloatingFeatureClass,
                "isFeatureEnabled",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        // 启用涂鸦生图相关功能
                        if (key.contains("SketchToImage", ignoreCase = true) ||
                            key.contains("AiDrawing", ignoreCase = true) ||
                            key.contains("ContextualSearch", ignoreCase = true) ||
                            key.contains("DrawingAssist", ignoreCase = true)
                        ) {
                            param.result = true
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            log("enableSketch: Failed to hook SemFloatingFeature.isFeatureEnabled - ${t.message}")
        }
    }

    /**
     * 移除 Build.PRODUCT 中的中国区后缀
     * 中国区后缀: zc, zm, zcx, zcw, ctc
     */
    private fun removeChinaRegionSuffix() {
        try {
            val buildClass = findClass("android.os.Build", null)
            val productField = buildClass.getDeclaredField("PRODUCT")
            productField.isAccessible = true
            val product = productField.get(null) as? String ?: return

            // 需要移除的中国区后缀
            val suffixes = listOf("zc", "zm", "zcx", "zcw", "ctc")
            var modifiedProduct = product

            for (suffix in suffixes) {
                if (product.endsWith(suffix, ignoreCase = true)) {
                    modifiedProduct = product.substring(0, product.length - suffix.length)
                    break
                }
            }

            if (modifiedProduct != product) {
                productField.set(null, modifiedProduct)
                log("removeChinaRegionSuffix: $product -> $modifiedProduct")
            }
        } catch (t: Throwable) {
            log("removeChinaRegionSuffix failed: ${t.message}")
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
                "saveWatermark : text height = ",
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
                versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode,
                saveWatermarkMethod = saveWatermarkMethod.toDexMethod().serialize(),
            )
        }
    }
}
