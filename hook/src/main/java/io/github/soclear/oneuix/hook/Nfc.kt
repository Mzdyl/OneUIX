package io.github.soclear.oneuix.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package

object Nfc {
    fun init(lpparam: LoadPackageParam, enableSimulation: Boolean) {
        if (lpparam.packageName != Package.NFC) return

        bypassShellNfcPrompt(lpparam)

        if (enableSimulation) {
            overrideRoutingOptions(lpparam)
        }
    }

    private fun bypassShellNfcPrompt(lpparam: LoadPackageParam) {
        try {
            val adapterServiceClass = XposedHelpers.findClassIfExists(
                "com.android.nfc.NfcService\$NfcAdapterService",
                lpparam.classLoader
            ) ?: return

            XposedBridge.hookAllMethods(adapterServiceClass, "enable", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val pkg = param.args.firstOrNull() as? String
                    if (pkg == "com.android.shell" || pkg == "io.github.soclear.oneuix" || pkg == "io.github.mzdyl.oneuix" || pkg == "root") {
                        param.args[0] = "com.android.settings"
                    }
                }
            })
        } catch (_: Throwable) {}
    }

    private fun overrideRoutingOptions(lpparam: LoadPackageParam) {
        try {
            val routingManagerClass = XposedHelpers.findClassIfExists(
                "com.android.nfc.cardemulation.RoutingOptionManager",
                lpparam.classLoader
            )
            if (routingManagerClass != null) {
                tryHookMethod(routingManagerClass, "isAutoChangeEnabled", false)
                tryHookMethod(routingManagerClass, "isDefaultRouteAutoChange", false)
                tryHookMethod(routingManagerClass, "getDefaultRoute", 0)
                tryHookMethod(routingManagerClass, "getDefaultOffHostRoute", 0)
                tryHookMethod(routingManagerClass, "getDefaultIsoDepRoute", 0)
            }
        } catch (_: Throwable) {}
    }

    private fun tryHookMethod(clazz: Class<*>, methodName: String, returnValue: Any) {
        try {
            val methods = clazz.declaredMethods.filter { it.name == methodName }
            for (method in methods) {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    method.name,
                    *method.parameterTypes,
                    XC_MethodReplacement.returnConstant(returnValue)
                )
            }
        } catch (_: Throwable) {}
    }
}
