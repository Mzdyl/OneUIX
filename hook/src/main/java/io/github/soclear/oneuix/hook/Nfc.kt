package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object Nfc {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init(enableSimulation: Boolean) {
        if (param.packageName != Package.NFC) return

        bypassShellNfcPrompt()

        if (enableSimulation) {
            overrideRoutingOptions()
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun bypassShellNfcPrompt() {
        try {
            val adapterServiceClass = runCatching {
                param.classLoader.loadClass("com.android.nfc.NfcService\$NfcAdapterService")
            }.getOrNull() ?: return

            adapterServiceClass.declaredMethods.filter { it.name == "enable" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val pkg = chain.args.firstOrNull() as? String
                    if (pkg == "com.android.shell" || pkg == "io.github.soclear.oneuix" || pkg == "io.github.mzdyl.oneuix" || pkg == "root") {
                        val newArgs = chain.args.toTypedArray()
                        newArgs[0] = "com.android.settings"
                        chain.proceed(newArgs)
                    } else {
                        chain.proceed()
                    }
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
