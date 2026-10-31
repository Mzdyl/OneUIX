package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

/**
 * 三星「智能建议」com.samsung.android.smartsuggestions
 *
 * 所有开关都在 com.samsung.android.smartsuggestions.featureconfig.rune.Rune。
 * 本类按设置里的开关逐项放行，互不牵连。
 */
object SmartSuggestions {

    private const val RUNE_CLASS =
        "com.samsung.android.smartsuggestions.featureconfig.rune.Rune"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun applyHooks(
        chatTranslation: Boolean,
        globalCards: Boolean,
        healthCards: Boolean,
        nowNudge: Boolean,
    ) {
        if (param.packageName != Package.SMART_SUGGESTIONS) return
        val rune = try {
            param.classLoader.loadClass(RUNE_CLASS)
        } catch (t: Throwable) {
            xlog(t); return
        }

        val forceTrue = buildList {
            if (chatTranslation) add("getSUPPORT_CHAT_TRANSLATION")
            if (nowNudge) add("getSUPPORT_NOW_NUDGE")
            if (healthCards) {
                // rune 里硬编码 return false 的未发布卡片，打开判定试一把
                add("getSUPPORT_AI_SUGGESTION_HEALTH_NEW_DOCUMENT")
                add("getSUPPORT_AI_SUGGESTION_EXERCISE_GREETING")
                add("getSUPPORT_AI_SUGGESTION_ACTIVITY_FORECAST")
            }
        }
        forceTrue.forEach { name ->
            try {
                xposedModule.hook(rune.getDeclaredMethod(name)).intercept { true }
            } catch (t: Throwable) {
                xlog(t)
            }
        }

        // 国际卡组：Gemini 行程 / YouTube 新卡 / nano-banana / KimGiSa …
        // 代价：高德导航卡、美颜创意自拍卡为 `xxx && isChineseModel()`，会一起失效
        if (globalCards) {
            try {
                xposedModule.hook(rune.getDeclaredMethod("isChineseModel")).intercept { false }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }
}
