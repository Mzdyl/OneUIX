package io.github.soclear.oneuix.hook

import android.content.Context
import android.os.Build
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.DebugFileLogger
import java.io.File
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

object Bixby {

    private fun log(msg: String) {
        if (DebugFileLogger.isEnabled) XposedBridge.log("[OneUIX-Bixby] $msg")
        DebugFileLogger.log("Bixby", msg)
    }

    private fun logError(msg: String, t: Throwable? = null) {
        XposedBridge.log("[OneUIX-Bixby] $msg")
        if (t != null) XposedBridge.log(t)
        DebugFileLogger.logError("Bixby", msg, t)
    }

    fun init(lpparam: LoadPackageParam, p: Preference.Bixby) {
        DebugFileLogger.attachToProcess(lpparam)
        when (lpparam.packageName) {
            Package.BIXBY_AGENT  -> initBixbyAgent(lpparam, p)
            Package.BIXBY_WAKEUP -> initBixbyWakeup(lpparam, p)
        }
    }

    private fun initBixbyAgent(lpparam: LoadPackageParam, p: Preference.Bixby) {
        log("Init offline=${p.injectModel} customWakeup=${p.labsMgr} wwv=${p.wwvBypass}")
        if (p.injectModel) hookInjectModel(lpparam)
        if (p.labsMgr) {
            hookLabsFeatureManager(lpparam)
            hookWakeupKeywordTypeBridge(lpparam)
            hookAgentCustomPhraseBridge(lpparam)
            hookCustomWakeupResourceDownload(lpparam)
            hookAgentEnrollmentFlow(lpparam)
        }
        if (p.wwvBypass)  hookWakeupWordValidator(lpparam)
    }

    private fun initBixbyWakeup(lpparam: LoadPackageParam, p: Preference.Bixby) {
        if (p.labsMgr) {
            hookWakeupCustomPhrase(lpparam)
            hookCustomWakeupTrainers(lpparam)
            hookWakeupSpotterFlow(lpparam)
        }
        if (p.wwvBypass) {
            hookWakeupWordTypeValidator(lpparam)
            hookKwdAsianTextFix(lpparam)
        }
    }

    // ═══════ injectModel: 注入 Build.MODEL 到设备白名单缓存 ═══════

