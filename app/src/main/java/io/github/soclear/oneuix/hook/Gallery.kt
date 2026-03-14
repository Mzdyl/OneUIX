package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.logError

object Gallery {
    fun supportAllSettings(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.GALLERY) return
        val featureClass = findClass(
            "com.samsung.android.gallery.support.utils.Features",
            loadPackageParam.classLoader
        )
        // 设置项见反编译后的 SettingSearchIndexablesProvider
        val featureList = listOf(
            // 查看选项 -> 超级 HDR
            "SUPPORT_PHOTO_HDR",
            // 故事 -> 自动创建故事
            "SUPPORT_AUTO_CREATE_STORY",
            // 识别图片中的内容
            "SUPPORT_CMH_PROVIDER_PERMISSION",
            // 回收站
            "SUPPORT_TRASH",
            // 分享时转换 HEIF 图片
            "SUPPORT_HEIF_CONVERSION",
            // 分享时转换 HDR10+ 视频
            "SUPPORT_HDR10PLUS_CONVERSION",
            // 音频橡皮擦
            "SUPPORT_AUDIO_ERASER",
        )
        val returnTrue = returnConstant(true)

        for (feature in featureList) {
            val featureInstance = try {
                getStaticObjectField(featureClass, feature)
            } catch (_: NoSuchFieldError) {
                continue
            }
            try {
                findAndHookMethod(featureInstance.javaClass, "getEnabling", returnTrue)
            } catch (t: Throwable) {
                logError("supportAllSettings hook failed for $feature", t)
            }
        }

        // 各设置项见 com.samsung.android.gallery.settings.ui.SettingFragment 的 initPreference

        try {
            val settingPreferenceClass = findClass(
                "com.samsung.android.gallery.module.settings.SettingPreference",
                loadPackageParam.classLoader
            )
            val trashClass = getStaticObjectField(settingPreferenceClass, "Trash").javaClass
            findAndHookMethod(trashClass, "support", Context::class.java, returnTrue)
            val photoHdr = getStaticObjectField(settingPreferenceClass, "PhotoHdr").javaClass
            findAndHookMethod(photoHdr, "support", Context::class.java, returnTrue)
        } catch (_: Throwable) {
        }
    }
}
