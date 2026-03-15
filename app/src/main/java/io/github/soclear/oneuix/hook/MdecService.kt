package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.logError
import de.robv.android.xposed.callbacks.XC_LoadPackage

object MdecService {
    fun supportCallAndTextOnOtherDevices(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (loadPackageParam.packageName != Package.MDEC_SERVICE) return
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.mdeccommon.utils.SimUtils",
                loadPackageParam.classLoader,
                "isChinaSIMActive",
                Context::class.java,
                XC_MethodReplacement.returnConstant(false)
            )
        } catch (t: Throwable) {
            logError("supportCallAndTextOnOtherDevices failed", t)
        }
    }
}
