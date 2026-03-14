package io.github.soclear.oneuix.hook

import android.content.Context
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.setStaticBooleanField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.logError

object CoreRune {
    fun supportAppJumpBlock(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID &&
            loadPackageParam.packageName != Package.SETTINGS ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            return
        }

        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val coreRuneClass =
                        findClass("com.samsung.android.rune.CoreRune", loadPackageParam.classLoader)
                    setStaticBooleanField(coreRuneClass, "SUPPORT_APP_JUMP_BLOCK", true)
                } catch (t: Throwable) {
                    logError("supportAppJumpBlock callback failed", t)
                }
            }
        }

        try {
            if (loadPackageParam.packageName == Package.ANDROID) {
                findAndHookConstructor(
                    "com.android.server.wm.ActivityTaskManagerService",
                    loadPackageParam.classLoader,
                    Context::class.java,
                    callback
                )
            }
            if (loadPackageParam.packageName == Package.SETTINGS) {
                val infix =
                    if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                        "security"
                    } else {
                        "privacy"
                    }
                findAndHookMethod(
                    "com.samsung.android.settings.$infix.AppRedirectInterceptionPreferenceController",
                    loadPackageParam.classLoader,
                    "getAvailabilityStatus",
                    callback
                )
            }
        } catch (t: Throwable) {
            logError("supportAppJumpBlock failed", t)
        }
    }
}
