package io.github.soclear.oneuix.hook

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package

object Messaging {
    fun isSupportBlock(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.MESSAGING) return
        val featureClass = XposedHelpers.findClassIfExists(
            "com.samsung.android.messaging.common.configuration.Feature",
            loadPackageParam.classLoader
        ) ?: return
        val returnTrue = XC_MethodReplacement.returnConstant(true)
        listOf(
            "isSupportBlockNumber",
            "isSupportBlockPhrase",
            "isBlockNumberSettingEnable",
            "isSupportPhishingReport",
            "enableAlwaysSendSpamReport",
            "getEnableBotSpamReport",
            "getEnableSpamReport4Kor",
//            "isSupportAIFeature",
//            "isSupportAISpam",
            "isSupportMaliciousMessageDetection",
            "isSupportMaliciousMessageDetectionAndSpamBlocker",
            "isSupportBlockSpamByAi",
            "isSupportMcsAiSpamMessage",
            "isSupportMcsSpamOrMaliciousMessage",
            "isSupportSuggestAiSpamFilter",
            "isSupportSuggestMaliciousSpamFilter",
            "isSupportMcs",
        ).forEach {
            try {
                XposedBridge.hookAllMethods(featureClass, it, returnTrue)
            } catch (_: Throwable) {
            }
        }
    }

    fun preventCmcRestart(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.MESSAGING) return

        val cmcFeatureLoadUtilsClass = XposedHelpers.findClassIfExists(
            "com.samsung.android.messaging.common.configuration.cmc.CmcFeatureLoadUtils",
            loadPackageParam.classLoader
        )
        if (cmcFeatureLoadUtilsClass != null) {
            try {
                XposedBridge.hookAllMethods(
                    cmcFeatureLoadUtilsClass,
                    "compareFeatures",
                    XC_MethodReplacement.returnConstant(true)
                )
            } catch (_: Throwable) {
            }

            try {
                XposedBridge.hookAllMethods(
                    cmcFeatureLoadUtilsClass,
                    "killOrRestartMessageApp",
                    XC_MethodReplacement.DO_NOTHING
                )
            } catch (_: Throwable) {
            }

            try {
                XposedBridge.hookAllMethods(
                    cmcFeatureLoadUtilsClass,
                    "loadFeatures",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val context = param.args[0] as? Context ?: return
                            val cmcFeatureClass = XposedHelpers.findClassIfExists(
                                "com.samsung.android.messaging.common.configuration.cmc.CmcFeature",
                                loadPackageParam.classLoader
                            ) ?: return
                            val cache = XposedHelpers.callStaticMethod(cmcFeatureClass, "getFeaturesCache")
                            if (cache == null) {
                                XposedHelpers.callStaticMethod(cmcFeatureLoadUtilsClass, "loadFeaturesCache", context)
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }
        }

        val cmcFeatureClass = XposedHelpers.findClassIfExists(
            "com.samsung.android.messaging.common.configuration.cmc.CmcFeature",
            loadPackageParam.classLoader
        )
        if (cmcFeatureClass != null) {
            try {
                XposedBridge.hookAllMethods(
                    cmcFeatureClass,
                    "needToKillAndRestartMsgApp",
                    XC_MethodReplacement.returnConstant(false)
                )
            } catch (_: Throwable) {
            }
        }
    }

    fun showCmcMessageIndicator(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.MESSAGING) return

        val cmcOpenUtilsClass = XposedHelpers.findClassIfExists(
            "com.samsung.android.messaging.common.cmc.CmcOpenUtils",
            loadPackageParam.classLoader
        )
        if (cmcOpenUtilsClass != null) {
            try {
                XposedHelpers.findAndHookMethod(
                    cmcOpenUtilsClass,
                    "isCmcOpenMessageForView",
                    String::class.java,
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any {
                            val str = param.args[0] as? String ?: return false
                            return XposedHelpers.callStaticMethod(
                                cmcOpenUtilsClass,
                                "isCmcOpenMessage",
                                str
                            ) as? Boolean ?: false
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            try {
                XposedBridge.hookAllMethods(
                    cmcOpenUtilsClass,
                    "isCmcSwitcherSupportState",
                    XC_MethodReplacement.returnConstant(true)
                )
            } catch (_: Throwable) {
            }
        }

        val bottomViewClass = XposedHelpers.findClassIfExists(
            "com.samsung.android.messaging.ui.view.bubble.item.BubbleInfoBottomView",
            loadPackageParam.classLoader
        )
        val vClass = bottomViewClass?.superclass
        if (vClass != null) {
            try {
                XposedBridge.hookAllMethods(
                    vClass,
                    "d",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val isCmc = param.args[1] as? Boolean ?: false
                            if (isCmc) {
                                try {
                                    val simSlotImg = XposedHelpers.getObjectField(param.thisObject, "s") as? View
                                    simSlotImg?.visibility = View.GONE
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }
        }

        val bubbleListItemClass = XposedHelpers.findClassIfExists(
            "com.samsung.android.messaging.ui.view.bubble.item.BubbleListItem",
            loadPackageParam.classLoader
        )
        if (bubbleListItemClass != null) {
            try {
                val oMethod = bubbleListItemClass.declaredMethods.firstOrNull { it.name == "o" && it.parameterCount == 0 }
                if (oMethod != null) {
                    XposedBridge.hookMethod(
                        oMethod,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                try {
                                    val item = runCatching {
                                        XposedHelpers.callMethod(param.thisObject, "getMessagePartsItem")
                                    }.getOrNull() ?: runCatching {
                                        XposedHelpers.getObjectField(param.thisObject, "messagePartsItem")
                                    }.getOrNull() ?: runCatching {
                                        XposedHelpers.getObjectField(param.thisObject, "w")
                                    }.getOrNull() ?: return
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
                                        val container = param.thisObject as? LinearLayout ?: return
                                        val res = container.resources
                                        val slotId = res.getIdentifier("announcement_list_item_divider_sim_slot", "id", loadPackageParam.packageName)
                                        val devIconId = res.getIdentifier("orc_ic_device_cmc", "drawable", loadPackageParam.packageName)
                                        if (slotId != 0 && devIconId != 0) {
                                            val imageView = container.findViewById<ImageView>(slotId)
                                                ?: (runCatching { XposedHelpers.getObjectField(param.thisObject, "l") as? View }.getOrNull())?.findViewById(slotId)
                                            if (imageView != null) {
                                                imageView.visibility = View.VISIBLE
                                                imageView.setImageResource(devIconId)
                                            }
                                        }
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    )
                }
            } catch (_: Throwable) {
            }
        }
    }
}
