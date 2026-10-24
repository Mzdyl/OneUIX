package io.github.soclear.oneuix.hook

import android.app.Activity
import android.content.Context
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.hook.util.xlog
import java.util.ArrayList

object WatchPlugin {

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init(pref: Preference.Other) {
        xlog("OneUIX: WatchPlugin init for ${param.packageName}")
        if (pref.preventWatchPluginUnpair) {
            preventWearOsPackageRemovedReset()
        }
        if (pref.unlockWatchPluginGlobalFeatures) {
            unlockGlobalFeatures()
        }
        if (pref.forceShowAllWatchfaceComplications) {
            forceShowAllComplications()
        }
        if (pref.enableWatchPluginHiddenMenuShortcut) {
            enableHiddenMenuShortcut()
        }
    }

    /**
     * 拦截卸载 Wear OS CN 时触发的手表重置机制。
     *
     * 背景：
     * 三星手表插件（watchuniteplugin、waterplugin 等）后台常驻服务 SettingsCompanion
     * 会监听 ACTION_PACKAGE_REMOVED。当监听到 com.google.android.wearable.app.cn 被卸载且手机
     * 判定为国行时，会触发 requestWearOSPluginList() 并通过 launchDialogActivity() 调起 SettingsDialogActivity。
     * SettingsDialogActivity 在 onCreate() 中会立即无条件调用 removeBond()，
     * 向蓝牙协议栈发送 WEAR_OS_PACKAGE_REMOVED 指令并清除底层配对关系，强行导致手表断联并重置。
     *
     * 防护策略（四重拦截）：
     * 0. 拦截 SettingsCompanion.requestWearOSPluginList()，从源头掐灭向 WearOSPluginInfoSubscriber 的请求；
     * 1. 拦截 SettingsCompanion.launchDialogActivity(ArrayList)，阻止调起解绑弹窗 Activity；
     * 2. 拦截 SettingsDialogActivity.removeBond(ArrayList)，彻底掐死任何触发底层蓝牙解绑的通路；
     * 3. 拦截 SettingsDialogActivity.showDialog(ArrayList)，直接 finish() 避免显示弹窗。
     */
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun preventWearOsPackageRemovedReset() {
        // 闸门 0：拦截 SettingsCompanion.requestWearOSPluginList()（源头拦截）
        try {
            val companionClass = param.classLoader.loadClass(
                "com.samsung.android.companionservice.service.SettingsCompanion"
            )
            val requestMethod = companionClass.getDeclaredMethod("requestWearOSPluginList")
            requestMethod.isAccessible = true
            xposedModule.hook(requestMethod).intercept {
                xlog("OneUIX: [WatchPlugin] BLOCKED SettingsCompanion.requestWearOSPluginList()!")
                Unit
            }
            xlog("OneUIX: [WatchPlugin] Hooked SettingsCompanion.requestWearOSPluginList")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook SettingsCompanion.requestWearOSPluginList failed: $t")
        }

        // 闸门 1：拦截 SettingsCompanion.launchDialogActivity(ArrayList<String>)
        try {
            val companionClass = param.classLoader.loadClass(
                "com.samsung.android.companionservice.service.SettingsCompanion"
            )
            val launchMethod = companionClass.getDeclaredMethod("launchDialogActivity", ArrayList::class.java)
            xposedModule.hook(launchMethod).intercept { chain ->
                xlog("OneUIX: [WatchPlugin] BLOCKED SettingsCompanion.launchDialogActivity(${chain.args.firstOrNull()})")
                Unit
            }
            xlog("OneUIX: [WatchPlugin] Hooked SettingsCompanion.launchDialogActivity")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook SettingsCompanion.launchDialogActivity failed: $t")
        }

        // 闸门 2：拦截 SettingsDialogActivity.removeBond(ArrayList<DeviceRegistryData>)
        try {
            val dialogActivityClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.activity.SettingsDialogActivity"
            )
            val removeBondMethod = dialogActivityClass.getDeclaredMethod("removeBond", ArrayList::class.java)
            removeBondMethod.isAccessible = true
            xposedModule.hook(removeBondMethod).intercept { chain ->
                xlog("OneUIX: [WatchPlugin] BLOCKED SettingsDialogActivity.removeBond! Prevented unpair/reset.")
                Unit
            }
            xlog("OneUIX: [WatchPlugin] Hooked SettingsDialogActivity.removeBond")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook SettingsDialogActivity.removeBond failed: $t")
        }

