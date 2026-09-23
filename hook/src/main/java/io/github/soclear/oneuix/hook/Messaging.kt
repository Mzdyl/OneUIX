package io.github.soclear.oneuix.hook

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.reflect

object Messaging {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun isSupportBlock() {
        if (param.packageName != Package.MESSAGING) return
        val featureClass = runCatching {
            param.classLoader.loadClass("com.samsung.android.messaging.common.configuration.Feature")
        }.getOrNull() ?: return
        listOf(
            "isSupportBlockNumber",
            "isSupportBlockPhrase",
            "isBlockNumberSettingEnable",
            "isSupportPhishingReport",
            "enableAlwaysSendSpamReport",
            "getEnableBotSpamReport",
            "getEnableSpamReport4Kor",
            "isSupportMaliciousMessageDetection",
            "isSupportMaliciousMessageDetectionAndSpamBlocker",
            "isSupportBlockSpamByAi",
            "isSupportMcsAiSpamMessage",
            "isSupportMcsSpamOrMaliciousMessage",
            "isSupportSuggestAiSpamFilter",
            "isSupportSuggestMaliciousSpamFilter",
            "isSupportMcs",
        ).forEach { methodName ->
            try {
                featureClass.declaredMethods.filter { it.name == methodName }.forEach {
                    xposedModule.hook(it).intercept { true }
                }
            } catch (_: Throwable) {
            }
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun preventCmcRestart() {
        if (param.packageName != Package.MESSAGING) return
        val classLoader = param.classLoader

        val cmcFeatureLoadUtilsClass = runCatching {
            classLoader.loadClass("com.samsung.android.messaging.common.configuration.cmc.CmcFeatureLoadUtils")
        }.getOrNull()
        if (cmcFeatureLoadUtilsClass != null) {
            cmcFeatureLoadUtilsClass.declaredMethods.filter { it.name == "compareFeatures" }.forEach {
                xposedModule.hook(it).intercept { true }
            }
            cmcFeatureLoadUtilsClass.declaredMethods.filter { it.name == "killOrRestartMessageApp" }.forEach {
                xposedModule.hook(it).intercept { null }
            }
            cmcFeatureLoadUtilsClass.declaredMethods.filter { it.name == "loadFeatures" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val context = chain.args.firstOrNull() as? Context
                    if (context != null) {
                        val cmcFeatureClass = runCatching {
                            classLoader.loadClass("com.samsung.android.messaging.common.configuration.cmc.CmcFeature")
                        }.getOrNull()
                        if (cmcFeatureClass != null) {
                            val getCacheMethod = cmcFeatureClass.getDeclaredMethod("getFeaturesCache")
                            val cache = getCacheMethod.invoke(null)
                            if (cache == null) {
                                val loadCacheMethod = cmcFeatureLoadUtilsClass.getDeclaredMethod("loadFeaturesCache", Context::class.java)
                                loadCacheMethod.invoke(null, context)
                            }
                        }
                    }
                    chain.proceed()
                }
            }
        }

        val cmcFeatureClass = runCatching {
            classLoader.loadClass("com.samsung.android.messaging.common.configuration.cmc.CmcFeature")
        }.getOrNull()
        if (cmcFeatureClass != null) {
            cmcFeatureClass.declaredMethods.filter { it.name == "needToKillAndRestartMsgApp" }.forEach {
                xposedModule.hook(it).intercept { false }
            }
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun showCmcMessageIndicator() {
        if (param.packageName != Package.MESSAGING) return
        val classLoader = param.classLoader

        val cmcOpenUtilsClass = runCatching {
            classLoader.loadClass("com.samsung.android.messaging.common.cmc.CmcOpenUtils")
        }.getOrNull()
        if (cmcOpenUtilsClass != null) {
            runCatching {
                val isCmcOpenMessageForViewMethod = cmcOpenUtilsClass.getDeclaredMethod("isCmcOpenMessageForView", String::class.java)
                val isCmcOpenMessageMethod = cmcOpenUtilsClass.getDeclaredMethod("isCmcOpenMessage", String::class.java)
                xposedModule.hook(isCmcOpenMessageForViewMethod).intercept { chain ->
                    val str = chain.args[0] as? String ?: return@intercept false
                    isCmcOpenMessageMethod.invoke(null, str) as? Boolean ?: false
                }
            }

            cmcOpenUtilsClass.declaredMethods.filter { it.name == "isCmcSwitcherSupportState" }.forEach {
                xposedModule.hook(it).intercept { true }
            }
        }

        val bottomViewClass = runCatching {
            classLoader.loadClass("com.samsung.android.messaging.ui.view.bubble.item.BubbleInfoBottomView")
        }.getOrNull()
        val vClass = bottomViewClass?.superclass
        if (vClass != null) {
            vClass.declaredMethods.filter { it.name == "d" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val isCmc = chain.args.getOrNull(1) as? Boolean ?: false
                    if (isCmc) {
                        try {
                            val simSlotImg = chain.thisObject.reflect["s"] as? View
                            simSlotImg?.visibility = View.GONE
                        } catch (_: Throwable) {}
                    }
                    result
                }
            }
        }

        val bubbleListItemClass = runCatching {
            classLoader.loadClass("com.samsung.android.messaging.ui.view.bubble.item.BubbleListItem")
        }.getOrNull()
        if (bubbleListItemClass != null) {
            bubbleListItemClass.declaredMethods.filter { it.name == "o" && it.parameterCount == 0 }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        val item = runCatching { chain.thisObject.reflect.call("getMessagePartsItem") }.getOrNull()
                            ?: runCatching { chain.thisObject.reflect["messagePartsItem"] }.getOrNull()
                            ?: runCatching { chain.thisObject.reflect["w"] }.getOrNull()
                        if (item != null) {
                            var isCmc = false
                            for (f in item.javaClass.declaredFields) {
                                if (f.type == String::class.java) {
                                    f.isAccessible = true
                                    val v = f.get(item) as? String ?: continue
                                    if (v.contains("relayMessage") || v.contains("syncedMessage")) {
                                        isCmc = true
                                        break
                                    }
                                }
                            }
                            if (isCmc) {
                                val container = chain.thisObject as? LinearLayout
                                if (container != null) {
                                    val res = container.resources
                                    val slotId = res.getIdentifier("announcement_list_item_divider_sim_slot", "id", param.packageName)
                                    val devIconId = res.getIdentifier("orc_ic_device_cmc", "drawable", param.packageName)
                                    if (slotId != 0 && devIconId != 0) {
                                        val imageView = container.findViewById<ImageView>(slotId)
                                            ?: (runCatching { chain.thisObject.reflect["l"] as? View }.getOrNull())?.findViewById(slotId)
                                        if (imageView != null) {
                                            imageView.visibility = View.VISIBLE
                                            imageView.setImageResource(devIconId)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                    result
                }
            }
        }
    }
}
