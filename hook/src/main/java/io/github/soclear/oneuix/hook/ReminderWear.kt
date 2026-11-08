package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.hook.util.xlog
import org.luckypray.dexkit.DexKitBridge

/**
 * 三星生活助手（com.samsung.android.app.sreminder）「手机 → 手表」消息闸门。
 *
 * 现象：手表端「三星生活助手」提示「请在手机安装三星生活助手」/「连不到手机」，提醒等数据不同步。
 *
 * 根因：手机端 App 里所有「手机 → 手表」的 GMS Wearable 发送入口，进门前都会先判断
 *      「国行 Wear OS companion（com.google.android.wearable.app.cn）装了没」，没装就静默
 *      返回 null（不抛异常、不打日志），消息不出去。接收方向不过这道门，所以表现为
 *      「手表请求能到手机、手机永远不回」：手表 30s 超时后判定手机没装 App。
 *      走海外 GMS 配对时手机上并没有这个 .cn 包，这道检查必然失败。
 *
 * 做法：用 DexKit 在目标进程里定位所有「引用该包名字符串 + 无参 + 返回 boolean」的方法
 *      （即全部「装了没/没装」判定点），统一让它们返回 false。该包实际未安装，所以签名校验、
 *      provider 选择这类判定点今天本来就返回 false，强制 false 不改变它们；唯一语义相反的是
 *      那个「没装就吞掉发送」的闸门，返回 false 即放行。
 *
 * 真机通道由 com.samsung.wearable.watchuniteplugin 的 WearProxyService 提供，不需要国行 companion
 * （见 recon/team/companion-send-gate-2026-10-24.md §④）。
 */
object ReminderWear {

    /** 国行 Wear OS companion 包名（Google 公开包名，非混淆符号）。 */
    private const val CN_COMPANION = "com.google.android.wearable.app.cn"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun init(pref: Preference.Other) {
        if (param.packageName != Package.SREMINDER) return
        if (!pref.bypassReminderWearableCnCheck) {
            xlog("OneUIX: ReminderWear disabled by preference")
            return
        }
        xlog("OneUIX: ReminderWear init")
        openWearMessageGate()
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun openWearMessageGate() {
        try {
            System.loadLibrary("dexkit")
            DexKitBridge.create(param.classLoader, true).use { bridge ->
                val gates = bridge.findMethod {
                    matcher {
                        returnType = "boolean"
                        paramCount = 0
                        usingStrings(CN_COMPANION)
                    }
                }
                if (gates.isEmpty()) {
                    xlog("OneUIX: [ReminderWear] no gate method found (version drift?)")
                    return
                }
                var hooked = 0
                gates.forEach { gate ->
                    try {
                        val method = gate.getMethodInstance(param.classLoader)
                        xposedModule.hook(method).intercept { false }
                        hooked++
                        xlog("OneUIX: [ReminderWear] hooked ${gate.className}#${gate.methodName} -> false")
                    } catch (t: Throwable) {
                        xlog(t)
                    }
                }
                xlog("OneUIX: [ReminderWear] $hooked/${gates.size} gate method(s) hooked")
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
