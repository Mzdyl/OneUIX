package io.github.soclear.oneuix.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.SPenTranslationSource
import io.github.soclear.oneuix.hook.util.SamsungFeature

object SPen {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun switchTranslateSource(source: SPenTranslationSource) {
        if (param.packageName != Package.TRANSLATION || source == SPenTranslationSource.DEFAULT) return
        val targetSolution = when (source) {
            SPenTranslationSource.GOOGLE -> "Google"
            SPenTranslationSource.BAIDU -> "Baidu"
            SPenTranslationSource.DEFAULT -> return
        }

        SamsungFeature.overrideCscString("SPenTranslate") { key, _ ->
            if (key.contains("SPen_ConfigDefTranslatorSolution", ignoreCase = true) ||
                key.contains("translate", ignoreCase = true) ||
                key == "DefaultCscFeature_Spen_Translation" ||
                key == "CscFeature_Spen_Translation"
            ) {
                targetSolution
            } else {
                null
            }
        }
    }
}

