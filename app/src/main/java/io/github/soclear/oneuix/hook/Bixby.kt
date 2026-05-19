package io.github.soclear.oneuix.hook

import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.data.Package

/**
 * Bixby 限制解除 Hook
 *
 * 1. injectModel — 注入 Build.MODEL 到白名单缓存 → 解除离线+免唤醒设备限制
 * 2. labsFeatureManager — 绕过 Labs 功能检查 → 解除自定义唤醒词限制
 * 3. prefScreen — 强制 Labs Switch 可见（XML isPreferenceVisible=false）
 */
object Bixby {

    fun init(lpparam: LoadPackageParam, p: Preference.Bixby) {
        when (lpparam.packageName) {
            Package.BIXBY_AGENT -> initBixbyAgent(lpparam, p)
        }
    }

    private fun initBixbyAgent(lpparam: LoadPackageParam, p: Preference.Bixby) {
        XposedBridge.log("[Bixby] Init offline=${p.enableOffline} customWakeup=${p.enableCustomWakeup}")

        if (p.enableOffline) {
            try { hookInjectModel(lpparam); XposedBridge.log("[Bixby]   [+] injectModel") } catch (e: Throwable) { XposedBridge.log("[Bixby]   [-] injectModel: ${e.message}") }
        }

        if (p.enableCustomWakeup) {
            try { hookLabsFeatureManager(lpparam); XposedBridge.log("[Bixby]   [+] labsFeatureManager") } catch (e: Throwable) { XposedBridge.log("[Bixby]   [-] labsFeatureManager: ${e.message}") }
            try { hookPreferenceScreen(lpparam); XposedBridge.log("[Bixby]   [+] prefScreen") } catch (e: Throwable) { XposedBridge.log("[Bixby]   [-] prefScreen: ${e.message}") }
        }
    }

    // ═══════ 1. 注入设备型号到白名单缓存 ═══════

    private fun hookInjectModel(lpparam: LoadPackageParam) {
        findAndHookMethod("android.app.SharedPreferencesImpl", lpparam.classLoader,
            "getString", String::class.java, String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    if (p.args[0] == "pref_key_on_device_config_cache") {
                        val orig = (p.result ?: p.args[1] ?: "") as String
                        val model = Build.MODEL
                        if (!orig.contains(model))
                            p.result = if (orig.isEmpty()) model else "$orig,$model"
                    }
                }
            })
    }

    // ═══════ 2. 绕过 Labs 功能检查 ═══════

    private fun hookLabsFeatureManager(lpparam: LoadPackageParam) {
        try {
            val c = Class.forName(
                "com.samsung.android.bixby.agent.common.util.datamanager.LabsFeatureManager",
                true, lpparam.classLoader
            )
            for (name in arrayOf("isSupported", "isAvailable", "isEnabled")) {
                findAndHookMethod(c, name, String::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(mp: MethodHookParam) {
                        if (mp.args[0] == "labs_custom_wakeup") mp.result = true
                    }
                })
            }
            findAndHookMethod(c, "isLabsMenuSupported", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) { p.result = true }
            })
        } catch (_: Throwable) {}
    }

    // ═══════ 3. 强制 Labs Switch 可见 ═══════

    private fun hookPreferenceScreen(lpparam: LoadPackageParam) {
        try {
            val x = Class.forName("androidx.preference.x", true, lpparam.classLoader)
            val screenClass = Class.forName("androidx.preference.PreferenceScreen", true, lpparam.classLoader)
            findAndHookMethod(x, "t", screenClass, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val s = p.args[0]
                        val ctx = p.thisObject.javaClass.superclass!!.getMethod("getContext").invoke(p.thisObject)
                        val res = ctx.javaClass.getMethod("getResources").invoke(ctx)
                        val key = res.javaClass.getMethod("getString", java.lang.Integer.TYPE).invoke(res, 0x7f1407e4) as String
                        val fm = s.javaClass.methods.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0] == CharSequence::class.java && it.returnType == Class.forName("androidx.preference.Preference", true, lpparam.classLoader) } ?: return
                        val pref = fm.invoke(s, key) ?: return
                        val vm = pref.javaClass.methods.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0] == java.lang.Boolean.TYPE && it.returnType == Void.TYPE } ?: return
                        vm.invoke(pref, true)
                    } catch (_: Throwable) {}
                }
            })
        } catch (_: Throwable) {}
    }
}