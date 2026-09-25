package io.github.soclear.oneuix.hook.systemui

import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.afterAttachTry

object Other {
    context(xposedModule: XposedModule)
    fun disableScreenshotCaptureSound() = afterAttachTry {
        classLoader
            .loadClass(
                "com.android.systemui.screenshot.${
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "sep."
                    else ""
                }ScreenshotCaptureSound"
            )
            .declaredMethods
            .filter { it.name == "play" }
            .forEach {
                xposedModule.hook(it).intercept { }
            }
    }
}
