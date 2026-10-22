package io.github.soclear.oneuix.hook

import android.app.Activity
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.hook.util.xlog
import java.util.ArrayList

object WatchPlugin {

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init() {
        xlog("OneUIX: WatchPlugin init for ${param.packageName}")
        preventWearOsPackageRemovedReset()
    }

    /**
     * 拦截卸载 Wear OS CN 时触发的手表重置机制。
     *
     * 背景：
     * 三星手表插件（watchuniteplugin、waterplugin 等）后台常驻服务 SettingsCompanion
     * 会监听 ACTION_PACKAGE_REMOVED。当监听到 com.google.android.wearable.app.cn 被卸载且手机
     * 判定为国行时，会通过 launchDialogActivity() 调起 SettingsDialogActivity。
     * SettingsDialogActivity 在 onCreate() 中会立即无条件调用 removeBond()，
     * 向蓝牙协议栈发送 WEAR_OS_PACKAGE_REMOVED 指令并清除底层配对关系，强行导致手表断联并重置。
     *
     * 防护策略：
     * 1. 拦截 SettingsCompanion.launchDialogActivity(ArrayList)，阻止调起解绑弹窗 Activity；
     * 2. 拦截 SettingsDialogActivity.removeBond(ArrayList)，彻底掐死任何触发底层蓝牙解绑的通路；
     * 3. 拦截 SettingsDialogActivity.showDialog(ArrayList)，直接 finish() 避免显示弹窗。
     */
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun preventWearOsPackageRemovedReset() {
        // 闸门 1：拦截 SettingsCompanion.launchDialogActivity(ArrayList<String>)
        try {
            val companionClass = param.classLoader.loadClass(
                "com.samsung.android.companionservice.service.SettingsCompanion"
            )
            val launchMethod = companionClass.getDeclaredMethod("launchDialogActivity", ArrayList::class.java)
            xposedModule.hook(launchMethod).intercept { chain ->
                xlog("OneUIX: [WatchPlugin] BLOCKED SettingsCompanion.launchDialogActivity(${chain.args.firstOrNull()})")
                Unit
            }
            xlog("OneUIX: [WatchPlugin] Hooked SettingsCompanion.launchDialogActivity")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook SettingsCompanion.launchDialogActivity failed: $t")
        }

        // 闸门 2：拦截 SettingsDialogActivity.removeBond(ArrayList<DeviceRegistryData>)
        try {
            val dialogActivityClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.activity.SettingsDialogActivity"
            )
            val removeBondMethod = dialogActivityClass.getDeclaredMethod("removeBond", ArrayList::class.java)
            removeBondMethod.isAccessible = true
            xposedModule.hook(removeBondMethod).intercept { chain ->
                xlog("OneUIX: [WatchPlugin] BLOCKED SettingsDialogActivity.removeBond! Prevented unpair/reset.")
                Unit
            }
            xlog("OneUIX: [WatchPlugin] Hooked SettingsDialogActivity.removeBond")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook SettingsDialogActivity.removeBond failed: $t")
        }

        // 闸门 3：拦截 SettingsDialogActivity.showDialog(ArrayList<DeviceRegistryData>)
        try {
            val dialogActivityClass = param.classLoader.loadClass(
                "com.samsung.android.waterplugin.activity.SettingsDialogActivity"
            )
            val showDialogMethod = dialogActivityClass.getDeclaredMethod("showDialog", ArrayList::class.java)
            showDialogMethod.isAccessible = true
            xposedModule.hook(showDialogMethod).intercept { chain ->
                xlog("OneUIX: [WatchPlugin] BLOCKED SettingsDialogActivity.showDialog, finishing activity")
                (chain.thisObject as? Activity)?.finish()
                Unit
            }
            xlog("OneUIX: [WatchPlugin] Hooked SettingsDialogActivity.showDialog")
        } catch (t: Throwable) {
            xlog("OneUIX: [WatchPlugin] Hook SettingsDialogActivity.showDialog failed: $t")
        }
    }
}
