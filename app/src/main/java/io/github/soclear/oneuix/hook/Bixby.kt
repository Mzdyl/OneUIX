package io.github.soclear.oneuix.hook

import android.content.Context
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.data.Package
import java.io.File
import java.lang.reflect.Modifier
import java.util.Locale

object Bixby {

    private fun log(msg: String) { XposedBridge.log("[OneUIX-Bixby] $msg") }

    fun init(lpparam: LoadPackageParam, p: Preference.Bixby) {
        when (lpparam.packageName) {
            Package.BIXBY_AGENT  -> initBixbyAgent(lpparam, p)
            Package.BIXBY_WAKEUP -> initBixbyWakeup(lpparam)
        }
    }

    private fun initBixbyAgent(lpparam: LoadPackageParam, p: Preference.Bixby) {
        log("Init offline=${p.injectModel} customWakeup=${p.labsMgr} wwv=${p.wwvBypass}")
        if (p.injectModel) hookInjectModel(lpparam)
        if (p.labsMgr)    hookLabsFeatureManager(lpparam)
        if (p.wwvBypass)  hookWakeupWordValidator(lpparam)
    }

    private fun initBixbyWakeup(lpparam: LoadPackageParam) {
        hookWakeupCustomPhrase(lpparam)
    }

    // ═══════ injectModel: 注入 Build.MODEL 到设备白名单缓存 ═══════

    private fun hookInjectModel(lpparam: LoadPackageParam) {
        findAndHookMethod("android.app.SharedPreferencesImpl", lpparam.classLoader,
            "getString", String::class.java, String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    if (p.args[0] == "pref_key_on_device_config_cache") {
                        val orig = (p.result ?: p.args[1] ?: "") as String
                        if (!orig.contains(Build.MODEL))
                            p.result = if (orig.isEmpty()) Build.MODEL else "$orig,${Build.MODEL}"
                    }
                }
            })
    }

    // ═══════ labsMgr: 绕过 LabsFeatureManager 所有限制 ═══════

    private fun hookLabsFeatureManager(lpparam: LoadPackageParam) {
        try {
            val c = Class.forName("com.samsung.android.bixby.agent.common.util.datamanager.LabsFeatureManager", true, lpparam.classLoader)
            for (name in arrayOf("isSupported", "isAvailable", "isEnabled", "isLabs")) {
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

    // ═══════ wwvBypass: 绕过原生库唤醒词黑名单（竞品词/脏话/政治等） ═══════
    // 签名匹配而非硬编码方法名，兼容不同 Bixby 版本

    private fun hookWakeupWordValidator(lpparam: LoadPackageParam) {
        val cls = lpparam.classLoader.loadClass("com.samsung.voicewakeup.wwv.WakeupWordValidator")
        for (m in cls.declaredMethods) {
            if (!Modifier.isPublic(m.modifiers)) continue
            val pts = m.parameterTypes
            // b(Locale, String, String, String) boolean → 绕过长度校验
            if (m.returnType == Boolean::class.javaPrimitiveType &&
                pts.contentEquals(arrayOf(Locale::class.java, String::class.java, String::class.java, String::class.java))) {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) { p.result = true }
                })
            }
            // d(Context, String, Locale, String) int → 绕过所有黑名单校验
            if (m.returnType == Int::class.javaPrimitiveType &&
                pts.contentEquals(arrayOf(Context::class.java, String::class.java, Locale::class.java, String::class.java))) {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) { p.result = 0 }
                })
            }
        }
    }

    // ═══════ wakeup: 修复自定义短语文本返回空的问题 ═══════

    private fun hookWakeupCustomPhrase(lpparam: LoadPackageParam) {
        // SP.getString → 数据源为空时从 XML 文件读取
        findAndHookMethod("android.app.SharedPreferencesImpl", lpparam.classLoader,
            "getString", String::class.java, String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    if (p.args[0] == "myvoice_string_custom") {
                        val orig = p.result ?: p.args[1] ?: ""
                        if (orig.toString().isEmpty()) {
                            val txt = readWakeupSP("myvoice_string_custom")
                            if (txt.isNotEmpty()) p.result = txt
                        }
                    }
                }
            })
        // MatrixCursor.addRow → ContentProvider locale 不匹配后丢弃文本
        try {
            val c = lpparam.classLoader.loadClass("android.database.MatrixCursor")
            findAndHookMethod(c, "addRow", arrayOfNulls<Any>(0).javaClass, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val row = p.args[0] as? Array<Any?> ?: return
                    try {
                        val cols = p.thisObject.javaClass.getDeclaredField("columnNames").apply { isAccessible = true }.get(p.thisObject) as? Array<String> ?: return
                        for (i in cols.indices) {
                            if (cols[i] != "customKeyword") continue
                            if (row[i] == null || row[i].toString().isEmpty()) {
                                val txt = readWakeupSP("myvoice_string_custom")
                                if (txt.isNotEmpty()) row[i] = txt
                            }
                        }
                    } catch (_: Throwable) {}
                }
            })
        } catch (_: Throwable) {}
    }

    private fun readWakeupSP(key: String): String {
        try {
            val dir = File("/data/data/com.samsung.android.bixby.wakeup/shared_prefs")
            if (!dir.exists() || !dir.isDirectory) return ""
            for (f in dir.listFiles() ?: emptyArray()) {
                if (!f.name.endsWith(".xml")) continue
                val m = Regex("<string name=\"$key\">(.*?)</string>").find(f.readText())
                if (m != null) return m.groupValues[1]
            }
        } catch (_: Throwable) {}
        return ""
    }
}