package io.github.soclear.oneuix.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.log

object SPen {
    /**
     * S Pen 翻译源切换
     * 原理：
     * 1. Hook Validation.isChinaModel 返回 false（伪装为非中国机型）
     * 2. Hook Validation.getCountryCode 返回 "CN"（保持中国区特性）
     * 3. Hook SemCscFeature.getString 返回相应的翻译源 CSC 特性值
     */
    fun switchTranslateSource(loadPackageParam: LoadPackageParam, useGoogle: Boolean) {
        if (loadPackageParam.packageName != Package.SPEN) return

        val classLoader = loadPackageParam.classLoader

        // Hook Validation 类
        try {
            val validationClass = findClass(
                "com.samsung.sdk.clickstreamanalytics.internal.policy.Validation",
                classLoader
            )

            // Hook isChinaModel - 返回 false（伪装为非中国机型）
            findAndHookMethod(
                validationClass,
                "isChinaModel",
                returnConstant(false)
            )
            log("SPen: Hooked Validation.isChinaModel -> false")

            // Hook getCountryCode - 返回 "CN"
            findAndHookMethod(
                validationClass,
                "getCountryCode",
                String::class.java,
                returnConstant("CN")
            )
            log("SPen: Hooked Validation.getCountryCode -> CN")
        } catch (t: Throwable) {
            log("SPen: Failed to hook Validation - ${t.message}")
        }

        // Hook SemCscFeature.getString
        try {
            val cscFeatureClass = findClass(
                "com.samsung.android.feature.SemCscFeature",
                classLoader
            )

            // Hook getString(String) - 单参数版本
            findAndHookMethod(
                cscFeatureClass,
                "getString",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        param.result = getTranslateSourceValue(key, useGoogle)
                    }
                }
            )

            // Hook getString(String, String) - 双参数版本
            findAndHookMethod(
                cscFeatureClass,
                "getString",
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        param.result = getTranslateSourceValue(key, useGoogle)
                    }
                }
            )

            log("SPen: Hooked SemCscFeature.getString -> ${if (useGoogle) "Google" else "Baidu"}")
        } catch (t: Throwable) {
            log("SPen: Failed to hook SemCscFeature - ${t.message}")
        }
    }

    /**
     * 根据配置返回翻译源 CSC 特性值
     */
    private fun getTranslateSourceValue(key: String, useGoogle: Boolean): String? {
        // 翻译源相关的 CSC 特性键
        return when {
            key.contains("translate", ignoreCase = true) ||
            key.contains("trans", ignoreCase = true) ||
            key == "DefaultCscFeature_Spen_Translation" ||
            key == "CscFeature_Spen_Translation" -> {
                if (useGoogle) "GOOGLE" else "BAIDU"
            }
            else -> null // 不处理其他键
        }
    }
}