package io.github.soclear.oneuix.hook.util

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

object SamsungFeature {
    private const val SEM_CSC_FEATURE = "com.samsung.android.feature.SemCscFeature"
    private const val SEM_FLOATING_FEATURE = "com.samsung.android.feature.SemFloatingFeature"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun overrideCscString(
        label: String,
        resolver: (key: String, defaultValue: String?) -> String?,
    ) {
        hookStringFeature(SEM_CSC_FEATURE, label, resolver)
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun overrideCscBoolean(
        label: String,
        resolver: (key: String, defaultValue: Boolean?) -> Boolean?,
    ) {
        hookBooleanFeature(SEM_CSC_FEATURE, label, resolver)
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun overrideFloatingBoolean(
        label: String,
        resolver: (key: String, defaultValue: Boolean?) -> Boolean?,
    ) {
        hookBooleanFeature(SEM_FLOATING_FEATURE, label, resolver)
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookStringFeature(
        className: String,
        label: String,
        resolver: (key: String, defaultValue: String?) -> String?,
    ) {
        try {
            val clazz = param.classLoader.loadClass(className)
            clazz.declaredMethods
                .filter { it.name == "getString" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val key = chain.args.getOrNull(0) as? String ?: return@intercept chain.proceed()
                        val defaultValue = chain.args.getOrNull(1) as? String
                        resolver(key, defaultValue) ?: chain.proceed()
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookBooleanFeature(
        className: String,
        label: String,
        resolver: (key: String, defaultValue: Boolean?) -> Boolean?,
    ) {
        try {
            val clazz = param.classLoader.loadClass(className)
            clazz.declaredMethods
                .filter { it.name == "getBoolean" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val key = chain.args.getOrNull(0) as? String ?: return@intercept chain.proceed()
                        val defaultValue = chain.args.getOrNull(1) as? Boolean
                        resolver(key, defaultValue) ?: chain.proceed()
                    }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
