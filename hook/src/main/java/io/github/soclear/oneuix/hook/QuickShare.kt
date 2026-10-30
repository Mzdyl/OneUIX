package io.github.soclear.oneuix.hook

import android.content.Context
import android.view.Menu
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.quickshare.QuickShareConfig
import io.github.soclear.oneuix.hook.quickshare.QuickShareConfigCache
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.xlog
import java.io.File
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod

object QuickShare {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun enableGoogleQuickShare() {
        handleHooks(enableGoogleQuickShare = true, unlockQuickShareLabs = false)
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun handleHooks(enableGoogleQuickShare: Boolean, unlockQuickShareLabs: Boolean) {
        if (param.packageName != Package.SHARE_LIVE) {
            return
        }
        if (!enableGoogleQuickShare && !unlockQuickShareLabs) {
            return
        }

        afterAttachTry {
            val hookConfig = QuickShareConfigCache.load(
                File(filesDir, "OneUIXQuickShare.json"), longVersionCode
            ) { getHookConfigFromDexKit() }

            fun install(methodName: String?, hooker: XposedInterface.Hooker) {
                if (methodName == null) return
                runCatching {
                    val method = DexMethod(methodName).getMethodInstance(classLoader)
                    xposedModule.hook(method).intercept(hooker)
                }.onFailure { xlog(it) }
            }

            if (enableGoogleQuickShare) {
                install(hookConfig.nearbyShareSupportedMethod) { true }
                install(hookConfig.isMoseySupportedMethod) { true }
                install(hookConfig.sepGlobalCheckMethod) { chain ->
                    val arg0 = chain.args.getOrNull(0) as? String
                    val arg1 = chain.args.getOrNull(1) as? String
                    if ((arg0 == "sepChinaPublic" && arg1 == "sepGlobal") ||
                        (arg0 == "sepGlobal" && arg1 == "sepChinaPublic")
                    ) true else chain.proceed()
                }
                install(hookConfig.temporaryModeMethod) { false }
            }

            if (unlockQuickShareLabs) {
                // 1. 绕过发货机型校验 (ro.product_ship == "true")
                // 激活 DeveloperSettingsActivity 与隐藏转码设置，同时抑制 Samsung Analytics 遥测
                install(hookConfig.isProductShipMethod) { false }

                // 2. 解锁应用栏常驻开发者选项菜单 (R.id.developer)
                install(hookConfig.prepareAppbarMenuMethod) { chain ->
                    chain.proceed()
                    val menu = chain.args.getOrNull(0) as? Menu ?: return@install null
                    val devId = runCatching {
                        resources.getIdentifier("developer", "id", packageName)
                    }.getOrDefault(0)
                    if (devId != 0) {
                        menu.findItem(devId)?.isVisible = true
                    } else {
                        for (i in 0 until menu.size()) {
                            val item = menu.getItem(i)
                            val name = runCatching { resources.getResourceEntryName(item.itemId) }.getOrNull()
                            if (name == "developer") {
                                item.isVisible = true
                                break
                            }
                        }
                    }
                    null
                }

                // 3. 阻止从主设置页移除 pref_category_labs (快速分享实验室)
                install(hookConfig.removePreferenceMethod) { chain ->
                    val pref = chain.args.getOrNull(0)
                    val key = runCatching {
                        pref?.javaClass?.getMethod("getKey")?.invoke(pref) as? String
                    }.getOrNull()
                    if (key == "pref_category_labs") {
                        return@install null
                    }
                    chain.proceed()
                }

                // 4. 强制实验室特性项保持可见 (文件夹整包分享、NFC 触碰分享、手气分享)
                install(hookConfig.setPreferenceVisibleMethod) { chain ->
                    val pref = chain.thisObject
                    val key = runCatching {
                        pref?.javaClass?.getMethod("getKey")?.invoke(pref) as? String
                    }.getOrNull()
                    if (key in LABS_PREF_KEYS) {
                        chain.args[0] = true
                    }
                    chain.proceed()
                }

                // 5. 自动激活实时传输通信协议显示 (P2P / Aware / LAN / BLE)
                runCatching {
                    val prefs = getSharedPreferences("hidden_setting", Context.MODE_PRIVATE)
                    if (!prefs.getBoolean("show_communication_type", false)) {
                        prefs.edit().putBoolean("show_communication_type", true).apply()
                    }
                }
            }
        }
    }

    private val LABS_PREF_KEYS = setOf("pref_folder_sharing", "pref_lucky_share", "pref_nfc_sharing")

    private fun Context.getHookConfigFromDexKit(): QuickShareConfig {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val nearbyShareSupportedMethod = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "boolean"
                    usingStrings("nearbyShareSupported=", "CapabilitySourceImpl")
                }
            }.singleOrNull()

            val isMoseySupportedMethod = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "boolean"
                    usingStrings("isMoseySupported : ", "CapabilitySourceImpl")
                }
            }.singleOrNull()

            val sepGlobalCheckMethod = bridge.findMethod {
                matcher {
                    paramTypes("java.lang.String", "java.lang.String", "boolean")
                    returnType = "boolean"
                    addCaller {
                        usingStrings("sepChinaPublic", "sepGlobal")
                    }
                }
            }.firstOrNull()

            val temporaryModeMethod = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "boolean"
                    addCaller {
                        usingStrings("temporaryModeEnabled true, skip updateNearbyShareVisibility")
                    }
                }
            }.firstOrNull {
                val cls = it.toDexMethod().className
                cls != "pf.f" && !cls.contains("CapabilitySource")
            }

            val isProductShipMethod = bridge.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "boolean"
                    usingStrings("ro.product_ship")
                }
            }.firstOrNull {
                !it.toDexMethod().className.contains("SLog")
            }

            val prepareAppbarMenuMethod = bridge.findMethod {
                matcher {
                    paramTypes("android.view.Menu")
                    returnType = "void"
                    declaredClass {
                        usingStrings("com.samsung.android.app.sharelive.presentation.developer.DeveloperSettingsActivity")
                    }
                }
            }.firstOrNull()

            val removePreferenceMethod = bridge.findMethod {
                matcher {
                    paramTypes("androidx.preference.Preference")
                    returnType = "void"
                    addCaller {
                        usingStrings("pref_category_labs")
                    }
                }
            }.firstOrNull()

            val setPreferenceVisibleMethod = bridge.findMethod {
                matcher {
                    paramTypes("boolean")
                    returnType = "void"
                    addCaller {
                        usingStrings("pref_folder_sharing")
                    }
                }
            }.firstOrNull()

            return QuickShareConfig(
                versionCode = longVersionCode,
                nearbyShareSupportedMethod = nearbyShareSupportedMethod?.toDexMethod()?.serialize(),
                isMoseySupportedMethod = isMoseySupportedMethod?.toDexMethod()?.serialize(),
                sepGlobalCheckMethod = sepGlobalCheckMethod?.toDexMethod()?.serialize(),
                temporaryModeMethod = temporaryModeMethod?.toDexMethod()?.serialize(),
                isProductShipMethod = isProductShipMethod?.toDexMethod()?.serialize(),
                prepareAppbarMenuMethod = prepareAppbarMenuMethod?.toDexMethod()?.serialize(),
                removePreferenceMethod = removePreferenceMethod?.toDexMethod()?.serialize(),
                setPreferenceVisibleMethod = setPreferenceVisibleMethod?.toDexMethod()?.serialize(),
            )
        }
    }
}

