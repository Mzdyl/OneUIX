package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

import io.github.soclear.oneuix.hook.util.reflect

object Nfc {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init(enableSimulation: Boolean, bypassPrompt: Boolean) {
        if (param.packageName != Package.NFC) return

        if (bypassPrompt) {
            bypassShellNfcPrompt()
        }

        if (enableSimulation) {
            overrideRoutingOptions()
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun bypassShellNfcPrompt() {
        xlog("OneUIX: Initializing NFC prompt bypass hook")
        try {
            val adapterServiceClass = runCatching {
                param.classLoader.loadClass("com.android.nfc.NfcService\$NfcAdapterService")
            }.getOrNull()

            adapterServiceClass?.declaredMethods?.filter { it.name == "enable" }?.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
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

            val booleanMethods = listOf("isAutoChangeEnabled", "isDefaultRouteAutoChange")
            booleanMethods.forEach { methodName ->
                routingManagerClass.declaredMethods.filter { it.name == methodName }.forEach { method ->
                    xposedModule.hook(method).intercept { false }
                }
            }

            val intMethods = listOf("getDefaultRoute", "getDefaultOffHostRoute", "getDefaultIsoDepRoute")
            intMethods.forEach { methodName ->
                routingManagerClass.declaredMethods.filter { it.name == methodName }.forEach { method ->
                    xposedModule.hook(method).intercept { 0 }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
