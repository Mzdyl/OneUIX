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
}