    private fun hookInjectModel(lpparam: LoadPackageParam) {
        findAndHookMethod("android.app.SharedPreferencesImpl", lpparam.classLoader,
            "getString", String::class.java, String::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    if (p.args[0] == "pref_key_on_device_config_cache") {
                        val orig = p.result as? String ?: p.args[1] as? String ?: ""
                        if (!orig.contains(Build.MODEL)) {
                            p.result = if (orig.isEmpty()) Build.MODEL else "$orig,${Build.MODEL}"
                            log("injectModel matched cache='$orig' result='${p.result}'")
                        }
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
                        if (mp.args[0] == "labs_custom_wakeup") {
                            mp.result = true
                            log("labsMgr forced $name(${mp.args[0]})=true")
                        }
                    }
                })
            }
            findAndHookMethod(c, "isLabsMenuSupported", object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    p.result = true
                    log("labsMgr forced isLabsMenuSupported=true")
                }
            })
        } catch (t: Throwable) {
            logError("hookLabsFeatureManager failed", t)
        }
    }

    // ═══════ wwvBypass: 绕过原生库唤醒词黑名单（竞品词/脏话/政治等） ═══════
    // 签名匹配而非硬编码方法名，兼容不同 Bixby 版本

    private fun hookWakeupWordValidator(lpparam: LoadPackageParam) {
        try {
            val cls = lpparam.classLoader.loadClass(
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
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = true
                            log(
                                "wwvBypass forced length validator ${m.name} " +
                                    "locale=${p.args[0]} keyword=${p.args[1]}"
                            )
                        }
                    })
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
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = 0
                            log(
                                "wwvBypass forced blacklist validator ${m.name} " +
                                    "keyword=${p.args[1]} locale=${p.args[2]}"
                            )
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            logError("hookWakeupWordValidator failed", t)
        }
    }

    // ═══════ wakeup: 绕过 KWV isVaildWordType 的 locale 限制 ═══════
    // zhCN 等非韩语 locale 直接返回 false，导致 TEXT_CUSTOM 训练失败
    // 三个 decoder 变体 (normal/bargein/acousticecho) 均有同名方法

    private fun hookWakeupWordTypeValidator(lpparam: LoadPackageParam) {
        for (cn in arrayOf(
            "com.samsung.voicewakeup.kwv.normal.custom.WakeupKwvNormalCommon",
            "com.samsung.voicewakeup.kwv.bargein.custom.WakeupKwvBargeinCommon",
            "com.samsung.voicewakeup.kwv.acousticecho.custom.WakeupKwvAcousticEchoCommon")) {
            try {
                val cls = lpparam.classLoader.loadClass(cn)
                var hooked = 0
                for (m in cls.declaredMethods) {
                    if (m.returnType != Boolean::class.javaPrimitiveType) continue
                    if (!m.parameterTypes.contentEquals(arrayOf(String::class.java, Locale::class.java))) continue
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            p.result = true
                            log("kwv locale bypass $cn.${m.name} text=${p.args[0]} locale=${p.args[1]}")
                        }
                    })
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

    private fun hookKwdAsianTextFix(lpparam: LoadPackageParam) {
        for (kn in arrayOf(
            "com.samsung.voicewakeup.kwd.normal.custom.WakeupKwdNormalCustom",
            "com.samsung.voicewakeup.kwd.bargein.custom.WakeupKwdBargeinCustom",
            "com.samsung.voicewakeup.kwd.acousticecho.custom.WakeupKwdAcousticEchoCustom")) {
            try {
                val cls = lpparam.classLoader.loadClass(kn)
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
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val ret = p.result as? Int ?: return
                            if (ret != 0) return
                            val kw = try { kwF?.get(p.thisObject) ?: "" } catch (_: Throwable) { "" }
                            if ((kw as? String)?.any { it.isAsianWakeupCharacter() } == true) {
                                p.result = 1
                                log("kwd asian text fix $kn.${m.name} keyword=$kw")
                            }
                        }
                    })
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

    private fun hookWakeupCustomPhrase(lpparam: LoadPackageParam) {
        // SP.getString → 数据源为空时从 XML 文件读取
        findAndHookMethod("android.app.SharedPreferencesImpl", lpparam.classLoader,
            "getString", String::class.java, String::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    if (p.args[0] == CUSTOM_WAKEUP_TEXT_KEY) {
                        val orig = p.result as? String
                        if (orig.isNullOrEmpty()) {
                            val txt = readWakeupText()
                            if (txt.isNotEmpty()) {
                                p.result = txt
                                log("custom phrase fallback from shared_prefs key=$CUSTOM_WAKEUP_TEXT_KEY value=$txt")
                            }
                        } else {
                            updateWakeupTextCache(orig)
                        }
                    }
                }
            })
        // MatrixCursor.addRow → ContentProvider locale 不匹配后丢弃文本
        try {
            val c = lpparam.classLoader.loadClass("android.database.MatrixCursor")
            val columnNamesField = try {
                c.getDeclaredField("columnNames").apply { isAccessible = true }
            } catch (_: Throwable) {
                null
            }
            findAndHookMethod(c, "addRow", arrayOfNulls<Any>(0).javaClass, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val row = p.args[0] ?: return
                    if (!row.javaClass.isArray) return
                    try {
                        val cols = columnNamesField?.get(p.thisObject) as? Array<*> ?: return
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
                    } catch (t: Throwable) {
                        logError("MatrixCursor customKeyword patch failed", t)
                    }
                }
            })
        } catch (t: Throwable) {
            logError("hookWakeupCustomPhrase failed", t)
        }
    }

    private fun hookCustomWakeupTrainers(lpparam: LoadPackageParam) {
        hookCustomWakeupTrainerClass(lpparam, "uc.a", "CustomKwdTrainer")
        hookCustomWakeupTrainerClass(lpparam, "uc.b", "CustomKwvTrainer")
        hookCustomWakeupTrainerClass(lpparam, "sb.c", "CustomWakeupKwdCoreWrapper")
    }

    private fun hookCustomWakeupTrainerClass(lpparam: LoadPackageParam, className: String, tag: String) {
        try {
            val cls = lpparam.classLoader.loadClass(className)
            var hooked = 0
            for (m in cls.declaredMethods) {
                when {
                    m.name == "c" && m.parameterTypes.size == 2 -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                val gVar = p.args.getOrNull(0)
                                val lVar = p.args.getOrNull(1)
                                val wakeupWord = extractStringField(lVar, "f7970e")
                                val debugMode = extractBooleanField(lVar, "f7968c")
                                val locale = extractLocaleField(gVar, "f7962b")
                                log("$tag prepare ok=${p.result} wakeupWord=$wakeupWord debug=$debugMode locale=$locale")
                            }
                        })
                        hooked++
                    }
                    m.name == "b" && m.parameterTypes.size == 2 -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                log("$tag train ok=${p.result}")
                            }
                        })
                        hooked++
                    }
                    m.name == "m" && m.parameterTypes.size == 1 -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                log("$tag verifyRun ok=${p.result}")
                            }
                        })
                        hooked++
                    }
                    m.name == "release" && m.parameterTypes.isEmpty() -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                log("$tag release")
                            }
                        })
                        hooked++
                    }
                }
            }
            log("$tag hooks installed class=$className count=$hooked")
        } catch (t: Throwable) {
            logError("hookCustomWakeupTrainerClass failed for $className", t)
        }
    }

    private fun hookWakeupKeywordTypeBridge(lpparam: LoadPackageParam) {
        try {
            val cls = lpparam.classLoader.loadClass("eh0.l0")
            var hooked = 0
            for (m in cls.declaredMethods) {
                if (m.name != "l" || m.parameterTypes.size != 2) continue
                if (m.parameterTypes[0] != Context::class.java || m.parameterTypes[1] != String::class.java) continue
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        log("keywordType update ${m.name} value=${p.args.getOrNull(1)}")
                    }
                })
                hooked++
            }
            for (m in cls.declaredMethods) {
                if (m.name != "a" || m.parameterTypes.size != 1) continue
                if (m.parameterTypes[0] != Context::class.java) continue
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        log("keywordType query ${m.name} result=${p.result}")
                    }
                })
                hooked++
            }
            log("keywordType bridge installed hooks=$hooked")
        } catch (t: Throwable) {
            logError("hookWakeupKeywordTypeBridge failed", t)
        }
    }

    private fun hookAgentCustomPhraseBridge(lpparam: LoadPackageParam) {
        try {
            val cls = lpparam.classLoader.loadClass("ut.m")
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
            XposedBridge.hookMethod(getter, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val text = p.result as? String ?: return
                    if (text.isNotBlank()) updateWakeupTextCache(text)
                }
            })
            log("custom phrase provider bridge installed")
        } catch (t: Throwable) {
            logError("hookAgentCustomPhraseBridge failed", t)
        }
    }

    private fun hookCustomWakeupResourceDownload(lpparam: LoadPackageParam) {
        try {
            val cls = lpparam.classLoader.loadClass("ck0.a")
            val invoke = cls.declaredMethods.firstOrNull { method ->
                method.name == "invoke" && method.parameterTypes.size == 1
            } ?: return
            XposedBridge.hookMethod(invoke, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    if (extractIntField(p.thisObject, "f9823a") != 17) return
                    val presenter = extractAnyField(p.thisObject, "f9824b") ?: return
                    if (presenter.javaClass.name != "dk0.l") return
                    val result = p.args.getOrNull(0) as? Int ?: return
                    val resourceState = queryCustomWakeupResourceState(lpparam.classLoader)
                    log("custom wakeup PUS result=$result resourceState=$resourceState")

                    if (result == 1) {
                        customWakeupResourceRetryByPresenter.remove(presenter)
                        return
                    }
                    if (result != -1) {
                        customWakeupResourceRetryByPresenter.remove(presenter)
                        return
                    }
                    if (resourceState == CUSTOM_WAKEUP_RESOURCE_READY) {
                        customWakeupResourceRetryByPresenter.remove(presenter)
                        p.args[0] = 1
                        log("custom wakeup PUS stale failure reconciled as ready")
                        return
                    }
                    if (customWakeupResourceRetryByPresenter.put(presenter, true) == true) return

                    p.result = null
                    Thread {
                        try {
                            Thread.sleep(CUSTOM_WAKEUP_RESOURCE_RETRY_DELAY_MILLIS)
                            val refreshedState = queryCustomWakeupResourceState(lpparam.classLoader)
                            val methodName = if (refreshedState == CUSTOM_WAKEUP_RESOURCE_READY) "E" else "D"
                            presenter.javaClass.getDeclaredMethod(methodName).apply {
                                isAccessible = true
                            }.invoke(presenter)
                            log("custom wakeup PUS retry action=$methodName resourceState=$refreshedState")
                        } catch (t: Throwable) {
                            logError("custom wakeup PUS retry failed", t)
                        }
                    }.start()
                }
            })
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

    private fun hookAgentEnrollmentFlow(lpparam: LoadPackageParam) {
        hookPhraseSelectionPresenter(lpparam)
        hookRecordingLaunch(lpparam)
        hookEnrollManagerConstructors(lpparam)
        hookEnrollManagers(lpparam)
        hookEnrollCallbacks(lpparam)
        hookSentenceSpotterFlow(lpparam)
        hookSentenceFinalAsrFlow(lpparam)
    }

    private fun hookEnrollManagerConstructors(lpparam: LoadPackageParam) {
        try {
            val cls = lpparam.classLoader.loadClass("eh0.w")
            XposedBridge.hookAllConstructors(cls, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    cacheSentenceManagerConfig(
                        p.thisObject,
                        p.args.getOrNull(4) as? String
                    )
                    log(
                        "SentenceEnrollManager.ctor " +
                            "langArg=${p.args.getOrNull(3)} " +
                            "spotterKeywordArg=${p.args.getOrNull(4)} " +
                            "lang=${extractStringField(p.thisObject, "f21859d")} " +
                            "spotterKeyword=${extractStringField(p.thisObject, "f21875u")} " +
                            "customText=${readWakeupText()}"
                    )
                }
            })
            log("SentenceEnrollManager constructor hook installed")
        } catch (t: Throwable) {
            logError("hookEnrollManagerConstructors failed", t)
        }
    }

    private fun hookPhraseSelectionPresenter(lpparam: LoadPackageParam) {
        try {
            val cls = lpparam.classLoader.loadClass("dk0.l")
            for (m in cls.declaredMethods) {
                if (m.name != "F" || !m.parameterTypes.contentEquals(arrayOf(String::class.java))) continue
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        log("VoiceWakeupOptionsPresenter.select phrase=${p.args.getOrNull(0)}")
                    }
                    override fun afterHookedMethod(p: MethodHookParam) {
                        log("VoiceWakeupOptionsPresenter.select done phrase=${p.args.getOrNull(0)}")
                    }
                })
            }
        } catch (t: Throwable) {
            logError("hookPhraseSelectionPresenter failed", t)
        }
    }

    private fun hookRecordingLaunch(lpparam: LoadPackageParam) {
        try {
            val cls = lpparam.classLoader.loadClass("dk0.f")
            for (m in cls.declaredMethods) {
                if (m.name != "H" || !m.parameterTypes.contentEquals(arrayOf(String::class.java))) continue
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        log("VoiceWakeupOptionsFragment.launch recording WAKEUP_PHRASE=${p.args.getOrNull(0)}")
                    }
                })
            }
        } catch (t: Throwable) {
            logError("hookRecordingLaunch failed", t)
        }
    }

    private fun hookEnrollManagers(lpparam: LoadPackageParam) {
        hookEnrollManagerClass(lpparam, "eh0.n", "CustomEnrollManager")
        hookEnrollManagerClass(lpparam, "eh0.w", "SentenceEnrollManager")
    }

    private fun hookEnrollManagerClass(lpparam: LoadPackageParam, className: String, tag: String) {
        try {
            val cls = lpparam.classLoader.loadClass(className)
            var hooked = 0
            for (m in cls.declaredMethods) {
                when {
                    m.name == "prepare" && m.parameterTypes.isEmpty() -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                log("$tag.prepare before state=${extractState(p.thisObject)} lang=${extractStringField(p.thisObject, if (className == "eh0.n") "f21826d" else "f21859d")} keyword=${extractStringField(p.thisObject, "f21875u")}")
                            }
                            override fun afterHookedMethod(p: MethodHookParam) {
                                log("$tag.prepare after result=${p.result} binded=${extractBooleanField(p.thisObject, if (className == "eh0.n") "l" else "f21866k")}")
                            }
                        })
                        hooked++
                    }
                    m.name == "b" && m.parameterTypes.isEmpty() -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                log("$tag.startRecordCustomKeyword state=${extractState(p.thisObject)}")
                            }
                        })
                        hooked++
                    }
                    m.name == "f" && m.parameterTypes.isEmpty() -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                log("$tag.startEnroll before customKeyword=${extractStringField(p.thisObject, "f21830h")} g2p=${extractStringField(p.thisObject, "f21843v")}")
                            }
                            override fun afterHookedMethod(p: MethodHookParam) {
                                log("$tag.startEnroll after result=${p.result} customKeyword=${extractStringField(p.thisObject, "f21830h")} g2p=${extractStringField(p.thisObject, "f21843v")}")
                            }
                        })
                        hooked++
                    }
                    (m.name == "i" || m.name == "e") && m.parameterTypes.isNotEmpty() -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                if (className == "eh0.w") {
                                    val expectedText = p.args.getOrNull(2) as? String
                                    cacheSentenceExpectedText(p.thisObject, expectedText)
                                }
                                log("$tag.setNext ${m.name} args=${p.args.joinToString()}")
                            }
                        })
                        hooked++
                    }
                    m.name == "l" && m.parameterTypes.isEmpty() -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                sentenceEndpointStopArmedByManager.remove(p.thisObject)
                                log("$tag.startRecording audioSrc=${if (className == "eh0.n") "CUSTOM_ENROLL" else "SENTENCE_ENROLL"} spotterKeyword=${extractStringField(p.thisObject, "f21875u")} spotterCompleted=${extractBooleanField(p.thisObject, "f21873s")} spotterBundle=${safeToString(extractAnyField(p.thisObject, "f21874t"))}")
                            }
                        })
                        hooked++
                    }
                }
            }
            log("$tag manager hooks installed class=$className count=$hooked")
        } catch (t: Throwable) {
            logError("hookEnrollManagerClass failed for $className", t)
        }
    }

    private fun hookSentenceSpotterFlow(lpparam: LoadPackageParam) {
        try {
            val listenerCls = lpparam.classLoader.loadClass("eh0.u")
            val onTransact = listenerCls.getDeclaredMethod(
                "onTransact",
                Int::class.javaPrimitiveType,
                android.os.Parcel::class.java,
                android.os.Parcel::class.java,
                Int::class.javaPrimitiveType
            )
            XposedBridge.hookMethod(onTransact, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val manager = extractSpotterManager(p.thisObject)
                    val directBundle = readSpotterResultBundle(p.args.getOrNull(1))
                    val normalizedBundle = normalizeAgentSpotterResult(manager, directBundle)
                    if (manager != null && normalizedBundle != null) {
                        cacheSpotterResult(manager, normalizedBundle)
                        maybeArmSentenceEndpointStop(manager, normalizedBundle, "spotter")
                    }
                    log(
                        "SentenceSpotterListener.onTransact code=${p.args.getOrNull(0)} " +
                            "completed=${extractBooleanField(manager, "f21873s")} " +
                            "cachedCompleted=${extractCachedSpotterCompleted(manager)} " +
                            "keyword=${extractManagerSpotterKeyword(manager)} " +
                            "bundle=${safeToString(extractAnyField(manager, "f21874t"))} " +
                            "cachedBundle=${safeToString(extractCachedSpotterBundle(manager))} " +
                            "directBundle=${safeToString(directBundle)} " +
                            "normalizedBundle=${safeToString(normalizedBundle)}"
                    )
                }
            })
            log("SentenceSpotterListener hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceSpotterFlow listener failed", t)
        }
        try {
            val compareCls = lpparam.classLoader.loadClass("com.samsung.android.imagetranslation.util.p")
            val accept = compareCls.getDeclaredMethod("accept", Any::class.java)
            XposedBridge.hookMethod(accept, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val manager = extractCompareManager(p.thisObject) ?: return
                    val expected = extractCompareExpected(p.thisObject)
                    val pos = extractComparePosition(p.thisObject)
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
                            "asr=${safeToString(p.args.getOrNull(0))}"
                    )
                }
                override fun afterHookedMethod(p: MethodHookParam) {
                    val manager = extractCompareManager(p.thisObject) ?: return
                    log(
                        "SentenceSpotterCompare.after spotterCompleted=${extractBooleanField(manager, "f21873s")} " +
                            "cachedCompleted=${extractCachedSpotterCompleted(manager)} " +
                            "bundle=${safeToString(extractAnyField(manager, "f21874t"))} " +
                            "cachedBundle=${safeToString(extractCachedSpotterBundle(manager))}"
                    )
                }
            })
            log("SentenceSpotterCompare hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceSpotterFlow compare failed", t)
        }
    }

    private fun hookSentenceFinalAsrFlow(lpparam: LoadPackageParam) {
        try {
            val runnerCls = lpparam.classLoader.loadClass("com.samsung.phoebus.audio.output.c0")
            val run = runnerCls.getDeclaredMethod("run")
            XposedBridge.hookMethod(run, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val caseId = extractIntField(p.thisObject, "f17674a")
                    if (caseId != 4) return
                    val manager = extractAnyField(p.thisObject, "f17675b") ?: return
                    log(
                        "SentenceFinalAsr.run case=$caseId " +
                            "manager=${manager.javaClass.name} " +
                            "keyword=${extractManagerSpotterKeyword(manager)} " +
                            "expected=${extractExpectedSentenceText(manager)} " +
                            "consumer=${extractAnyField(p.thisObject, "f17677d")?.javaClass?.name}"
                    )
                    if (!isCustomSentenceManager(manager)) return
                    val originalConsumer = extractAnyField(p.thisObject, "f17677d") as? java.util.function.Consumer<Any?> ?: return
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
                    findFieldRecursive(p.thisObject.javaClass, "f17677d")?.apply {
                        isAccessible = true
                        set(p.thisObject, wrapper)
                    }
                    log(
                        "SentenceFinalAsr.wrap consumer=${originalConsumer.javaClass.name} " +
                            "expected=${extractExpectedSentenceText(manager)}"
                    )
                }
            })
            log("SentenceFinalAsr hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceFinalAsrFlow failed", t)
        }
        try {
            val enrollProxyCls = lpparam.classLoader.loadClass("no0.b")
            val submit = enrollProxyCls.getDeclaredMethod(
                "o",
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                android.os.Bundle::class.java
            )
            XposedBridge.hookMethod(submit, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    log(
                        "EnrollmentService.setNextWithText " +
                            "sessionId=${p.args.getOrNull(0)} " +
                            "expected=${p.args.getOrNull(1)} " +
                            "asr=${p.args.getOrNull(2)} " +
                            "bundle=${safeToString(p.args.getOrNull(3))}"
                    )
                }
            })
            log("EnrollmentService setNextWithText hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceFinalAsrFlow enrollment proxy failed", t)
        }
        try {
            val dispatcherCls = lpparam.classLoader.loadClass("a51.o")
            val asrResultCls = lpparam.classLoader.loadClass("xp0.c")
            val dispatch = dispatcherCls.getDeclaredMethod("s", asrResultCls)
            XposedBridge.hookMethod(dispatch, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val recognizer = extractAnyField(p.thisObject, "f780b") ?: return
                    if (recognizer.javaClass.name != "eh0.c0") return
                    val finalListener = extractAnyField(recognizer, "f21769g")
                    val partialListener = extractAnyField(recognizer, "f21768f")
                    log(
                        "SentenceAsrDispatcher.s " +
                            "final=${extractBooleanField(p.args.getOrNull(0), "f63648a")} " +
                            "result=${safeToString(p.args.getOrNull(0))} " +
                            "finalListener=${finalListener?.javaClass?.name} " +
                            "partialListener=${partialListener?.javaClass?.name}"
                    )
                }
            })
            log("SentenceAsrDispatcher hook installed")
        } catch (t: Throwable) {
            logError("hookSentenceFinalAsrFlow dispatcher failed", t)
        }
    }

    private fun hookWakeupSpotterFlow(lpparam: LoadPackageParam) {
        try {
            val cls = lpparam.classLoader.loadClass("com.samsung.android.voicewakeup.audiorecord.SpotterService")
            val onStartCommand = cls.getDeclaredMethod(
                "onStartCommand",
                android.content.Intent::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            XposedBridge.hookMethod(onStartCommand, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val intent = p.args.getOrNull(0) as? android.content.Intent
                    currentWakeupSpotterKeyword = intent?.getStringExtra("spotterKeyword")
                    lastWakeupSpotterBundle = null
                    log(
                        "SpotterService.onStartCommand " +
                            "intent=${intentSummary(intent)} " +
                            "customText=${readWakeupText()} " +
                            "fieldKeyword=${extractStringField(p.thisObject, "F")}"
                    )
                }

                override fun afterHookedMethod(p: MethodHookParam) {
                    log(
                        "SpotterService.onStartCommand.after " +
                            "fieldKeyword=${extractStringField(p.thisObject, "F")} " +
                            "bundle=${safeToString(extractAnyField(p.thisObject, "E"))}"
                    )
                }
            })
            val getSpotter = cls.getDeclaredMethod("a")
            XposedBridge.hookMethod(getSpotter, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    log(
                        "SpotterService.getSpotter.before " +
                            "fieldKeyword=${extractStringField(p.thisObject, "F")} " +
                            "customText=${readWakeupText()}"
                    )
                }

                override fun afterHookedMethod(p: MethodHookParam) {
                    val keyword = extractStringField(p.thisObject, "F")
                    if (keyword == "custom") {
                        val replacement = buildCustomSentenceSpotter(p.thisObject, lpparam.classLoader)
                        if (replacement != null) {
                            p.result = replacement
                            log("SpotterService.getSpotter replaced default verifier for custom keyword")
                        }
                    }
                    log(
                        "SpotterService.getSpotter.after " +
                            "fieldKeyword=${extractStringField(p.thisObject, "F")} " +
                            "spotter=${p.result?.javaClass?.name}"
                    )
                }
            })
            log("SpotterService hooks installed")
        } catch (t: Throwable) {
            logError("hookWakeupSpotterFlow service failed", t)
        }
        try {
            val listenerProxyCls = lpparam.classLoader.loadClass("com.samsung.android.bixby.wakeup.x")
            val deliver = listenerProxyCls.getDeclaredMethod("t", android.os.Bundle::class.java)
            XposedBridge.hookMethod(deliver, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val original = p.args.getOrNull(0) as? android.os.Bundle ?: return
                    val normalized = normalizeWakeupResultBundle(original)
                    if (normalized !== original) {
                        p.args[0] = normalized
                    }
                    log(
                        "SpotterResultProxy.t " +
                            "keyword=$currentWakeupSpotterKeyword " +
                            "original=${safeToString(original)} " +
                            "normalized=${safeToString(normalized)}"
                    )
                }
            })
            log("SpotterResultProxy hook installed")
        } catch (t: Throwable) {
            logError("hookWakeupSpotterFlow proxy failed", t)
        }
        try {
            val callbackCls = lpparam.classLoader.loadClass("pc.e")
            for (methodName in arrayOf("a", "b", "c")) {
                val m = callbackCls.getDeclaredMethod(methodName, lpparam.classLoader.loadClass("gd.z0"))
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val service = extractSpotterServiceFromCallback(p.thisObject) ?: return
                        log(
                            "SpotterCallback.$methodName " +
                                "fieldKeyword=${extractStringField(service, "F")} " +
                                "bundle=${safeToString(extractAnyField(service, "E"))} " +
                                "result=${safeToString(p.args.getOrNull(0))}"
                        )
                    }
                })
            }
            log("SpotterCallback hooks installed")
        } catch (t: Throwable) {
            logError("hookWakeupSpotterFlow callback failed", t)
        }
    }

    private fun hookEnrollCallbacks(lpparam: LoadPackageParam) {
        try {
            val customAsrCls = lpparam.classLoader.loadClass("eh0.d")
            val accept = customAsrCls.getDeclaredMethod("accept", Any::class.java)
            XposedBridge.hookMethod(accept, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    log("CustomEnrollManager.accept case=${extractIntField(p.thisObject, "f21775a")} obj=${safeToString(p.args.getOrNull(0))}")
                }
            })
            log("CustomEnrollManager callback hook installed")
        } catch (t: Throwable) {
            logError("hookEnrollCallbacks custom failed", t)
        }
        try {
            val sentenceAsrCls = lpparam.classLoader.loadClass("eh0.t")
            val accept = sentenceAsrCls.getDeclaredMethod("accept", Any::class.java)
            XposedBridge.hookMethod(accept, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    val manager = extractSentenceAcceptManager(p.thisObject)
                    val partialText = extractFinalAsrText(p.args.getOrNull(0))
                    val partialRawText = extractRawAsrText(p.args.getOrNull(0))
                    maybeArmSentenceEndpointStop(manager, extractCachedSpotterBundle(manager), "partial")
                    log(
                        "SentenceEnrollManager.accept case=${extractIntField(p.thisObject, "f21853a")} " +
                            "obj=${safeToString(p.args.getOrNull(0))} " +
                            "text=$partialText raw=$partialRawText " +
                            "spotterCompleted=${extractBooleanField(manager, "f21873s")} " +
                            "cachedCompleted=${extractCachedSpotterCompleted(manager)} " +
                            "bundle=${safeToString(extractAnyField(manager, "f21874t"))} " +
                            "cachedBundle=${safeToString(extractCachedSpotterBundle(manager))}"
                    )
                }
            })
            log("SentenceEnrollManager callback hook installed")
        } catch (t: Throwable) {
            logError("hookEnrollCallbacks sentence failed", t)
        }
        try {
            val vmCls = lpparam.classLoader.loadClass("ek0.o")
            for (m in vmCls.declaredMethods) {
                when {
                    m.name == "g" && m.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, String::class.java)) -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                log("EnrollView callback onCustomKeywordReceived code=${p.args.getOrNull(0)} text=${p.args.getOrNull(1)} mode=${extractIntField(p.thisObject, "f22007a")}")
                            }
                        })
                    }
                    m.name == "f" && m.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) -> {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(p: MethodHookParam) {
                                log("EnrollView callback onEnrollResult status=${p.args.getOrNull(0)} step=${p.args.getOrNull(1)} result=${p.args.getOrNull(2)} mode=${extractIntField(p.thisObject, "f22007a")}")
                            }
                        })
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
