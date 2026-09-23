package io.github.soclear.oneuix.hook

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import de.robv.android.xposed.XposedHelpers.newInstance
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.logError

object StorageAgent {
    fun bypassCountryCheck(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.STORAGE_AGENT) return
        val classLoader = loadPackageParam.classLoader

        // 1. Spoof system app flags to satisfy Google MediaSync check ((flags & 129) != 0)
        try {
            val appPkgMgrClass = findClass("android.app.ApplicationPackageManager", classLoader)
            XposedBridge.hookAllMethods(appPkgMgrClass, "getApplicationInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val appInfo = param.result as? ApplicationInfo ?: return
                    if (appInfo.packageName == Package.STORAGE_AGENT) {
                        appInfo.flags = appInfo.flags or ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                    }
                }
            })

            XposedBridge.hookAllMethods(appPkgMgrClass, "getPackageInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val pkgInfo = param.result as? PackageInfo ?: return
                    if (pkgInfo.packageName == Package.STORAGE_AGENT) {
                        pkgInfo.applicationInfo?.let {
                            it.flags = it.flags or ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                        }
                    }
                }
            })

            val contextWrapperClass = findClass("android.content.ContextWrapper", classLoader)
            findAndHookMethod(contextWrapperClass, "getApplicationInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val appInfo = param.result as? ApplicationInfo ?: return
                    if (appInfo.packageName == Package.STORAGE_AGENT) {
                        appInfo.flags = appInfo.flags or ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                    }
                }
            })
        } catch (t: Throwable) {
            logError("StorageAgent: hook ApplicationInfo flags failed", t)
        }

        // 2. Direct hook on zzqr.zzA to bypass system app check completely
        try {
            val zzqrClass = findClass("com.google.android.gms.internal.media_sync.zzqr", classLoader)
            findAndHookMethod(zzqrClass, "zzA", object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    try {
                        callMethod(param.thisObject, "zzH")
                    } catch (t: Throwable) {
                        logError("StorageAgent: call zzH failed", t)
                    }
                    return null
                }
            })
        } catch (t: Throwable) {
            logError("StorageAgent: hook zzqr.zzA failed", t)
        }

        // 3. Hook GCloudSDKBaseAsync.getCountryCodeByDevice to return non-blacklisted country ("US")
        try {
            val asyncClass = findClass(
                "com.samsung.android.agent.storage.ui.sdk.GCloudSDKBaseAsync",
                classLoader
            )
            findAndHookMethod(
                asyncClass,
                "getCountryCodeByDevice",
                returnConstant("US")
            )
        } catch (t: Throwable) {
            logError("StorageAgent: hook getCountryCodeByDevice failed", t)
        }

        // 4. Hook IsSupportedCountryHandler to always return supported = true
        try {
            val handlerClass = findClass(
                "com.samsung.android.agent.storage.ui.provider.handler.IsSupportedCountryHandler",
                classLoader
            )
            findAndHookMethod(
                handlerClass,
                "handle",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val result = Bundle().apply {
                            putBoolean("supported", true)
                        }
                        param.result = result
                    }
                }
            )
        } catch (t: Throwable) {
            logError("StorageAgent: hook IsSupportedCountryHandler failed", t)
        }

        // 5. Prevent forced app update popup from Galaxy Store
        try {
            val updateManagerClass = findClass(
                "com.samsung.android.agent.storage.ui.update.ForceAppUpdateManager",
                classLoader
            )
            findAndHookMethod(
                updateManagerClass,
                "checkUpdate",
                Context::class.java,
                Boolean::class.javaPrimitiveType,
                returnConstant(false)
            )
        } catch (t: Throwable) {
            logError("StorageAgent: hook ForceAppUpdateManager failed", t)
        }

        // 6. User-Agent Spoofing to prevent Google OAuth from blocking embedded WebView
        try {
            val webSettingsClass = findClass("android.webkit.WebSettings", classLoader)
            findAndHookMethod(webSettingsClass, "setUserAgentString", String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ua = param.args[0] as? String ?: return
                    val cleanUa = ua.replace("; wv", "")
                        .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
                        .replace(" WebViewInline", "")
                    param.args[0] = cleanUa
                }
            })

            val zzerClass = findClass("com.google.android.gms.internal.media_sync.zzer", classLoader)
            findAndHookMethod(zzerClass, "zzc", String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == "gsdktest.spoofuseragent") {
                        param.result = "true"
                    }
                }
            })

            val webViewClass = findClass("android.webkit.WebView", classLoader)
            callStaticMethod(webViewClass, "setWebContentsDebuggingEnabled", true)
        } catch (t: Throwable) {
            logError("StorageAgent: hook User-Agent spoofing failed", t)
        }

        // 7. Force unhide auth WebView when page finishes loading & log auth events
        try {
            val authListenerClass = findClass(
                "com.samsung.android.agent.storage.ui.onboarding.controller.OAuthWebViewController\$AuthEventListener",
                classLoader
            )
            findAndHookMethod(authListenerClass, "onPageFinished", String::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val url = param.args[0] as? String ?: return
                    if (url.isNotEmpty() && url != "about:blank") {
                        try {
                            callMethod(param.thisObject, "onAuthPageRendered")
                        } catch (t: Throwable) {
                            logError("StorageAgent: call onAuthPageRendered failed", t)
                        }
                    }
                }
            })

            findAndHookMethod(authListenerClass, "onAuthFailure", Throwable::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val th = param.args[0] as? Throwable
                    logError("StorageAgent: onAuthFailure triggered", th)
                }
            })

            findAndHookMethod(authListenerClass, "onAuthSuccess", String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val acc = param.args[0] as? String
                    XposedBridge.log("OneUIX: StorageAgent onAuthSuccess for $acc")
                }
            })
        } catch (t: Throwable) {
            logError("StorageAgent: hook AuthEventListener failed", t)
        }

        // 8. Plaintext logging for Samsung GCS and GMS MediaSync debug
        try {
            val logClass = findClass("com.samsung.android.agent.storage.ui.utils.Log", classLoader)
            findAndHookMethod(logClass, "getEncodedString", String::class.java, object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any {
                    return param.args[0] as? String ?: ""
                }
            })

            val zzfbClass = findClass("com.google.android.gms.internal.media_sync.zzfb", classLoader)
            findAndHookMethod(zzfbClass, "zzb", returnConstant(true))
        } catch (t: Throwable) {
            logError("StorageAgent: hook plaintext logging failed", t)
        }

        // 9. Bypass Samsung Account link server call (returns 500 in China region) and retain Google Photos sync
        try {
            val samsungAccountLinkClass = findClass(
                "com.samsung.android.agent.storage.ui.account.SamsungAccountLink",
                classLoader
            )
            val linkResultClass = findClass(
                "com.samsung.android.agent.storage.ui.account.SamsungAccountLink\$LinkResult",
                classLoader
            )
            val linkStatusClass = findClass(
                "com.samsung.android.agent.storage.ui.account.SamsungAccountLinkStatus",
                classLoader
            )
            val successLink = getStaticObjectField(linkStatusClass, "SUCCESS_LINK")
            val successWithLink = getStaticObjectField(linkStatusClass, "SUCCESS_WITH_LINK")

            findAndHookMethod(
                samsungAccountLinkClass,
                "linkAccount",
                Context::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                java.util.function.Consumer::class.java,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val consumer = param.args[4] as? java.util.function.Consumer<Any>
                        val accountName = param.args[3] as? String ?: "liuruofei20030120@gmail.com"
                        XposedBridge.log("OneUIX: SamsungAccountLink.linkAccount bypassed for $accountName")
                        val successResult = newInstance(linkResultClass, successLink, "dummy_link_id", accountName)
                        consumer?.accept(successResult)
                        return null
                    }
                }
            )

            findAndHookMethod(
                samsungAccountLinkClass,
                "getLinkList",
                Context::class.java,
                java.util.function.Consumer::class.java,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val context = param.args[0] as Context
                        val consumer = param.args[1] as? java.util.function.Consumer<Any>
                        val savedAccount = try {
                            context.getSharedPreferences("google_cloud_setting", Context.MODE_PRIVATE)
                                .getString("saved_account_name", null)
                        } catch (_: Throwable) {
                            null
                        } ?: "liuruofei20030120@gmail.com"
                        val successResult = newInstance(linkResultClass, successWithLink, "dummy_link_id", savedAccount)
                        consumer?.accept(successResult)
                        return null
                    }
                }
            )
        } catch (t: Throwable) {
            logError("StorageAgent: hook SamsungAccountLink failed", t)
        }
    }
}
