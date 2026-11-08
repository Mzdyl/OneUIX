package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.hook.util.PreferenceProvider
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog

object NfcEmulation {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init() {
        if (param.packageName != Package.NFC) return
        bypassShellNfcPrompt()
        overrideRoutingOptions()
    }

    // NFC 路由判定一次会话会连进多个拦截点，每次都 openRemoteFile + 读盘 + JSON 解码
    // 都在 binder 热路径上，容易拖出 ANR。短 TTL 快照足够，改开关最多延迟 3 秒生效。
    private const val PREFERENCE_CACHE_TTL_MS = 3_000L

    @Volatile
    private var cachedPreference: Preference? = null

    @Volatile
    private var cachedPreferenceAt = 0L

    context(xposedModule: XposedModule)
    private fun nfcPreference(): Preference.Nfc? {
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = cachedPreference
        if (cached != null && now - cachedPreferenceAt < PREFERENCE_CACHE_TTL_MS) {
            return cached.nfc
        }
        val loaded = PreferenceProvider.loadPreference()
        if (loaded != null) {
            cachedPreference = loaded
            cachedPreferenceAt = now
            return loaded.nfc
        }
        return cached?.nfc
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun bypassShellNfcPrompt() {
        try {
            val adapterServiceClass = runCatching {
                param.classLoader.loadClass("com.android.nfc.NfcService\$NfcAdapterService")
            }.getOrNull()

            adapterServiceClass?.declaredMethods?.filter { it.name == "enable" }?.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val shouldBypass = nfcPreference()?.bypassPrompt ?: false
                    if (!shouldBypass) return@intercept chain.proceed()

                    val pkg = chain.args.firstOrNull() as? String
                    xlog("OneUIX: Intercepted NfcAdapterService.enable(pkg=$pkg)")
                    try {
                        val thisObj = chain.thisObject
                        val nfcService = thisObj.reflect["this$0"] ?: runCatching {
                            param.classLoader.loadClass("com.android.nfc.NfcService")
                                .getDeclaredMethod("getInstance").invoke(null)
                        }.getOrNull()
                        nfcService?.reflect?.call("enableNfc")
                        true
                    } catch (t: Throwable) {
                        xlog(t)
                        chain.proceed()
                    }
                }
            }

            val allowlistActivityClass = runCatching {
                param.classLoader.loadClass("com.android.nfc.NfcEnableAllowlistActivity")
            }.getOrNull()

            allowlistActivityClass?.declaredMethods?.filter { it.name == "onCreate" }?.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val shouldBypass = nfcPreference()?.bypassPrompt ?: false
                    if (!shouldBypass) return@intercept chain.proceed()

                    val result = chain.proceed()
                    try {
                        xlog("OneUIX: Intercepted NfcEnableAllowlistActivity.onCreate - auto finishing")
                        val nfcService = runCatching {
                            param.classLoader.loadClass("com.android.nfc.NfcService")
                                .getDeclaredMethod("getInstance").invoke(null)
                        }.getOrNull()
                        nfcService?.reflect?.call("enableNfc")
                        (chain.thisObject as? android.app.Activity)?.finish()
                    } catch (t: Throwable) {
                        xlog(t)
                    }
                    result
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun overrideRoutingOptions() {
        try {
            val routingManagerClass = runCatching {
                param.classLoader.loadClass("com.android.nfc.cardemulation.RoutingOptionManager")
            }.getOrNull() ?: return

            val booleanMethods = listOf(
                "isAutoChangeEnabled",
                "isDefaultRouteAutoChange",
                "isRoutingTableOverrided"
            )
            booleanMethods.forEach { methodName ->
                routingManagerClass.declaredMethods.filter { it.name == methodName }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val shouldSimulate = nfcPreference()?.enableSimulation ?: false
                        if (shouldSimulate) false else chain.proceed()
                    }
                }
            }

            val intMethods = listOf(
                "getDefaultRoute",
                "getDefaultOffHostRoute",
                "getDefaultIsoDepRoute",
                "getOverrideDefaultRoute",
                "getOverrideDefaultOffHostRoute",
                "getOverrideDefaultIsoDepRoute"
            )
            intMethods.forEach { methodName ->
                routingManagerClass.declaredMethods.filter { it.name == methodName }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val shouldSimulate = nfcPreference()?.enableSimulation ?: false
                        if (shouldSimulate) 0 else chain.proceed()
                    }
                }
            }

            val overrideSetterMethods = listOf(
                "overrideDefaultRoute",
                "overrideDefaultIsoDepRoute",
                "overrideDefaultOffHostRoute",
                "overrideDefaultScRoute"
            )
            overrideSetterMethods.forEach { methodName ->
                routingManagerClass.declaredMethods.filter { it.name == methodName && it.parameterCount == 1 }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val shouldSimulate = nfcPreference()?.enableSimulation ?: false
                        if (shouldSimulate) {
                            chain.args[0] = 0
                        }
                        chain.proceed()
                    }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
