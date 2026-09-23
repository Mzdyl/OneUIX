package io.github.soclear.oneuix.hook

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package

object Nfc {
    fun init(lpparam: LoadPackageParam) {
        if (lpparam.packageName != Package.NFC) return

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
