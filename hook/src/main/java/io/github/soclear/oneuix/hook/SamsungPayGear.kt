package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object SamsungPayGear {

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init() {
        if (param.packageName != Package.SAMSUNG_PAY_GEAR) return
        xlog("OneUIX: SamsungPayGear init")

        bypassWearableCnCheck()
        supportWatchUnitePlugin()
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun bypassWearableCnCheck() {
        try {
            val spayBaseActivityClass = param.classLoader.loadClass(
                "com.samsung.android.samsungpay.gear.ui.SpayBaseActivity"
            )
            val method = spayBaseActivityClass.getDeclaredMethod("checkIsWearableCNInstalled")
            xposedModule.hook(method).intercept {
                xlog("OneUIX: bypass checkIsWearableCNInstalled")
                Unit
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun supportWatchUnitePlugin() {
        try {
            val wearableProviderClass = param.classLoader.loadClass(
                "com.samsung.android.samsungpay.gear.common.provider.WearableProvider"
            )
            val method = wearableProviderClass.getDeclaredMethod("d")
            xposedModule.hook(method).intercept { chain ->
                val real = runCatching { chain.proceed() as? Boolean }.getOrNull()
                if (real == true) return@intercept true
                try {
                    val pluginCheckerClass = param.classLoader.loadClass(
                        "com.samsung.android.samsungpay.gear.common.provider.WearablePluginChecker"
                    )
                    val activePlugin = pluginCheckerClass.getDeclaredMethod("getActivePlugin").invoke(null) as? String
                    if (activePlugin == "com.samsung.wearable.watchuniteplugin") {
                        xlog("OneUIX: WearableProvider.d() plugin=$activePlugin -> true")
                        return@intercept true
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                real ?: false
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
