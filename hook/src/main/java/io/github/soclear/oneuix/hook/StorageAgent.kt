package io.github.soclear.oneuix.hook

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object StorageAgent {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun bypassCountryCheck() {
        if (param.packageName != Package.STORAGE_AGENT) return
        val classLoader = param.classLoader

        // 1. Spoof system app flags to satisfy Google MediaSync check ((flags & 129) != 0)
        try {
            val appPkgMgrClass = classLoader.loadClass("android.app.ApplicationPackageManager")
            appPkgMgrClass.declaredMethods.filter { it.name == "getApplicationInfo" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val appInfo = chain.proceed() as? ApplicationInfo
                    if (appInfo?.packageName == Package.STORAGE_AGENT) {
                        appInfo.flags = appInfo.flags or ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                    }
                    appInfo
                }
            }

            appPkgMgrClass.declaredMethods.filter { it.name == "getPackageInfo" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val pkgInfo = chain.proceed() as? PackageInfo
                    if (pkgInfo?.packageName == Package.STORAGE_AGENT) {
                        pkgInfo.applicationInfo?.let {
                            it.flags = it.flags or ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                        }
                    }
                    pkgInfo
                }
            }

            val contextWrapperClass = classLoader.loadClass("android.content.ContextWrapper")
            val getAppInfoMethod = contextWrapperClass.getDeclaredMethod("getApplicationInfo")
            xposedModule.hook(getAppInfoMethod).intercept { chain ->
                val appInfo = chain.proceed() as? ApplicationInfo
                if (appInfo?.packageName == Package.STORAGE_AGENT) {
                    appInfo.flags = appInfo.flags or ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                }
                appInfo
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 2. Direct hook on zzqr.zzA to bypass system app check completely
        try {
            val zzqrClass = classLoader.loadClass("com.google.android.gms.internal.media_sync.zzqr")
            val zzaMethod = zzqrClass.getDeclaredMethod("zzA")
            val zzhMethod = zzqrClass.getDeclaredMethod("zzH")
            xposedModule.hook(zzaMethod).intercept { chain ->
                runCatching { zzhMethod.invoke(chain.thisObject) }.onFailure { xlog(it) }
                null
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 3. Hook GCloudSDKBaseAsync.getCountryCodeByDevice to return non-blacklisted country ("US")
        try {
            val asyncClass = classLoader.loadClass("com.samsung.android.agent.storage.ui.sdk.GCloudSDKBaseAsync")
            val getCountryMethod = asyncClass.getDeclaredMethod("getCountryCodeByDevice")
            xposedModule.hook(getCountryMethod).intercept { "US" }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 4. Hook IsSupportedCountryHandler to always return supported = true
        try {
            val handlerClass = classLoader.loadClass("com.samsung.android.agent.storage.ui.provider.handler.IsSupportedCountryHandler")
            val handleMethod = handlerClass.getDeclaredMethod("handle", Bundle::class.java)
            xposedModule.hook(handleMethod).intercept {
                Bundle().apply { putBoolean("supported", true) }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 5. Prevent forced app update popup from Galaxy Store
        try {
            val updateManagerClass = classLoader.loadClass("com.samsung.android.agent.storage.ui.update.ForceAppUpdateManager")
            val checkUpdateMethod = updateManagerClass.getDeclaredMethod("checkUpdate", Context::class.java, Boolean::class.javaPrimitiveType)
            xposedModule.hook(checkUpdateMethod).intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 6. User-Agent Spoofing to prevent Google OAuth from blocking embedded WebView
        try {
            val webSettingsClass = classLoader.loadClass("android.webkit.WebSettings")
            val setUserAgentMethod = webSettingsClass.getDeclaredMethod("setUserAgentString", String::class.java)
            xposedModule.hook(setUserAgentMethod).intercept { chain ->
                val ua = chain.args[0] as? String
                if (ua != null) {
                    val cleanUa = ua.replace("; wv", "")
                        .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
                        .replace(" WebViewInline", "")
                    val newArgs = chain.args.toTypedArray()
                    newArgs[0] = cleanUa
                    chain.proceed(newArgs)
                } else {
                    chain.proceed()
                }
            }

            val zzerClass = classLoader.loadClass("com.google.android.gms.internal.media_sync.zzer")
            val zzcMethod = zzerClass.getDeclaredMethod("zzc", String::class.java)
            xposedModule.hook(zzcMethod).intercept { chain ->
                if (chain.args.firstOrNull() == "gsdktest.spoofuseragent") "true" else chain.proceed()
            }

            val webViewClass = classLoader.loadClass("android.webkit.WebView")
            val debugMethod = webViewClass.getDeclaredMethod("setWebContentsDebuggingEnabled", Boolean::class.javaPrimitiveType)
            debugMethod.invoke(null, true)
        } catch (t: Throwable) {
            xlog(t)
        }

        // 7. Force unhide auth WebView when page finishes loading & log auth events
        try {
            val authListenerClass = classLoader.loadClass(
                "com.samsung.android.agent.storage.ui.onboarding.controller.OAuthWebViewController\$AuthEventListener"
            )
            authListenerClass.declaredMethods.filter { it.name == "onPageFinished" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val url = chain.args.firstOrNull() as? String ?: ""
                    if (url.isNotEmpty() && url != "about:blank") {
                        runCatching {
                            chain.thisObject.javaClass.getDeclaredMethod("onAuthPageRendered").invoke(chain.thisObject)
                        }.onFailure { xlog(it) }
                    }
                    result
                }
            }

            authListenerClass.declaredMethods.filter { it.name == "onAuthFailure" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val th = chain.args.firstOrNull() as? Throwable
                    xlog(th ?: Exception("onAuthFailure"))
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 8. Plaintext logging for Samsung GCS and GMS MediaSync debug
        try {
            val logClass = classLoader.loadClass("com.samsung.android.agent.storage.ui.utils.Log")
            val getEncodedMethod = logClass.getDeclaredMethod("getEncodedString", String::class.java)
            xposedModule.hook(getEncodedMethod).intercept { chain ->
                chain.args.firstOrNull() as? String ?: ""
            }

            val zzfbClass = classLoader.loadClass("com.google.android.gms.internal.media_sync.zzfb")
            val zzbMethod = zzfbClass.getDeclaredMethod("zzb")
            xposedModule.hook(zzbMethod).intercept { true }
        } catch (t: Throwable) {
            xlog(t)
        }

        // 9. Bypass Samsung Account link server call (returns 500 in China region) and retain Google Photos sync
        try {
            val samsungAccountLinkClass = classLoader.loadClass(
                "com.samsung.android.agent.storage.ui.account.SamsungAccountLink"
            )
            val linkResultClass = classLoader.loadClass(
                "com.samsung.android.agent.storage.ui.account.SamsungAccountLink\$LinkResult"
            )
            val linkStatusClass = classLoader.loadClass(
                "com.samsung.android.agent.storage.ui.account.SamsungAccountLinkStatus"
            )
            val successLink = linkStatusClass.getDeclaredField("SUCCESS_LINK").get(null)
            val successWithLink = linkStatusClass.getDeclaredField("SUCCESS_WITH_LINK").get(null)
            val linkResultConstructor = linkResultClass.getDeclaredConstructor(linkStatusClass, String::class.java, String::class.java).apply {
                isAccessible = true
            }

            samsungAccountLinkClass.declaredMethods.filter { it.name == "linkAccount" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    @Suppress("UNCHECKED_CAST")
                    val consumer = chain.args.getOrNull(4) as? java.util.function.Consumer<Any>
                    val accountName = chain.args.getOrNull(3) as? String ?: "dummy@gmail.com"
                    val successResult = linkResultConstructor.newInstance(successLink, "dummy_link_id", accountName)
                    consumer?.accept(successResult)
                    null
                }
            }

            samsungAccountLinkClass.declaredMethods.filter { it.name == "getLinkList" }.forEach { method ->
                xposedModule.hook(method).intercept { chain ->
                    val context = chain.args[0] as Context
                    @Suppress("UNCHECKED_CAST")
                    val consumer = chain.args.getOrNull(1) as? java.util.function.Consumer<Any>
                    val savedAccount = try {
                        context.getSharedPreferences("google_cloud_setting", Context.MODE_PRIVATE)
                            .getString("saved_account_name", null)
                    } catch (_: Throwable) {
                        null
                    } ?: "dummy@gmail.com"
                    val successResult = linkResultConstructor.newInstance(successWithLink, "dummy_link_id", savedAccount)
                    consumer?.accept(successResult)
                    null
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