        // 闸门 3：拦截 SettingsDialogActivity.showDialog(ArrayList<DeviceRegistryData>)
        try {
            val dialogActivityClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.activity.SettingsDialogActivity"
            )
            val showDialogMethod = dialogActivityClass.getDeclaredMethod("showDialog", ArrayList::class.java)
            showDialogMethod.isAccessible = true
            xposedModule.hook(showDialogMethod).intercept { chain ->
                xlog("OneUIX: [WatchPlugin] BLOCKED SettingsDialogActivity.showDialog, finishing activity")
                (chain.thisObject as? Activity)?.finish()
                Unit
            }
            xlog("OneUIX: [WatchPlugin] Hooked SettingsDialogActivity.showDialog")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook SettingsDialogActivity.showDialog failed: $t")
        }
    }

    /**
     * 解锁手表海外全量功能与 Google 账号登录/同步：
     * 1. 拦截 GoogleRequirementUtils.isChinaPhoneByGoogleLogic(Context) -> 返回 false
     * 2. 拦截 SharedCommonUtils.isChinaModel(Context) -> 返回 false
     * 3. 拦截 SharedCommonUtils.isChinaRegion(Context) -> 返回 false
     * 4. 拦截 RaiseToTalkFeature.isChinaModelWatch() -> 返回 false
     * 5. 拦截 WatchfaceUtils.isChinaModelWatch() -> 返回 false
     * 6. 拦截 CommonUtils.isChinaWatchModel() -> 返回 false
     * 7. 拦截 UIUtils.isChinaModelWatch(Context, String) -> 返回 false
     * 8. 拦截 SettingsFeatureCheckerKt.isSupport(SettingsFeature) -> 对 GOOGLE_DIGITAL_ASSISTANT 及 RAISE_TO_TALK_SUPPORT 返回 true
     */
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun unlockGlobalFeatures() {
        // 1. GoogleRequirementUtils.isChinaPhoneByGoogleLogic(Context)
        try {
            val googleReqClass = param.classLoader.loadClass(
                "com.samsung.android.plugin.sharedlib.utils.GoogleRequirementUtils"
            )
            val method = googleReqClass.getDeclaredMethod("isChinaPhoneByGoogleLogic", Context::class.java)
            method.isAccessible = true
            xposedModule.hook(method).intercept {
                xlog("OneUIX: [WatchPlugin] GoogleRequirementUtils.isChinaPhoneByGoogleLogic() -> false")
                false
            }
            xlog("OneUIX: [WatchPlugin] Hooked GoogleRequirementUtils.isChinaPhoneByGoogleLogic")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook GoogleRequirementUtils.isChinaPhoneByGoogleLogic failed: $t")
        }

        // 2 & 3. SharedCommonUtils.isChinaModel(Context) & isChinaRegion(Context)
        try {
            val sharedCommonUtilsClass = param.classLoader.loadClass(
                "com.samsung.android.plugin.sharedlib.utils.SharedCommonUtils"
            )
            try {
                val isChinaModelMethod = sharedCommonUtilsClass.getDeclaredMethod("isChinaModel", Context::class.java)
                isChinaModelMethod.isAccessible = true
                xposedModule.hook(isChinaModelMethod).intercept {
                    xlog("OneUIX: [WatchPlugin] SharedCommonUtils.isChinaModel() -> false")
                    false
                }
                xlog("OneUIX: [WatchPlugin] Hooked SharedCommonUtils.isChinaModel")
            } catch (t: Throwable) {
                xlog("OneUIX: [WatchPlugin] Hook SharedCommonUtils.isChinaModel failed: $t")
            }

            try {
                val isChinaRegionMethod = sharedCommonUtilsClass.getDeclaredMethod("isChinaRegion", Context::class.java)
                isChinaRegionMethod.isAccessible = true
                xposedModule.hook(isChinaRegionMethod).intercept {
                    xlog("OneUIX: [WatchPlugin] SharedCommonUtils.isChinaRegion() -> false")
                    false
                }
                xlog("OneUIX: [WatchPlugin] Hooked SharedCommonUtils.isChinaRegion")
            } catch (t: Throwable) {
                xlog("OneUIX: [WatchPlugin] Hook SharedCommonUtils.isChinaRegion failed: $t")
            }
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Load SharedCommonUtils failed: $t")
        }

        // 4. RaiseToTalkFeature.isChinaModelWatch()
        try {
            val raiseToTalkClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.settings.module.buttonsandgestures.raisetotalk.RaiseToTalkFeature"
            )
            val isChinaWatchMethod = raiseToTalkClass.getDeclaredMethod("isChinaModelWatch")
            isChinaWatchMethod.isAccessible = true
            xposedModule.hook(isChinaWatchMethod).intercept {
                xlog("OneUIX: [WatchPlugin] RaiseToTalkFeature.isChinaModelWatch() -> false")
                false
            }
            xlog("OneUIX: [WatchPlugin] Hooked RaiseToTalkFeature.isChinaModelWatch")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook RaiseToTalkFeature.isChinaModelWatch failed: $t")
        }

        // 5. WatchfaceUtils.isChinaModelWatch()
        try {
            val watchfaceUtilsClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.watchface.common.util.WatchfaceUtils"
            )
            val isChinaWatchMethod = watchfaceUtilsClass.getDeclaredMethod("isChinaModelWatch")
            isChinaWatchMethod.isAccessible = true
            xposedModule.hook(isChinaWatchMethod).intercept {
                xlog("OneUIX: [WatchPlugin] WatchfaceUtils.isChinaModelWatch() -> false")
                false
            }
            xlog("OneUIX: [WatchPlugin] Hooked WatchfaceUtils.isChinaModelWatch")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook WatchfaceUtils.isChinaModelWatch failed: $t")
        }

        // 6. CommonUtils.isChinaWatchModel()
        try {
            val commonUtilsClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.watchface.compose.util.CommonUtils"
            )
            val isChinaWatchModelMethod = commonUtilsClass.getDeclaredMethod("isChinaWatchModel")
            isChinaWatchModelMethod.isAccessible = true
            xposedModule.hook(isChinaWatchModelMethod).intercept {
                xlog("OneUIX: [WatchPlugin] CommonUtils.isChinaWatchModel() -> false")
                false
            }
            xlog("OneUIX: [WatchPlugin] Hooked CommonUtils.isChinaWatchModel")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook CommonUtils.isChinaWatchModel failed: $t")
        }

        // 7. UIUtils.isChinaModelWatch(Context, String)
        try {
            val uiUtilsClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.utils.UIUtils"
            )
            val isChinaModelWatchMethod = uiUtilsClass.getDeclaredMethod(
                "isChinaModelWatch",
                Context::class.java,
                String::class.java
            )
            isChinaModelWatchMethod.isAccessible = true
            xposedModule.hook(isChinaModelWatchMethod).intercept {
                xlog("OneUIX: [WatchPlugin] UIUtils.isChinaModelWatch() -> false")
                false
            }
            xlog("OneUIX: [WatchPlugin] Hooked UIUtils.isChinaModelWatch")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook UIUtils.isChinaModelWatch failed: $t")
        }

        // 8. SettingsFeatureCheckerKt.isSupport(SettingsFeature)
        try {
            val checkerClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.settings.manager.SettingsFeatureCheckerKt"
            )
            val isSupportMethod = checkerClass.declaredMethods.firstOrNull { it.name == "isSupport" }
            if (isSupportMethod != null) {
                isSupportMethod.isAccessible = true
                xposedModule.hook(isSupportMethod).intercept { chain ->
                    val feature = chain.args.firstOrNull()
                    val featureName = (feature as? Enum<*>)?.name ?: feature?.toString() ?: ""
                    if (featureName == "GOOGLE_DIGITAL_ASSISTANT" || featureName == "RAISE_TO_TALK_SUPPORT") {
                        xlog("OneUIX: [WatchPlugin] SettingsFeatureCheckerKt.isSupport($featureName) -> true")
                        return@intercept true
                    }
                    chain.proceed()
                }
                xlog("OneUIX: [WatchPlugin] Hooked SettingsFeatureCheckerKt.isSupport")
            }
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook SettingsFeatureCheckerKt.isSupport failed: $t")
        }
    }

    /**
     * 强制解锁全量表盘复杂功能/微件：
     * 1. 拦截 WatchFaceSharedUtil.getSharedPref 当读取 pref_conditional_complication_view 时返回 true
     * 2. 拦截 ComplicationData.isSupportedComplication(...) 返回 true
     */
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun forceShowAllComplications() {
        // 1. WatchFaceSharedUtil.getSharedPref(Context, String, String, boolean)
        try {
            val sharedUtilClass = param.classLoader.loadClass(
                "com.samsung.android.plugin.sharedlib.watchface.WatchFaceSharedUtil"
            )
            val getSharedPrefMethod = sharedUtilClass.getDeclaredMethod(
                "getSharedPref",
                Context::class.java,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            getSharedPrefMethod.isAccessible = true
            xposedModule.hook(getSharedPrefMethod).intercept { chain ->
                val fileName = chain.args[1] as? String
                if (fileName == "pref_conditional_complication_view") {
                    xlog("OneUIX: [WatchPlugin] WatchFaceSharedUtil.getSharedPref(pref_conditional_complication_view) -> true")
                    return@intercept java.lang.Boolean.TRUE
                }
                chain.proceed()
            }
            xlog("OneUIX: [WatchPlugin] Hooked WatchFaceSharedUtil.getSharedPref")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook WatchFaceSharedUtil.getSharedPref failed: $t")
        }

        // 2. ComplicationData.isSupportedComplication
        try {
            val complicationDataClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.watchface.customization.model.ComplicationData"
            )
            val method = complicationDataClass.declaredMethods.firstOrNull { it.name == "isSupportedComplication" }
            if (method != null) {
                method.isAccessible = true
                xposedModule.hook(method).intercept {
                    true
                }
                xlog("OneUIX: [WatchPlugin] Hooked ComplicationData.isSupportedComplication")
            }
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook ComplicationData.isSupportedComplication failed: $t")
        }
    }

    /**
     * 开启隐藏工程菜单一键直达与表盘调试：
     * 1. 拦截 AboutGalaxyWearableStartFragment.loadHiddenMenuFragment()，单击直接进入 HiddenMenuFragment
     * 2. 拦截 ModuleTestHiddenFragment.onViewCreated，强制将 button_watchface 设为 View.VISIBLE 并绑定点击事件
     */
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun enableHiddenMenuShortcut() {
        // 1. loadHiddenMenuFragment() 单击直达
        try {
            val aboutFragmentClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.aboutgalaxywearable.fragments.AboutGalaxyWearableStartFragment"
            )
            val loadMethod = aboutFragmentClass.declaredMethods.firstOrNull { it.name == "loadHiddenMenuFragment" }
            if (loadMethod != null) {
                loadMethod.isAccessible = true
                xposedModule.hook(loadMethod).intercept { chain ->
                    xlog("OneUIX: [WatchPlugin] loadHiddenMenuFragment() called -> launching HiddenMenuFragment directly")
                    val target = chain.thisObject
                    val contextMethod = target.javaClass.methods.firstOrNull { it.name == "getMContext" || it.name == "getContext" }
                    val context = contextMethod?.invoke(target) as? Context
                    if (context != null) {
                        val navClass = param.classLoader.loadClass("com.samsung.android.waterplugin.activity.Navigator")
                        val hiddenClass = param.classLoader.loadClass("com.samsung.android.waterplugin.aboutgalaxywearable.hidden.HiddenMenuFragment")
                        val startMethod = navClass.methods.firstOrNull {
                            it.name == "startSecondLvlFragment" && it.parameterTypes.size == 2 &&
                                Context::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                                Class::class.java.isAssignableFrom(it.parameterTypes[1])
                        }
                        if (startMethod != null) {
                            startMethod.invoke(null, context, hiddenClass)
                            return@intercept Unit
                        }
                    }
                    chain.proceed()
                }
                xlog("OneUIX: [WatchPlugin] Hooked AboutGalaxyWearableStartFragment.loadHiddenMenuFragment")
            }
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook AboutGalaxyWearableStartFragment.loadHiddenMenuFragment failed: $t")
        }

        // 2. ModuleTestHiddenFragment.onViewCreated 解锁 button_watchface
        try {
            val moduleTestClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.aboutgalaxywearable.hidden.ModuleTestHiddenFragment"
            )
            val onViewCreatedMethod = moduleTestClass.declaredMethods.firstOrNull { it.name == "onViewCreated" }
            if (onViewCreatedMethod != null) {
                onViewCreatedMethod.isAccessible = true
                xposedModule.hook(onViewCreatedMethod).intercept { chain ->
                    chain.proceed()
                    val view = chain.args.firstOrNull() as? View ?: return@intercept Unit
                    val buttonId = view.resources.getIdentifier("button_watchface", "id", view.context.packageName)
                    if (buttonId != 0) {
                        val button = view.findViewById<View>(buttonId)
                        if (button != null) {
                            button.visibility = View.VISIBLE
                            (chain.thisObject as? View.OnClickListener)?.let { listener ->
                                button.setOnClickListener(listener)
                            }
                            xlog("OneUIX: [WatchPlugin] ModuleTestHiddenFragment: button_watchface unlocked to View.VISIBLE")
                        }
                    }
                    Unit
                }
                xlog("OneUIX: [WatchPlugin] Hooked ModuleTestHiddenFragment.onViewCreated")
            }
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook ModuleTestHiddenFragment.onViewCreated failed: $t")
        }
    }
}
