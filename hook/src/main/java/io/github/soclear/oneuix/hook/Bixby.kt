package io.github.soclear.oneuix.hook

import android.content.Context
import android.os.Build
import android.os.SystemClock
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.bixby.BixbyModern
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.DebugFileLogger
import java.io.File
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Modifier
import java.util.Locale

object Bixby {

    private fun log(msg: String) {
        if (DebugFileLogger.isEnabled) android.util.Log.i("OneUIX-Bixby", msg)
        DebugFileLogger.log("Bixby", msg)
    }

    private fun logError(msg: String, t: Throwable? = null) {
        android.util.Log.e("OneUIX-Bixby", msg, t)
        DebugFileLogger.logError("Bixby", msg, t)
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init(p: Preference.Bixby) {
        DebugFileLogger.attachToProcess(param.packageName)
        when (param.packageName) {
            Package.BIXBY_AGENT  -> initBixbyAgent(p)
            Package.BIXBY_WAKEUP -> initBixbyWakeup(p)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun initBixbyAgent(p: Preference.Bixby) {
        log("Init offline=${p.injectModel} customWakeup=${p.labsMgr} wwv=${p.wwvBypass}")
        if (p.injectModel) hookInjectModel()
        if (p.labsMgr) {
            hookLabsFeatureManager()
            afterAttachTry {
                wakeupTextProviderReader = BixbyModern.installAgent(this) { text ->
                    updateWakeupTextCache(text)
                }
            }
        }
        if (p.wwvBypass) hookWakeupWordValidator()
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun initBixbyWakeup(p: Preference.Bixby) {
        if (p.labsMgr) {
            hookWakeupCustomPhrase()
        }
        if (p.wwvBypass) {
            hookWakeupWordTypeValidator()
            hookKwdAsianTextFix()
        }
    }

    // ═══════ injectModel: 注入 Build.MODEL 到设备白名单缓存 ═══════

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookInjectModel() {
        try {
            val spClass = param.classLoader.loadClass("android.app.SharedPreferencesImpl")
            val method = spClass.getDeclaredMethod("getString", String::class.java, String::class.java)
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (chain.args[0] == "pref_key_on_device_config_cache") {
                    val orig = result as? String ?: chain.args[1] as? String ?: ""
                    if (!orig.contains(Build.MODEL)) {
                        val newResult = if (orig.isEmpty()) Build.MODEL else "$orig,${Build.MODEL}"
                        log("injectModel matched cache='$orig' result='$newResult'")
                        newResult
                    } else {
                        result
                    }
                } else {
                    result
                }
            }
        } catch (t: Throwable) {
            logError("hookInjectModel failed", t)
        }
    }

    // ═══════ labsMgr: 绕过 LabsFeatureManager 所有限制 ═══════

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookLabsFeatureManager() {
        try {
            val c = Class.forName("com.samsung.android.bixby.agent.common.util.datamanager.LabsFeatureManager", true, param.classLoader)
            for (name in arrayOf("isSupported", "isAvailable", "isEnabled", "isLabs")) {
                val method = c.getDeclaredMethod(name, String::class.java)
                xposedModule.hook(method).intercept { chain ->
                    if (chain.args[0] == "labs_custom_wakeup") {
                        log("labsMgr forced $name(${chain.args[0]})=true")
                        true
                    } else {
                        chain.proceed()
                    }
                }
            }
            val isLabsMenuSupported = c.getDeclaredMethod("isLabsMenuSupported")
            xposedModule.hook(isLabsMenuSupported).intercept {
                log("labsMgr forced isLabsMenuSupported=true")
                true
            }
        } catch (t: Throwable) {
            logError("hookLabsFeatureManager failed", t)
        }
    }

    // ═══════ wwvBypass: 绕过原生库唤醒词黑名单（竞品词/脏话/政治等） ═══════
    // 签名匹配而非硬编码方法名，兼容不同 Bixby 版本

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookWakeupWordValidator() {
        try {
            val cls = param.classLoader.loadClass(
                "com.samsung.voicewakeup.wwv.WakeupWordValidator"
            )
            for (m in cls.declaredMethods) {
                if (!Modifier.isPublic(m.modifiers)) continue
                val pts = m.parameterTypes
                if (m.returnType == Boolean::class.javaPrimitiveType &&
                    pts.contentEquals(
                        arrayOf(
                            Locale::class.java,
                            String::class.java,
                            String::class.java,
                            String::class.java
                        )
                    )
                ) {
                    xposedModule.hook(m).intercept { chain ->
                        log(
                            "wwvBypass forced length validator ${m.name} " +
                                "locale=${chain.args[0]} keyword=${chain.args[1]}"
                        )
                        true
                    }
                }
                if (m.returnType == Int::class.javaPrimitiveType &&
                    pts.contentEquals(
                        arrayOf(
                            Context::class.java,
                            String::class.java,
                            Locale::class.java,
                            String::class.java
                        )
                    )
                ) {
                    xposedModule.hook(m).intercept { chain ->
                        log(
                            "wwvBypass forced blacklist validator ${m.name} " +
                                "keyword=${chain.args[1]} locale=${chain.args[2]}"
                        )
                        0
                    }
                }
            }
        } catch (t: Throwable) {
            logError("hookWakeupWordValidator failed", t)
        }
    }

    // ═══════ wakeup: 绕过 KWV isVaildWordType 的 locale 限制 ═══════
    // zhCN 等非韩语 locale 直接返回 false，导致 TEXT_CUSTOM 训练失败
    // 三个 decoder 变体 (normal/bargein/acousticecho) 均有同名方法

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookWakeupWordTypeValidator() {
        for (cn in arrayOf(
            "com.samsung.voicewakeup.kwv.normal.custom.WakeupKwvNormalCommon",
            "com.samsung.voicewakeup.kwv.bargein.custom.WakeupKwvBargeinCommon",
            "com.samsung.voicewakeup.kwv.acousticecho.custom.WakeupKwvAcousticEchoCommon")) {
            try {
                val cls = param.classLoader.loadClass(cn)
                var hooked = 0
                for (m in cls.declaredMethods) {
                    if (m.returnType != Boolean::class.javaPrimitiveType) continue
                    if (!m.parameterTypes.contentEquals(arrayOf(String::class.java, Locale::class.java))) continue
                    xposedModule.hook(m).intercept { chain ->
                        log("kwv locale bypass $cn.${m.name} text=${chain.args[0]} locale=${chain.args[1]}")
                        true
                    }
                    hooked++
                }
                log("kwv locale bypass installed class=$cn hooks=$hooked")
            } catch (t: Throwable) {
                logError("hookWakeupWordTypeValidator failed for $cn", t)
            }
        }
    }

    // ═══════ wakeup: KWD 引擎强制匹配亚洲文字唤醒词 ═══════
    // native KWD 引擎无法处理中文/韩文/日文文本，verifyRun 始终返回 0
    // 检测到 mKeyword 含相关文字时强改结果为 1，实际唤醒由 KWV 音频匹配把关

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookKwdAsianTextFix() {
        for (kn in arrayOf(
            "com.samsung.voicewakeup.kwd.normal.custom.WakeupKwdNormalCustom",
            "com.samsung.voicewakeup.kwd.bargein.custom.WakeupKwdBargeinCustom",
            "com.samsung.voicewakeup.kwd.acousticecho.custom.WakeupKwdAcousticEchoCustom")) {
            try {
                val cls = param.classLoader.loadClass(kn)
                var kwField: java.lang.reflect.Field? = null
                try { kwField = cls.getDeclaredField("mKeyword"); kwField.isAccessible = true } catch (_: Throwable) {}
                var hooked = 0

                for (m in cls.declaredMethods) {
                    if (m.returnType != Int::class.javaPrimitiveType) continue
                    val pts = m.parameterTypes
                    val isVr = (pts.size == 1 && pts[0].isArray && pts[0].componentType == Short::class.javaPrimitiveType)
                        || (pts.size == 3 && pts[0].isArray && pts[0].componentType == Short::class.javaPrimitiveType
                            && pts[1] == Int::class.javaPrimitiveType && pts[2] == Int::class.javaPrimitiveType)
                    if (!isVr) continue

                    val kwF = kwField
                    xposedModule.hook(m).intercept { chain ->
                        val result = chain.proceed()
                        val ret = result as? Int ?: return@intercept result
                        if (ret != 0) return@intercept ret
                        val kw = try { kwF?.get(chain.thisObject) ?: "" } catch (_: Throwable) { "" }
                        if ((kw as? String)?.any { it.isAsianWakeupCharacter() } == true) {
                            log("kwd asian text fix $kn.${m.name} keyword=$kw")
                            1
                        } else {
                            ret
                        }
                    }
                    hooked++
                }
                log("kwd asian text fix installed class=$kn hooks=$hooked hasKeywordField=${kwField != null}")
            } catch (t: Throwable) {
                logError("hookKwdAsianTextFix failed for $kn", t)
            }
        }
    }

    private fun Char.isAsianWakeupCharacter(): Boolean {
        return this in '\u4E00'..'\u9FFF' ||
            this in '\uAC00'..'\uD7AF' ||
            this in '\u3040'..'\u30FF'
    }

    // ═══════ wakeup: 修复自定义短语文本返回空的问题 ═══════

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookWakeupCustomPhrase() {
        // SP.getString → 数据源为空时从 XML 文件读取
        try {
            val spClass = param.classLoader.loadClass("android.app.SharedPreferencesImpl")
            val getString = spClass.getDeclaredMethod("getString", String::class.java, String::class.java)
            xposedModule.hook(getString).intercept { chain ->
                val result = chain.proceed()
                if (chain.args[0] == CUSTOM_WAKEUP_TEXT_KEY) {
                    val orig = result as? String
                    if (orig.isNullOrEmpty()) {
                        val txt = readWakeupText()
                        if (txt.isNotEmpty()) {
                            log("custom phrase fallback from shared_prefs key=$CUSTOM_WAKEUP_TEXT_KEY value=$txt")
                            txt
                        } else {
                            result
                        }
                    } else {
                        updateWakeupTextCache(orig)
                        result
                    }
                } else {
                    result
                }
            }
        } catch (t: Throwable) {
            logError("hookWakeupCustomPhrase getString failed", t)
        }

        // MatrixCursor.addRow → ContentProvider locale 不匹配后丢弃文本
        try {
            val c = param.classLoader.loadClass("android.database.MatrixCursor")
            val columnNamesField = try {
                c.getDeclaredField("columnNames").apply { isAccessible = true }
            } catch (_: Throwable) {
                null
            }
            val addRow = c.getDeclaredMethod("addRow", arrayOfNulls<Any>(0).javaClass)
            xposedModule.hook(addRow).intercept { chain ->
                val row = chain.args.firstOrNull()
                if (row != null && row.javaClass.isArray) {
                    try {
                        val cols = columnNamesField?.get(chain.thisObject) as? Array<*>
                        if (cols != null) {
                            val rowSize = ReflectArray.getLength(row)
                            for (i in cols.indices) {
                                if (i >= rowSize) break
                                if (cols[i] != "customKeyword") continue
                                val value = ReflectArray.get(row, i)
                                if (value == null || value.toString().isEmpty()) {
                                    val txt = readWakeupText()
                                    if (txt.isNotEmpty()) {
                                        ReflectArray.set(row, i, txt)
                                        log("customKeyword row filled from XML cache value=$txt")
                                    }
                                } else {
                                    updateWakeupTextCache(value.toString())
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        logError("MatrixCursor customKeyword patch failed", t)
                    }
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logError("hookWakeupCustomPhrase failed", t)
        }
    }

    private fun readWakeupText(): String {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedWakeupText
        if (!cached.isNullOrEmpty() && now - cachedWakeupTextTimeMillis < WAKEUP_TEXT_CACHE_TTL_MILLIS) {
            return cached
        }

        val providerText = runCatching { wakeupTextProviderReader?.invoke().orEmpty() }
            .onFailure { logError("wakeup text provider query failed", it) }
            .getOrDefault("")
        if (providerText.isNotBlank()) {
            updateWakeupTextCache(providerText, now)
            return providerText
        }
        if (cached != null && now - cachedWakeupTextTimeMillis < WAKEUP_TEXT_CACHE_TTL_MILLIS) {
            return cached
        }

        // I/O 优化：优先直读固定的主首选项文件，找不到时再回退扫描整个目录
        val text = try {
            val primaryFile = File(BIXBY_WAKEUP_SHARED_PREFERENCES_PATH, BIXBY_WAKEUP_PREFS_FILE)
            if (primaryFile.exists() && primaryFile.canRead()) {
                val match = WAKEUP_TEXT_REGEX.find(primaryFile.readText())
                if (match != null) {
                    log("read wakeup text from primary preferences value=${match.groupValues[1]}")
                    match.groupValues[1]
                } else {
                    scanAllPrefsFallback()
                }
            } else {
                scanAllPrefsFallback()
            }
        } catch (t: Throwable) {
            logError("readWakeupText failed", t)
            ""
        }

        updateWakeupTextCache(text, now)
        return text
    }

    private fun scanAllPrefsFallback(): String {
        val dir = File(BIXBY_WAKEUP_SHARED_PREFERENCES_PATH)
        if (!dir.exists() || !dir.isDirectory) return ""
        for (f in dir.listFiles() ?: emptyArray()) {
            if (!f.name.endsWith(".xml") || f.name == BIXBY_WAKEUP_PREFS_FILE) continue
            val match = WAKEUP_TEXT_REGEX.find(f.readText())
            if (match != null) {
                log("read wakeup text fallback from ${f.name} value=${match.groupValues[1]}")
                return match.groupValues[1]
            }
        }
        return ""
    }

    private fun updateWakeupTextCache(text: String, now: Long = SystemClock.elapsedRealtime()) {
        if (cachedWakeupText != text) {
            log("wakeup text cache updated value=$text")
        }
        cachedWakeupText = text
        cachedWakeupTextTimeMillis = now
    }

    private const val CUSTOM_WAKEUP_TEXT_KEY = "myvoice_string_custom"
    private const val WAKEUP_TEXT_CACHE_TTL_MILLIS = 5_000L
    private const val BIXBY_WAKEUP_SHARED_PREFERENCES_PATH =
        "/data/data/com.samsung.android.bixby.wakeup/shared_prefs"
    private const val BIXBY_WAKEUP_PREFS_FILE =
        "com.samsung.android.bixby.wakeup_preferences.xml"
    private val WAKEUP_TEXT_REGEX = Regex("<string name=\"$CUSTOM_WAKEUP_TEXT_KEY\">(.*?)</string>")

    @Volatile
    private var wakeupTextProviderReader: (() -> String)? = null
    @Volatile
    private var cachedWakeupText: String? = null
    @Volatile
    private var cachedWakeupTextTimeMillis: Long = 0
}
