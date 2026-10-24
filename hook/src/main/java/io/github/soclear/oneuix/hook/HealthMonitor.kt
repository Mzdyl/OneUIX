package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import io.github.soclear.oneuix.hook.util.longVersionCode
import io.github.soclear.oneuix.hook.util.xlog
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Modifier

object HealthMonitor {
    private const val SUPPORTED_TYPE_CLASS = "com.samsung.android.shealthmonitor.util.CommonConstants\$SupportedType"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun bypassCountryCheck() {
        if (param.packageName != Package.HEALTH_MONITOR) {
            return
        }
        afterAttach {
            val hookConfig = getHookConfig(File(filesDir, "HealthMonitorHookConfig_v2.json")) {
                getHookConfigFromDexKit()
            } ?: return@afterAttach

            try {
                val supportedTypeClass = classLoader.loadClass(SUPPORTED_TYPE_CLASS)

                val allSupportType = supportedTypeClass.enumConstants
                    ?.filterIsInstance<Enum<*>>()
                    ?.firstOrNull { it.name == "ALL_SUPPORT" }

                if (allSupportType != null) {
                    val method = DexMethod(hookConfig.isSupportedCountryMethod).getMethodInstance(classLoader)
                    xposedModule.hook(method).intercept { allSupportType }
                }

                hookConfig.getSavedSimNetworkCountryMethod?.let {
                    val method = DexMethod(it).getMethodInstance(classLoader)
                    xposedModule.hook(method).intercept { "KR" }
                }

                hookConfig.getControllerListMethod?.let {
                    val method = DexMethod(it).getMethodInstance(classLoader)
                    xposedModule.hook(method).intercept {
                        arrayListOf(
                            "com.samsung.android.shealthmonitor.sleep.control.SleepController",
                            "com.samsung.android.shealthmonitor.bp.control.BloodPressureController",
                            "com.samsung.android.shealthmonitor.ecg2.control.Ecg2Controller",
                        )
                    }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }

    @Serializable
    data class HealthMonitorHookConfig(
        override val versionCode: Long,
        val isSupportedCountryMethod: String,
        val getSavedSimNetworkCountryMethod: String? = null,
        val getControllerListMethod: String? = null,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): HealthMonitorHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val supportedTypeClass = bridge.findClass {
                matcher {
                    superClass = "java.lang.Enum"
                    usingStrings("ALL_SUPPORT", "MCC_NOT_SUPPORT", "CSC_NOT_SUPPORT")
                }
            }.singleOrNull() ?: return null

            val isSupportedCountryMethod = bridge.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    returnType = supportedTypeClass.name
                    usingStrings("fake country not set")
                }
            }.singleOrNull() ?: return null

            val getSavedSimNetworkCountryMethod = bridge.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    declaredClass(isSupportedCountryMethod.toDexMethod().className)
                    returnType = "java.lang.String"
                    paramCount = 0
                    usingStrings("shealth_monitor_base_app_setup_saved_sim_country")
                }
            }.singleOrNull()

            val getControllerListMethod = bridge.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    returnType = "java.util.ArrayList"
                    paramCount = 0
                    usingStrings(
                        "com.samsung.android.shealthmonitor.sleep.control.SleepController",
                        "com.samsung.android.shealthmonitor.ecg2.control.Ecg2Controller",
                    )
                }
            }.singleOrNull()

            return HealthMonitorHookConfig(
                versionCode = longVersionCode,
                isSupportedCountryMethod = isSupportedCountryMethod.toDexMethod().serialize(),
                getSavedSimNetworkCountryMethod = getSavedSimNetworkCountryMethod?.toDexMethod()?.serialize(),
                getControllerListMethod = getControllerListMethod?.toDexMethod()?.serialize(),
            )
        }
    }
}
