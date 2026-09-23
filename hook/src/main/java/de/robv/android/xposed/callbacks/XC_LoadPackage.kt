package de.robv.android.xposed.callbacks

import android.content.pm.ApplicationInfo

class XC_LoadPackage {
    class LoadPackageParam(
        @JvmField var packageName: String = "",
        @JvmField var processName: String = "",
        @JvmField var classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
        @JvmField var appInfo: ApplicationInfo? = null,
        @JvmField var isFirstApplication: Boolean = false
    )
}
