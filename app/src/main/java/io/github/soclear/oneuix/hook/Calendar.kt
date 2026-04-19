package io.github.soclear.oneuix.hook

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.SamsungFeature.overrideCscString

object Calendar {
    fun enableChineseHolidayDisplay(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.CALENDAR) return

        overrideCscString(loadPackageParam, "enableChineseHolidayDisplay") { key, _ ->
            if (key == "CscFeature_Calendar_EnableLocalHolidayDisplay") {
                "CHINA"
            } else {
                null
            }
        }
    }
}
