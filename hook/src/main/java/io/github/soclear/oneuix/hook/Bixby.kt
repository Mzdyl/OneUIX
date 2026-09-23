package io.github.soclear.oneuix.hook

import android.content.Context
import android.os.Build
import android.os.SystemClock
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.DebugFileLogger
import java.io.File
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

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
            hookWakeupKeywordTypeBridge()
            hookAgentCustomPhraseBridge()
            hookCustomWakeupResourceDownload()
            hookAgentEnrollmentFlow()
        }
        if (p.wwvBypass) hookWakeupWordValidator()
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun initBixbyWakeup(p: Preference.Bixby) {
        if (p.labsMgr) {
            hookWakeupCustomPhrase()
            hookCustomWakeupTrainers()
            hookWakeupSpotterFlow()
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

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookCustomWakeupTrainers() {
        hookCustomWakeupTrainerClass("uc.a", "CustomKwdTrainer")
        hookCustomWakeupTrainerClass("uc.b", "CustomKwvTrainer")
        hookCustomWakeupTrainerClass("sb.c", "CustomWakeupKwdCoreWrapper")
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookCustomWakeupTrainerClass(className: String, tag: String) {
        try {
            val cls = param.classLoader.loadClass(className)
            var hooked = 0
            for (m in cls.declaredMethods) {
                when {
                    m.name == "c" && m.parameterTypes.size == 2 -> {
                        xposedModule.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            val gVar = chain.args.getOrNull(0)
                            val lVar = chain.args.getOrNull(1)
                            val wakeupWord = extractStringField(lVar, "f7970e")
                            val debugMode = extractBooleanField(lVar, "f7968c")
                            val locale = extractLocaleField(gVar, "f7962b")
                            log("$tag prepare ok=$result wakeupWord=$wakeupWord debug=$debugMode locale=$locale")
                            result
                        }
                        hooked++
                    }
                    m.name == "b" && m.parameterTypes.size == 2 -> {
                        xposedModule.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            log("$tag train ok=$result")
                            result
                        }
                        hooked++
                    }
                    m.name == "m" && m.parameterTypes.size == 1 -> {
                        xposedModule.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            log("$tag verifyRun ok=$result")
                            result
                        }
                        hooked++
                    }
                    m.name == "release" && m.parameterTypes.isEmpty() -> {
                        xposedModule.hook(m).intercept { chain ->
                            val result = chain.proceed()
                            log("$tag release")
                            result
                        }
                        hooked++
                    }
                }
            }
            log("$tag hooks installed class=$className count=$hooked")
        } catch (t: Throwable) {
            logError("hookCustomWakeupTrainerClass failed for $className", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookWakeupKeywordTypeBridge() {
        try {
            val cls = param.classLoader.loadClass("eh0.l0")
            var hooked = 0
            for (m in cls.declaredMethods) {
                if (m.name != "l" || m.parameterTypes.size != 2) continue
                if (m.parameterTypes[0] != Context::class.java || m.parameterTypes[1] != String::class.java) continue
                xposedModule.hook(m).intercept { chain ->
                    val result = chain.proceed()
                    log("keywordType update ${m.name} value=${chain.args.getOrNull(1)}")
                    result
                }
                hooked++
            }
            for (m in cls.declaredMethods) {
                if (m.name != "a" || m.parameterTypes.size != 1) continue
                if (m.parameterTypes[0] != Context::class.java) continue
                xposedModule.hook(m).intercept { chain ->
                    val result = chain.proceed()
                    log("keywordType query ${m.name} result=$result")
                    result
                }
                hooked++
            }
            log("keywordType bridge installed hooks=$hooked")
        } catch (t: Throwable) {
            logError("hookWakeupKeywordTypeBridge failed", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookAgentCustomPhraseBridge() {
        try {
            val cls = param.classLoader.loadClass("ut.m")
            val getter = cls.declaredMethods.firstOrNull { method ->
                method.name == "a" &&
                    Modifier.isStatic(method.modifiers) &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            } ?: return
            getter.isAccessible = true
            wakeupTextProviderReader = {
                (getter.invoke(null) as? String).orEmpty().also { text ->
                    if (text.isNotBlank()) updateWakeupTextCache(text)
                }
            }
            xposedModule.hook(getter).intercept { chain ->
                val result = chain.proceed()
                val text = result as? String
                if (!text.isNullOrBlank()) updateWakeupTextCache(text)
                result
            }
            log("custom phrase provider bridge installed")
        } catch (t: Throwable) {
            logError("hookAgentCustomPhraseBridge failed", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookCustomWakeupResourceDownload() {
        try {
            val cls = param.classLoader.loadClass("ck0.a")
            val invoke = cls.declaredMethods.firstOrNull { method ->
                method.name == "invoke" && method.parameterTypes.size == 1
            } ?: return
            xposedModule.hook(invoke).intercept { chain ->
                if (extractIntField(chain.thisObject, "f9823a") == 17) {
                    val presenter = extractAnyField(chain.thisObject, "f9824b")
                    if (presenter != null && presenter.javaClass.name == "dk0.l") {
                        val result = chain.args.getOrNull(0) as? Int
                        if (result != null) {
                            val resourceState = queryCustomWakeupResourceState(param.classLoader)
                            log("custom wakeup PUS result=$result resourceState=$resourceState")

                            if (result == 1) {
                                customWakeupResourceRetryByPresenter.remove(presenter)
                                return@intercept chain.proceed()
                            }
                            if (result != -1) {
                                customWakeupResourceRetryByPresenter.remove(presenter)
                                return@intercept chain.proceed()
                            }
                            if (resourceState == CUSTOM_WAKEUP_RESOURCE_READY) {
                                customWakeupResourceRetryByPresenter.remove(presenter)
                                val newArgs = chain.args.toTypedArray()
                                newArgs[0] = 1
                                log("custom wakeup PUS stale failure reconciled as ready")
                                return@intercept chain.proceed(newArgs)
                            }
                            if (customWakeupResourceRetryByPresenter.put(presenter, true) != true) {
                                Thread {
                                    try {
                                        Thread.sleep(CUSTOM_WAKEUP_RESOURCE_RETRY_DELAY_MILLIS)
                                        val refreshedState = queryCustomWakeupResourceState(param.classLoader)
                                        val methodName = if (refreshedState == CUSTOM_WAKEUP_RESOURCE_READY) "E" else "D"
                                        presenter.javaClass.getDeclaredMethod(methodName).apply {
                                            isAccessible = true
                                        }.invoke(presenter)
                                        log("custom wakeup PUS retry action=$methodName resourceState=$refreshedState")
                                    } catch (t: Throwable) {
                                        logError("custom wakeup PUS retry failed", t)
                                    }
                                }.start()
                                return@intercept null
                            }
                        }
                    }
                }
                chain.proceed()
            }
            log("custom wakeup PUS result hook installed")
        } catch (t: Throwable) {
            logError("hookCustomWakeupResourceDownload failed", t)
        }
    }

    private fun queryCustomWakeupResourceState(classLoader: ClassLoader): Int? {
        return runCatching {
            classLoader.loadClass("ut.m")
                .getDeclaredMethod("b")
                .apply { isAccessible = true }
                .invoke(null) as? Int
        }.getOrNull()
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookAgentEnrollmentFlow() {
        hookPhraseSelectionPresenter()
        hookRecordingLaunch()
        hookEnrollManagerConstructors()
        hookEnrollManagers()
        hookEnrollCallbacks()
        hookSentenceSpotterFlow()
        hookSentenceFinalAsrFlow()
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookEnrollManagerConstructors() {
        try {
            val cls = param.classLoader.loadClass("eh0.w")
            cls.declaredConstructors.forEach { ctor ->
                xposedModule.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    cacheSentenceManagerConfig(
                        chain.thisObject,
                        chain.args.getOrNull(4) as? String
                    )
                    log(
                        "SentenceEnrollManager.ctor " +
                            "langArg=${chain.args.getOrNull(3)} " +
                            "spotterKeywordArg=${chain.args.getOrNull(4)} " +
                            "lang=${extractStringField(chain.thisObject, "f21859d")} " +
                            "spotterKeyword=${extractStringField(chain.thisObject, "f21875u")} " +
                            "customText=${readWakeupText()}"
                    )
                    result
                }
            }
            log("SentenceEnrollManager constructor hook installed")
        } catch (t: Throwable) {
            logError("hookEnrollManagerConstructors failed", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookPhraseSelectionPresenter() {
        try {
            val cls = param.classLoader.loadClass("dk0.l")
            for (m in cls.declaredMethods) {
                if (m.name != "F" || !m.parameterTypes.contentEquals(arrayOf(String::class.java))) continue
                xposedModule.hook(m).intercept { chain ->
                    log("VoiceWakeupOptionsPresenter.select phrase=${chain.args.getOrNull(0)}")
                    val result = chain.proceed()
                    log("VoiceWakeupOptionsPresenter.select done phrase=${chain.args.getOrNull(0)}")
                    result
                }
            }
        } catch (t: Throwable) {
            logError("hookPhraseSelectionPresenter failed", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookRecordingLaunch() {
        try {
            val cls = param.classLoader.loadClass("dk0.f")
            for (m in cls.declaredMethods) {
                if (m.name != "H" || !m.parameterTypes.contentEquals(arrayOf(String::class.java))) continue
                xposedModule.hook(m).intercept { chain ->
                    log("VoiceWakeupOptionsFragment.launch recording WAKEUP_PHRASE=${chain.args.getOrNull(0)}")
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            logError("hookRecordingLaunch failed", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookEnrollManagers() {
        hookEnrollManagerClass("eh0.n", "CustomEnrollManager")
        hookEnrollManagerClass("eh0.w", "SentenceEnrollManager")
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookEnrollManagerClass(className: String, tag: String) {
        try {
            val cls = param.classLoader.loadClass(className)
            var hooked = 0
            for (m in cls.declaredMethods) {
                when {
                    m.name == "prepare" && m.parameterTypes.isEmpty() -> {
                        xposedModule.hook(m).intercept { chain ->
                            log("$tag.prepare before state=${extractState(chain.thisObject)} lang=${extractStringField(chain.thisObject, if (className == "eh0.n") "f21826d" else "f21859d")} keyword=${extractStringField(chain.thisObject, "f21875u")}")
                            val result = chain.proceed()
                            log("$tag.prepare after result=$result binded=${extractBooleanField(chain.thisObject, if (className == "eh0.n") "l" else "f21866k")}")
                            result
                        }
                        hooked++
                    }
                    m.name == "b" && m.parameterTypes.isEmpty() -> {
                        xposedModule.hook(m).intercept { chain ->
                            log("$tag.startRecordCustomKeyword state=${extractState(chain.thisObject)}")
                            chain.proceed()
                        }
                        hooked++
                    }
                    m.name == "f" && m.parameterTypes.isEmpty() -> {
                        xposedModule.hook(m).intercept { chain ->
                            log("$tag.startEnroll before customKeyword=${extractStringField(chain.thisObject, "f21830h")} g2p=${extractStringField(chain.thisObject, "f21843v")}")
                            val result = chain.proceed()
                            log("$tag.startEnroll after result=$result customKeyword=${extractStringField(chain.thisObject, "f21830h")} g2p=${extractStringField(chain.thisObject, "f21843v")}")
                            result
                        }
                        hooked++
                    }
                    (m.name == "i" || m.name == "e") && m.parameterTypes.isNotEmpty() -> {
                        xposedModule.hook(m).intercept { chain ->
                            if (className == "eh0.w") {
                                val expectedText = chain.args.getOrNull(2) as? String
                                cacheSentenceExpectedText(chain.thisObject, expectedText)
                            }
                            log("$tag.setNext ${m.name} args=${chain.args.joinToString()}")
                            chain.proceed()
                        }
                        hooked++
                    }
                    m.name == "l" && m.parameterTypes.isEmpty() -> {
                        xposedModule.hook(m).intercept { chain ->
                            sentenceEndpointStopArmedByManager.remove(chain.thisObject)
                            log("$tag.startRecording audioSrc=${if (className == "eh0.n") "CUSTOM_ENROLL" else "SENTENCE_ENROLL"} spotterKeyword=${extractStringField(chain.thisObject, "f21875u")} spotterCompleted=${extractBooleanField(chain.thisObject, "f21873s")} spotterBundle=${safeToString(extractAnyField(chain.thisObject, "f21874t"))}")
                            chain.proceed()
                        }
                        hooked++
                    }
                }
            }
            log("$tag manager hooks installed class=$className count=$hooked")
        } catch (t: Throwable) {
            logError("hookEnrollManagerClass failed for $className", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookSentenceSpotterFlow() {
        try {
            val listenerCls = param.classLoader.loadClass("eh0.u")
            val onTransact = listenerCls.getDeclaredMethod(
                "onTransact",
                Int::class.javaPrimitiveType,
                android.os.Parcel::class.java,
                android.os.Parcel::class.java,
                Int::class.javaPrimitiveType
            )
            xposedModule.hook(onTransact).intercept { chain ->
                val result = chain.proceed()
                val manager = extractSpotterManager(chain.thisObject)
                val directBundle = readSpotterResultBundle(chain.args.getOrNull(1))
                val normalizedBundle = normalizeAgentSpotterResult(manager, directBundle)
                if (manager != null && normalizedBundle != null) {
                    cacheSpotterResult(manager, normalizedBundle)
                    maybeArmSentenceEndpointStop(manager, normalizedBundle, "spotter")
                }
                log(
                    "SentenceSpotterListener.onTransact code=${chain.args.getOrNull(0)} " +
                        "completed=${extractBooleanField(manager, "f21873s")} " +
                        "cachedCompleted=${extractCachedSpotterCompleted(manager)} " +
                        "keyword=${extractManagerSpotterKeyword(manager)} " +
                        "bundle=${safeToString(extractAnyField(manager, "f21874t"))} " +
                        "cachedBundle=${safeToString(extractCachedSpotterBundle(manager))} " +
                        "directBundle=${safeToString(directBundle)} " +
                        "normalizedBundle=${safeToString(normalizedBundle)}"
                )
                result
            }
            log("SentenceSpotterListener hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceSpotterFlow listener failed", t)
        }
        try {
            val compareCls = param.classLoader.loadClass("com.samsung.android.imagetranslation.util.p")
            val accept = compareCls.getDeclaredMethod("accept", Any::class.java)
            xposedModule.hook(accept).intercept { chain ->
                val manager = extractCompareManager(chain.thisObject)
                if (manager != null) {
                    val expected = extractCompareExpected(chain.thisObject)
                    val pos = extractComparePosition(chain.thisObject)
                    val normalized = normalizeAgentSpotterResult(manager, extractCachedSpotterBundle(manager))
                    if (normalized != null) {
                        cacheSpotterResult(manager, normalized)
                    }
                    log(
                        "SentenceSpotterCompare.before pos=$pos expected=$expected " +
                            "spotterCompleted=${extractBooleanField(manager, "f21873s")} " +
                            "cachedCompleted=${extractCachedSpotterCompleted(manager)} " +
                            "keyword=${extractManagerSpotterKeyword(manager)} " +
                            "bundle=${safeToString(extractAnyField(manager, "f21874t"))} " +
                            "cachedBundle=${safeToString(extractCachedSpotterBundle(manager))} " +
                            "asr=${safeToString(chain.args.getOrNull(0))}"
                    )
                }
                val result = chain.proceed()
                if (manager != null) {
                    log(
                        "SentenceSpotterCompare.after spotterCompleted=${extractBooleanField(manager, "f21873s")} " +
                            "cachedCompleted=${extractCachedSpotterCompleted(manager)} " +
                            "bundle=${safeToString(extractAnyField(manager, "f21874t"))} " +
                            "cachedBundle=${safeToString(extractCachedSpotterBundle(manager))}"
                    )
                }
                result
            }
            log("SentenceSpotterCompare hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceSpotterFlow compare failed", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookSentenceFinalAsrFlow() {
        try {
            val runnerCls = param.classLoader.loadClass("com.samsung.phoebus.audio.output.c0")
            val run = runnerCls.getDeclaredMethod("run")
            xposedModule.hook(run).intercept { chain ->
                val caseId = extractIntField(chain.thisObject, "f17674a")
                if (caseId == 4) {
                    val manager = extractAnyField(chain.thisObject, "f17675b")
                    if (manager != null) {
                        log(
                            "SentenceFinalAsr.run case=$caseId " +
                                "manager=${manager.javaClass.name} " +
                                "keyword=${extractManagerSpotterKeyword(manager)} " +
                                "expected=${extractExpectedSentenceText(manager)} " +
                                "consumer=${extractAnyField(chain.thisObject, "f17677d")?.javaClass?.name}"
                        )
                        if (isCustomSentenceManager(manager)) {
                            val originalConsumer = extractAnyField(chain.thisObject, "f17677d") as? java.util.function.Consumer<Any?>
                            if (originalConsumer != null) {
                                val wrapper = java.util.function.Consumer<Any?> { obj ->
                                    val finalText = extractFinalAsrText(obj)
                                    val expectedText = extractExpectedSentenceText(manager)
                                    val normalizedBundle = normalizeAgentSpotterResult(manager, extractCachedSpotterBundle(manager))
                                    val submitted = submitSentenceEnrollFromFinalAsr(manager, expectedText, finalText, normalizedBundle)
                                    log(
                                        "SentenceFinalAsr.consumer " +
                                            "expected=$expectedText final=$finalText " +
                                            "bundle=${safeToString(normalizedBundle)} submitted=$submitted"
                                    )
                                    if (!submitted) {
                                        originalConsumer.accept(obj)
                                    }
                                }
                                findFieldRecursive(chain.thisObject.javaClass, "f17677d")?.apply {
                                    isAccessible = true
                                    set(chain.thisObject, wrapper)
                                }
                                log(
                                    "SentenceFinalAsr.wrap consumer=${originalConsumer.javaClass.name} " +
                                        "expected=${extractExpectedSentenceText(manager)}"
                                )
                            }
                        }
                    }
                }
                chain.proceed()
            }
            log("SentenceFinalAsr hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceFinalAsrFlow failed", t)
        }
        try {
            val enrollProxyCls = param.classLoader.loadClass("no0.b")
            val submit = enrollProxyCls.getDeclaredMethod(
                "o",
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                android.os.Bundle::class.java
            )
            xposedModule.hook(submit).intercept { chain ->
                log(
                    "EnrollmentService.setNextWithText " +
                        "sessionId=${chain.args.getOrNull(0)} " +
                        "expected=${chain.args.getOrNull(1)} " +
                        "asr=${chain.args.getOrNull(2)} " +
                        "bundle=${safeToString(chain.args.getOrNull(3))}"
                )
                chain.proceed()
            }
            log("EnrollmentService setNextWithText hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceFinalAsrFlow enrollment proxy failed", t)
        }
        try {
            val dispatcherCls = param.classLoader.loadClass("a51.o")
            val asrResultCls = param.classLoader.loadClass("xp0.c")
            val dispatch = dispatcherCls.getDeclaredMethod("s", asrResultCls)
            xposedModule.hook(dispatch).intercept { chain ->
                val recognizer = extractAnyField(chain.thisObject, "f780b")
                if (recognizer != null && recognizer.javaClass.name == "eh0.c0") {
                    val finalListener = extractAnyField(recognizer, "f21769g")
                    val partialListener = extractAnyField(recognizer, "f21768f")
                    log(
                        "SentenceAsrDispatcher.s " +
                            "final=${extractBooleanField(chain.args.getOrNull(0), "f63648a")} " +
                            "result=${safeToString(chain.args.getOrNull(0))} " +
                            "finalListener=${finalListener?.javaClass?.name} " +
                            "partialListener=${partialListener?.javaClass?.name}"
                    )
                }
                chain.proceed()
            }
            log("SentenceAsrDispatcher hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceFinalAsrFlow dispatcher failed", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookWakeupSpotterFlow() {
        try {
            val cls = param.classLoader.loadClass("com.samsung.android.voicewakeup.audiorecord.SpotterService")
            val onStartCommand = cls.getDeclaredMethod(
                "onStartCommand",
                android.content.Intent::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            xposedModule.hook(onStartCommand).intercept { chain ->
                val intent = chain.args.getOrNull(0) as? android.content.Intent
                currentWakeupSpotterKeyword = intent?.getStringExtra("spotterKeyword")
                lastWakeupSpotterBundle = null
                log(
                    "SpotterService.onStartCommand " +
                        "intent=${intentSummary(intent)} " +
                        "customText=${readWakeupText()} " +
                        "fieldKeyword=${extractStringField(chain.thisObject, "F")}"
                )
                val result = chain.proceed()
                log(
                    "SpotterService.onStartCommand.after " +
                        "fieldKeyword=${extractStringField(chain.thisObject, "F")} " +
                        "bundle=${safeToString(extractAnyField(chain.thisObject, "E"))}"
                )
                result
            }
            val getSpotter = cls.getDeclaredMethod("a")
            xposedModule.hook(getSpotter).intercept { chain ->
                log(
                    "SpotterService.getSpotter.before " +
                        "fieldKeyword=${extractStringField(chain.thisObject, "F")} " +
                        "customText=${readWakeupText()}"
                )
                val defaultResult = chain.proceed()
                val keyword = extractStringField(chain.thisObject, "F")
                val finalResult = if (keyword == "custom") {
                    val replacement = buildCustomSentenceSpotter(chain.thisObject, param.classLoader)
                    if (replacement != null) {
                        log("SpotterService.getSpotter replaced default verifier for custom keyword")
                        replacement
                    } else {
                        defaultResult
                    }
                } else {
                    defaultResult
                }
                log(
                    "SpotterService.getSpotter.after " +
                        "fieldKeyword=${extractStringField(chain.thisObject, "F")} " +
                        "spotter=${finalResult?.javaClass?.name}"
                )
                finalResult
            }
            log("SpotterService hooks installed")
        } catch (t: Throwable) {
            logError("hookWakeupSpotterFlow service failed", t)
        }
        try {
            val listenerProxyCls = param.classLoader.loadClass("com.samsung.android.bixby.wakeup.x")
            val deliver = listenerProxyCls.getDeclaredMethod("t", android.os.Bundle::class.java)
            xposedModule.hook(deliver).intercept { chain ->
                val original = chain.args.getOrNull(0) as? android.os.Bundle
                if (original != null) {
                    val normalized = normalizeWakeupResultBundle(original)
                    log(
                        "SpotterResultProxy.t " +
                            "keyword=$currentWakeupSpotterKeyword " +
                            "original=${safeToString(original)} " +
                            "normalized=${safeToString(normalized)}"
                    )
                    if (normalized !== original) {
                        val args = chain.args.toTypedArray()
                        args[0] = normalized
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
            }
            log("SpotterResultProxy hook installed")
        } catch (t: Throwable) {
            logError("hookWakeupSpotterFlow proxy failed", t)
        }
        try {
            val callbackCls = param.classLoader.loadClass("pc.e")
            val z0Cls = param.classLoader.loadClass("gd.z0")
            for (methodName in arrayOf("a", "b", "c")) {
                val m = callbackCls.getDeclaredMethod(methodName, z0Cls)
                xposedModule.hook(m).intercept { chain ->
                    val service = extractSpotterServiceFromCallback(chain.thisObject)
                    if (service != null) {
                        log(
                            "SpotterCallback.$methodName " +
                                "fieldKeyword=${extractStringField(service, "F")} " +
                                "bundle=${safeToString(extractAnyField(service, "E"))} " +
                                "result=${safeToString(chain.args.getOrNull(0))}"
                        )
                    }
                    chain.proceed()
                }
            }
            log("SpotterCallback hooks installed")
        } catch (t: Throwable) {
            logError("hookWakeupSpotterFlow callback failed", t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookEnrollCallbacks() {
        try {
            val customAsrCls = param.classLoader.loadClass("eh0.d")
            val accept = customAsrCls.getDeclaredMethod("accept", Any::class.java)
            xposedModule.hook(accept).intercept { chain ->
                log("CustomEnrollManager.accept case=${extractIntField(chain.thisObject, "f21775a")} obj=${safeToString(chain.args.getOrNull(0))}")
                chain.proceed()
            }
            log("CustomEnrollManager callback hook installed")
        } catch (t: Throwable) {
            logError("hookEnrollCallbacks custom failed", t)
        }
        try {
            val sentenceAsrCls = param.classLoader.loadClass("eh0.t")
            val accept = sentenceAsrCls.getDeclaredMethod("accept", Any::class.java)
            xposedModule.hook(accept).intercept { chain ->
                val manager = extractSentenceAcceptManager(chain.thisObject)
                val partialText = extractFinalAsrText(chain.args.getOrNull(0))
                val partialRawText = extractRawAsrText(chain.args.getOrNull(0))
                maybeArmSentenceEndpointStop(manager, extractCachedSpotterBundle(manager), "partial")
                log(
                    "SentenceEnrollManager.accept case=${extractIntField(chain.thisObject, "f21853a")} " +
                        "obj=${safeToString(chain.args.getOrNull(0))} " +
                        "text=$partialText raw=$partialRawText " +
                        "spotterCompleted=${extractBooleanField(manager, "f21873s")} " +
                        "cachedCompleted=${extractCachedSpotterCompleted(manager)} " +
                        "bundle=${safeToString(extractAnyField(manager, "f21874t"))} " +
                        "cachedBundle=${safeToString(extractCachedSpotterBundle(manager))}"
                )
                chain.proceed()
            }
            log("SentenceEnrollManager callback hook installed")
        } catch (t: Throwable) {
            logError("hookEnrollCallbacks sentence failed", t)
        }
        try {
            val vmCls = param.classLoader.loadClass("ek0.o")
            for (m in vmCls.declaredMethods) {
                when {
                    m.name == "g" && m.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, String::class.java)) -> {
                        xposedModule.hook(m).intercept { chain ->
                            log("EnrollView callback onCustomKeywordReceived code=${chain.args.getOrNull(0)} text=${chain.args.getOrNull(1)} mode=${extractIntField(chain.thisObject, "f22007a")}")
                            chain.proceed()
                        }
                    }
                    m.name == "f" && m.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) -> {
                        xposedModule.hook(m).intercept { chain ->
                            log("EnrollView callback onEnrollResult status=${chain.args.getOrNull(0)} step=${chain.args.getOrNull(1)} result=${chain.args.getOrNull(2)} mode=${extractIntField(chain.thisObject, "f22007a")}")
                            chain.proceed()
                        }
                    }
                }
            }
            log("EnrollView callback hooks installed")
        } catch (t: Throwable) {
            logError("hookEnrollCallbacks viewmodel failed", t)
        }
    }

    private fun extractState(target: Any?): String? {
        return extractAnyField(target, "f21844w")?.toString()
            ?: extractAnyField(target, "f21877w")?.toString()
    }

    private fun extractIntField(target: Any?, fieldName: String): Int? {
        return try {
            if (target == null) return null
            findFieldRecursive(target.javaClass, fieldName)?.apply { isAccessible = true }?.getInt(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractAnyField(target: Any?, fieldName: String): Any? {
        return try {
            if (target == null) return null
            findFieldRecursive(target.javaClass, fieldName)?.apply { isAccessible = true }?.get(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeToString(value: Any?): String {
        return try {
            when (value) {
                null -> "null"
                is android.os.Bundle -> bundleSummary(value)
                else -> value.toString()
            }
        } catch (_: Throwable) {
            "<toString failed>"
        }
    }

    private fun bundleSummary(bundle: android.os.Bundle): String {
        return try {
            bundle.keySet()
                .sorted()
                .joinToString(prefix = "Bundle{", postfix = "}") { key ->
                    "$key=${safeToString(bundle.get(key))}"
                }
        } catch (_: Throwable) {
            "Bundle{<read failed>}"
        }
    }

    private fun intentSummary(intent: android.content.Intent?): String {
        if (intent == null) return "null"
        val extras = try { intent.extras } catch (_: Throwable) { null }
        return buildString {
            append("Intent{action=")
            append(intent.action)
            append(", package=")
            append(intent.`package`)
            append(", extras=")
            append(if (extras != null) bundleSummary(extras) else "null")
            append("}")
        }
    }

    private fun buildCustomSentenceSpotter(service: Any, classLoader: ClassLoader): Any? {
        return try {
            val o1Cls = classLoader.loadClass("gd.o1")
            val o1 = o1Cls.getDeclaredField("J").apply { isAccessible = true }.get(null)
            val featureEnabled = o1Cls.getDeclaredMethod("a").invoke(o1) as Boolean
            val sensitivity = o1Cls.getDeclaredMethod("h").invoke(o1) as Int
            val locale = o1Cls.getDeclaredMethod("n").invoke(o1) as Locale

            val r0Cls = classLoader.loadClass("gd.r0")
            val q0 = r0Cls.getDeclaredMethod("d", String::class.java).invoke(null, "custom")

            val nbdCls = classLoader.loadClass("nb.d")
            val config = nbdCls.getConstructor(
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Locale::class.java,
                classLoader.loadClass("gd.q0")
            ).newInstance(featureEnabled, sensitivity, locale, q0)

            val gdLCls = classLoader.loadClass("gd.l")
            val postVerifier = gdLCls.getConstructor(
                Context::class.java,
                classLoader.loadClass("sb.i"),
                Int::class.javaPrimitiveType
            ).newInstance(service as Context, config, 0)

            val obECls = classLoader.loadClass("ob.e")
            val spotter = obECls.getConstructor(
                classLoader.loadClass("sb.i"),
                classLoader.loadClass("qb.a")
            ).newInstance(config, postVerifier)
            val listener = extractAnyField(service, "H")
            val listenerAttachResult = attachSpotterListener(spotter, listener)

            log(
                "buildCustomSentenceSpotter created " +
                    "locale=$locale sensitivity=$sensitivity featureEnabled=$featureEnabled " +
                    "spotter=${spotter?.javaClass?.name} listener=${safeToString(listener)} attach=$listenerAttachResult"
            )
            spotter
        } catch (t: Throwable) {
            logError("buildCustomSentenceSpotter failed", t)
            null
        }
    }

    private fun extractStringField(target: Any?, fieldName: String): String? {
        return try {
            if (target == null) return null
            findFieldRecursive(target.javaClass, fieldName)?.apply { isAccessible = true }?.get(target) as? String
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractBooleanField(target: Any?, fieldName: String): Boolean? {
        return try {
            if (target == null) return null
            findFieldRecursive(target.javaClass, fieldName)?.apply { isAccessible = true }?.get(target) as? Boolean
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractLocaleField(target: Any?, fieldName: String): Locale? {
        return try {
            if (target == null) return null
            findFieldRecursive(target.javaClass, fieldName)?.apply { isAccessible = true }?.get(target) as? Locale
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractAnyFieldByTypeName(target: Any?, vararg typeNames: String): Any? {
        return try {
            if (target == null) return null
            val wanted = typeNames.toSet()
            findFieldByPredicateRecursive(target.javaClass) { field ->
                wanted.contains(field.type.name)
            }?.apply { isAccessible = true }?.get(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractStringFieldByExcludingKnown(target: Any?, knownFieldNames: Set<String>): String? {
        return try {
            if (target == null) return null
            findFieldByPredicateRecursive(target.javaClass) { field ->
                field.type == String::class.java && !knownFieldNames.contains(field.name)
            }?.apply { isAccessible = true }?.get(target) as? String
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractSpotterManager(target: Any?): Any? {
        return extractAnyField(target, "f21855g")
            ?: extractAnyFieldByTypeName(target, "eh0.w", "eh0.n")
    }

    private fun cacheSentenceManagerConfig(manager: Any?, spotterKeyword: String?) {
        if (manager == null) return
        if (spotterKeyword != null) {
            sentenceManagerSpotterKeywordByManager[manager] = spotterKeyword
        }
    }

    private fun cacheSentenceExpectedText(manager: Any?, expectedText: String?) {
        if (manager == null || expectedText.isNullOrBlank()) return
        sentenceExpectedTextByManager[manager] = expectedText
    }

    private fun extractExpectedSentenceText(manager: Any?): String? {
        if (manager == null) return null
        return sentenceExpectedTextByManager[manager]
    }

    private fun extractManagerSpotterKeyword(manager: Any?): String? {
        if (manager == null) return null
        return extractStringField(manager, "f21875u")
            ?: sentenceManagerSpotterKeywordByManager[manager]
    }

    private fun isCustomSentenceManager(manager: Any?): Boolean {
        return extractManagerSpotterKeyword(manager) == "custom"
    }

    private fun extractCompareManager(target: Any?): Any? {
        return extractAnyField(target, "f16529c")
            ?: extractAnyFieldByTypeName(target, "eh0.w", "eh0.n")
    }

    private fun extractCompareExpected(target: Any?): Any? {
        return extractAnyField(target, "f16530d")
            ?: extractStringFieldByExcludingKnown(target, setOf("f16527a"))
    }

    private fun extractComparePosition(target: Any?): Int? {
        return extractIntField(target, "f16528b")
    }

    private fun extractSentenceAcceptManager(target: Any?): Any? {
        return extractAnyField(target, "f21854b")
            ?: extractAnyFieldByTypeName(target, "eh0.w")
    }

    private fun extractSpotterServiceFromCallback(target: Any?): Any? {
        return extractAnyField(target, "f6065b")
            ?: extractAnyFieldByTypeName(target, "com.samsung.android.voicewakeup.audiorecord.SpotterService")
    }

    private fun attachSpotterListener(spotter: Any?, listener: Any?): String {
        if (spotter == null) return "spotter-null"
        if (listener == null) return "listener-null"

        val directField = findFieldRecursive(spotter.javaClass, "f5712g")
        if (directField != null) {
            return runCatching {
                directField.isAccessible = true
                directField.set(spotter, listener)
                "field=${directField.declaringClass.name}.${directField.name}"
            }.getOrElse {
                logError("attachSpotterListener direct field failed", it)
                "direct-failed"
            }
        }

        val listenerField = findFieldByPredicateRecursive(spotter.javaClass) { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.isAssignableFrom(listener.javaClass)
        }
        if (listenerField != null) {
            return runCatching {
                listenerField.isAccessible = true
                listenerField.set(spotter, listener)
                "assignable=${listenerField.declaringClass.name}.${listenerField.name}"
            }.getOrElse {
                logError("attachSpotterListener assignable field failed", it)
                "assignable-failed"
            }
        }

        val fieldSummary = describeFields(spotter.javaClass)
        log("attachSpotterListener no field spotter=${spotter.javaClass.name} fields=$fieldSummary")
        return "missing"
    }

    private fun extractFinalAsrText(obj: Any?): String? {
        if (obj == null) return null
        return runCatching {
            obj.javaClass.methods.firstOrNull { method ->
                method.name == "getText" && method.parameterTypes.isEmpty()
            }?.invoke(obj) as? String
        }.getOrNull() ?: safeToString(obj).takeIf { it != "null" }
    }

    private fun extractRawAsrText(obj: Any?): String? {
        if (obj == null) return null
        return runCatching {
            obj.javaClass.methods.firstOrNull { method ->
                method.name == "getRawText" && method.parameterTypes.isEmpty()
            }?.invoke(obj) as? String
        }.getOrNull()
    }

    private fun maybeArmSentenceEndpointStop(
        manager: Any?,
        bundle: android.os.Bundle?,
        source: String
    ) {
        if (manager == null || bundle == null || !isCustomSentenceManager(manager)) return
        if (!bundle.getBoolean("isSpottedComplete") || !bundle.getBoolean("isSpotted")) return
        if (sentenceEndpointStopArmedByManager[manager] == true) return
        val audioSessionControl = extractAnyField(manager, "l") ?: return
        runCatching {
            audioSessionControl.javaClass.getMethod(
                "stopAfterEndPointDetected",
                Boolean::class.javaPrimitiveType
            ).invoke(audioSessionControl, true)
            sentenceEndpointStopArmedByManager[manager] = true
            log(
                "SentenceEnrollManager.armStopAfterEndpoint " +
                    "source=$source expected=${extractExpectedSentenceText(manager)} " +
                    "bundle=${safeToString(bundle)}"
            )
        }.onFailure {
            logError("maybeArmSentenceEndpointStop failed", it)
        }
    }

    private fun submitSentenceEnrollFromFinalAsr(
        manager: Any,
        expectedText: String?,
        finalText: String?,
        bundle: android.os.Bundle?
    ): Boolean {
        if (expectedText.isNullOrBlank() || finalText.isNullOrBlank()) return false
        val enrollService = extractAnyField(manager, "f21863h") ?: return false
        val sessionId = resolveSentenceSessionId(manager) ?: return false
        val payload = android.os.Bundle(bundle ?: android.os.Bundle())
        if (!payload.containsKey("startPoint")) {
            payload.putInt("startPoint", 0)
        }
        ensureSentenceSessionMapping(manager, sessionId)
        return runCatching {
            enrollService.javaClass.getDeclaredMethod(
                "o",
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                android.os.Bundle::class.java
            ).invoke(enrollService, sessionId, expectedText, finalText, payload)
            resetSentenceSpotterState(manager)
            true
        }.getOrElse {
            logError("submitSentenceEnrollFromFinalAsr failed", it)
            false
        }
    }

    private fun resolveSentenceSessionId(manager: Any): Int? {
        val existing = extractIntField(manager, "f21871q")
        if (existing != null && existing != 0) return existing
        val audioSessionControl = extractAnyField(manager, "l") ?: return existing
        val sessionId = runCatching {
            val audioParams = audioSessionControl.javaClass.getMethod("getAudioParams").invoke(audioSessionControl)
            val configuration = audioParams?.javaClass?.getMethod("getConfiguration")?.invoke(audioParams)
            configuration?.javaClass?.getMethod("getAudioSessionId")?.invoke(configuration) as? Int
        }.getOrNull()
        if (sessionId != null && sessionId != 0) {
            runCatching {
                findFieldRecursive(manager.javaClass, "f21871q")?.apply {
                    isAccessible = true
                    setInt(manager, sessionId)
                }
            }
        }
        return sessionId ?: existing
    }

    private fun ensureSentenceSessionMapping(manager: Any, sessionId: Int) {
        val currentIndex = extractIntField(manager, "f21870p") ?: return
        val map = extractAnyField(manager, "f21872r") as? MutableMap<Any, Any> ?: return
        map[sessionId] = currentIndex
    }

    private fun resetSentenceSpotterState(manager: Any) {
        runCatching {
            findFieldRecursive(manager.javaClass, "f21873s")?.apply {
                isAccessible = true
                setBoolean(manager, false)
            }
            findFieldRecursive(manager.javaClass, "f21874t")?.apply {
                isAccessible = true
                set(manager, null)
            }
        }
        lastSpotterCompletedByManager[manager] = false
        lastSpotterBundleByManager.remove(manager)
        sentenceEndpointStopArmedByManager.remove(manager)
    }

    private fun normalizeWakeupResultBundle(bundle: android.os.Bundle): android.os.Bundle {
        val merged = android.os.Bundle(lastWakeupSpotterBundle ?: android.os.Bundle())
        merged.putAll(bundle)
        val normalized = android.os.Bundle(merged)
        lastWakeupSpotterBundle = android.os.Bundle(normalized)
        return normalized
    }

    private fun normalizeAgentSpotterResult(manager: Any?, bundle: android.os.Bundle?): android.os.Bundle? {
        if (bundle == null) return null
        val merged = android.os.Bundle(extractCachedSpotterBundle(manager) ?: android.os.Bundle())
        merged.putAll(bundle)
        return merged
    }

    private fun findFieldRecursive(clazz: Class<*>?, fieldName: String): java.lang.reflect.Field? {
        var current = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun findFieldByPredicateRecursive(
        clazz: Class<*>?,
        predicate: (java.lang.reflect.Field) -> Boolean
    ): java.lang.reflect.Field? {
        var current = clazz
        while (current != null) {
            current.declaredFields.firstOrNull(predicate)?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun describeFields(clazz: Class<*>?): String {
        val parts = mutableListOf<String>()
        var current = clazz
        while (current != null) {
            current.declaredFields.forEach { field ->
                parts += "${current.name}.${field.name}:${field.type.name}"
            }
            current = current.superclass
        }
        return parts.joinToString(prefix = "[", postfix = "]")
    }

    private fun readSpotterResultBundle(parcelArg: Any?): android.os.Bundle? {
        val parcel = parcelArg as? android.os.Parcel ?: return null
        return runCatching {
            val bytes = parcel.marshall()
            val copy = android.os.Parcel.obtain()
            try {
                copy.unmarshall(bytes, 0, bytes.size)
                copy.setDataPosition(0)
                copy.enforceInterface("com.samsung.android.bixby.wakeup.ISpotterResultListener")
                if (copy.readInt() == 0) return@runCatching null
                android.os.Bundle.CREATOR.createFromParcel(copy)
            } finally {
                copy.recycle()
            }
        }.getOrNull()
    }

    private fun cacheSpotterResult(manager: Any, bundle: android.os.Bundle) {
        lastSpotterBundleByManager[manager] = android.os.Bundle(bundle)
        lastSpotterCompletedByManager[manager] = bundle.getBoolean("isSpottedComplete")
    }

    private fun extractCachedSpotterBundle(manager: Any?): android.os.Bundle? {
        if (manager == null) return null
        return lastSpotterBundleByManager[manager]
    }

    private fun extractCachedSpotterCompleted(manager: Any?): Boolean? {
        if (manager == null) return null
        return lastSpotterCompletedByManager[manager]
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

        val text = try {
            val dir = File(BIXBY_WAKEUP_SHARED_PREFERENCES_PATH)
            if (!dir.exists() || !dir.isDirectory) {
                log("wakeup text dir missing: $BIXBY_WAKEUP_SHARED_PREFERENCES_PATH")
                ""
            } else {
                var result = ""
                for (f in dir.listFiles() ?: emptyArray()) {
                    if (!f.name.endsWith(".xml")) continue
                    val match = WAKEUP_TEXT_REGEX.find(f.readText())
                    if (match != null) {
                        result = match.groupValues[1]
                        log("read wakeup text from ${f.name} value=$result")
                        break
                    }
                }
                result
            }
        } catch (t: Throwable) {
            logError("readWakeupText failed", t)
            ""
        }

        updateWakeupTextCache(text, now)
        return text
    }

    private fun updateWakeupTextCache(text: String, now: Long = SystemClock.elapsedRealtime()) {
        if (cachedWakeupText != text) {
            log("wakeup text cache updated value=$text")
        }
        cachedWakeupText = text
        cachedWakeupTextTimeMillis = now
    }

    private const val CUSTOM_WAKEUP_TEXT_KEY = "myvoice_string_custom"
    private const val CUSTOM_WAKEUP_RESOURCE_READY = 100
    private const val CUSTOM_WAKEUP_RESOURCE_RETRY_DELAY_MILLIS = 1_500L
    private const val WAKEUP_TEXT_CACHE_TTL_MILLIS = 5_000L
    private const val BIXBY_WAKEUP_SHARED_PREFERENCES_PATH =
        "/data/data/com.samsung.android.bixby.wakeup/shared_prefs"
    private val WAKEUP_TEXT_REGEX = Regex("<string name=\"$CUSTOM_WAKEUP_TEXT_KEY\">(.*?)</string>")
    private val lastSpotterBundleByManager =
        Collections.synchronizedMap(WeakHashMap<Any, android.os.Bundle>())
    private val lastSpotterCompletedByManager =
        Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val sentenceExpectedTextByManager =
        Collections.synchronizedMap(WeakHashMap<Any, String>())
    private val sentenceManagerSpotterKeywordByManager =
        Collections.synchronizedMap(WeakHashMap<Any, String>())
    private val sentenceEndpointStopArmedByManager =
        Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val customWakeupResourceRetryByPresenter =
        Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    @Volatile
    private var wakeupTextProviderReader: (() -> String)? = null
    @Volatile
    private var cachedWakeupText: String? = null
    @Volatile
    private var cachedWakeupTextTimeMillis: Long = 0
    @Volatile
    private var currentWakeupSpotterKeyword: String? = null
    @Volatile
    private var lastWakeupSpotterBundle: android.os.Bundle? = null
}
