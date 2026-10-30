package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object SamsungPushService {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun handleHooks(useChinaCmcServer: Boolean = false) {
        if (param.packageName != Package.SPP) return
        val classLoader = param.classLoader

        if (useChinaCmcServer) {
            runCatching {
                val commonConfigClass = classLoader.loadClass("com.sec.spp.common.CommonConfig")
                val enableEosField = commonConfigClass.getDeclaredField("ENABLE_SPP_EOS").apply { isAccessible = true }
                enableEosField.setBoolean(null, false)
            }.onFailure { xlog(it) }

            runCatching {
                val prefClass = classLoader.loadClass("com.sec.spp.common.pref.CommonPreferences")
                prefClass.declaredMethods.filter { it.name == "getConnectionStopTime" }.forEach {
                    xposedModule.hook(it).intercept { 0L }
                }
                prefClass.declaredMethods.filter { it.name == "setConnectionStopTime" }.forEach {
                    xposedModule.hook(it).intercept { null }
                }
                prefClass.declaredMethods.filter { it.name == "getIsForceStopService" }.forEach {
                    xposedModule.hook(it).intercept { false }
                }
                prefClass.declaredMethods.filter { it.name == "setIsForceStopService" }.forEach {
                    xposedModule.hook(it).intercept { chain ->
                        val arg = chain.args.firstOrNull() as? Boolean ?: false
                        if (arg) null else chain.proceed()
                    }
                }
                prefClass.declaredMethods.filter { it.name == "getProvServerAddress" }.forEach {
                    xposedModule.hook(it).intercept { "china-gld.push.samsungosp.com.cn" }
                }
            }.onFailure { xlog(it) }

            runCatching {
                val sysPropClass = classLoader.loadClass("android.os.SystemProperties")
                sysPropClass.declaredMethods.filter { it.name == "get" }.forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val key = chain.args.firstOrNull() as? String
                        when (key) {
                            "ro.csc.countryiso_code" -> "CN"
                            else -> chain.proceed()
                        }
                    }
                }
            }.onFailure { xlog(it) }
        }
    }
}
