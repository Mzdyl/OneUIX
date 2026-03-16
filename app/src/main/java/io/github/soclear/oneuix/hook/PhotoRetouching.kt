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
import io.github.soclear.oneuix.hook.util.logError
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
     * 4. Hook SemCscFeature 相关方法
     */
    fun enableSketch(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != "com.sec.android.mimage.photoretouching") return
    
        val classLoader = loadPackageParam.classLoader
    
        // ============== Hook 1: SemSystemProperties.get(String) ==============
        try {
            val semSystemPropertiesClass = findClass(
                "android.os.SemSystemProperties",
                classLoader
            )
        
            XposedBridge.hookAllMethods(
                semSystemPropertiesClass,
                "get",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args.getOrNull(0) as? String ?: return
                    
                        // 港版区域码
                        when (key) {
                            "ro.csc.countryiso_code" -> {
                                param.result = "HK"
                                log("SemSystemProperties.get($key) -> HK")
                            }
                            "ro.csc.sales_code" -> {
                                param.result = "TGY"
                                log("SemSystemProperties.get($key) -> TGY")
                            }
                            "ro.csc.country_code" -> {
                                param.result = "Hong Kong"
                                log("SemSystemProperties.get($key) -> Hong Kong")
                            }
                        }
                    }
                }
            )
            log("enableSketch: SemSystemProperties.get hooked")
        } catch (t: Throwable) {
            log("enableSketch: Failed to hook SemSystemProperties.get - ${t.message}")
        }
    
        // ============== Hook 2: SemSystemProperties.getCountryIso() ==============
        try {
            val semSystemPropertiesClass = findClass(
                "android.os.SemSystemProperties",
                classLoader
            )
        
            XposedBridge.hookAllMethods(
                semSystemPropertiesClass,
                "getCountryIso",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = "HK"
                        log("SemSystemProperties.getCountryIso() -> HK")
                    }
                }
            )
            log("enableSketch: SemSystemProperties.getCountryIso hooked")
        } catch (t: Throwable) {
                log("enableSketch: Failed to hook getCountryIso - ${t.message}")
        }
    
        // ============== Hook 3: 移除 Build.PRODUCT 后缀 ==============
        try {
            val buildClass = findClass("android.os.Build", null)
            val productField = buildClass.getDeclaredField("PRODUCT")
            productField.isAccessible = true
            val product = productField.get(null) as? String ?: return
        
            // 需要移除的中国区后缀: zc, zm, zcx, zcw, ctcx
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
                log("enableSketch: Build.PRODUCT $product -> $modifiedProduct")
            }
        } catch (t: Throwable) {
            log("enableSketch: Failed to modify Build.PRODUCT - ${t.message}")
        }
    
        log("enableSketch: All hooks completed")
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
