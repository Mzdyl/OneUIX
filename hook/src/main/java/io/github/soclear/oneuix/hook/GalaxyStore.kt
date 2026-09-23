package io.github.soclear.oneuix.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.getHookConfig
import kotlinx.serialization.Serializable
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.MatchType
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier


object GalaxyStore {
    // 区域配置
    private val regionConfigs = mapOf(
        "US" to RegionConfig(
            salesCode = "USA",
            countryCode = "US",
            countryIsoCode = "XAA",
            simOperator = "310030",
            simCountryIso = "us",
            simOperatorName = "Centennial",
        ),
        "CN" to RegionConfig(
            salesCode = "CHC",
            countryCode = "CN",
            countryIsoCode = "CHC",
            simOperator = "46000",
            simCountryIso = "cn",
            simOperatorName = "中国移动",
        ),
        "HK" to RegionConfig(
            salesCode = "TGY",
            countryCode = "HK",
            countryIsoCode = "TGY",
            simOperator = "45400",
            simCountryIso = "hk",
            simOperatorName = "CSL",
        ),
        "TW" to RegionConfig(
            salesCode = "BRI",
            countryCode = "TW",
            countryIsoCode = "BRI",
            simOperator = "46697",
            simCountryIso = "tw",
            simOperatorName = "Taiwan Mobile",
        ),
        "JP" to RegionConfig(
            salesCode = "DCM",
            countryCode = "JP",
            countryIsoCode = "DCM",
            simOperator = "44010",
            simCountryIso = "jp",
            simOperatorName = "NTT Docomo",
        ),
        "KR" to RegionConfig(
            salesCode = "SKC",
            countryCode = "KR",
            countryIsoCode = "SKC",
            simOperator = "45005",
            simCountryIso = "kr",
            simOperatorName = "SK Telecom",
        ),
        "UK" to RegionConfig(
            salesCode = "BTU",
            countryCode = "GB",
            countryIsoCode = "BTU",
            simOperator = "23433",
            simCountryIso = "gb",
            simOperatorName = "EE",
        ),
        "DE" to RegionConfig(
            salesCode = "DBT",
            countryCode = "DE",
            countryIsoCode = "DBT",
            simOperator = "26202",
            simCountryIso = "de",
            simOperatorName = "Vodafone",
        ),
    )

    private data class RegionConfig(
        val salesCode: String,
        val countryCode: String,
        val countryIsoCode: String,
        val simOperator: String,
        val simCountryIso: String,
        val simOperatorName: String,
    )

    /**
     * 启用 Galaxy Store 换区功能
     *
     * **原理**:
     * 通过 hook SystemProperties.get 和 TelephonyManager 的方法，
     * 伪造设备区域信息，使 Galaxy Store 认为设备位于目标区域。
     */
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun changeRegion(regionCode: String) {
        if (param.packageName != Package.STORE) return
        val config = regionConfigs[regionCode] ?: return
        val classLoader = param.classLoader

        try {
            val systemProperties = classLoader.loadClass("android.os.SystemProperties")
            val getMethod = systemProperties.getDeclaredMethod("get", String::class.java)
            xposedModule.hook(getMethod).intercept { chain ->
                val key = chain.args[0] as? String
                when (key) {
                    "ro.csc.sales_code" -> config.salesCode
                    "ro.csc.country_code" -> config.countryCode
                    "ro.csc.countryiso_code" -> config.countryIsoCode
                    else -> chain.proceed()
                }
            }
        } catch (_: Throwable) {}

        try {
            val telephonyManager = classLoader.loadClass("android.telephony.TelephonyManager")
            telephonyManager.declaredMethods.filter { it.name == "getSimOperator" && it.parameterCount == 0 }.forEach {
                xposedModule.hook(it).intercept { config.simOperator }
            }
            telephonyManager.declaredMethods.filter { it.name == "getSimCountryIso" && it.parameterCount == 0 }.forEach {
                xposedModule.hook(it).intercept { config.simCountryIso }
            }
            telephonyManager.declaredMethods.filter { it.name == "getSimOperatorName" && it.parameterCount == 0 }.forEach {
                xposedModule.hook(it).intercept { config.simOperatorName }
            }
        } catch (_: Throwable) {}
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun blockGalaxyStoreAds() {
        if (param.packageName != Package.STORE) {
            return
        }
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() }
            if (hookConfig != null) {
                hook(classLoader, hookConfig)
            }
        }
    }


    @Serializable
    private data class GalaxyStoreHookConfig(
        override val versionCode: Long,
        val prepareRecommendPopupOnDownloading: Set<String>,
        val isWebViewPopupHideDay: String,
        val requestMapPutMethod: String,
        val getAdDataGroupParentMethod: String,
        val checkAge: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): GalaxyStoreHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val prepareRecommendPopupOnDownloading = bridge.findClass {
                searchPackages("com.sec.android.app.samsungapps.detail.activity")
                matcher {
                    modifiers(Modifier.PUBLIC, MatchType.Equals)
                }
            }.findMethod {
                matcher { name = "prepareRecommendPopupOnDownloading" }
            }
            if (prepareRecommendPopupOnDownloading.isEmpty()) {
                return null
            }

            val isWebViewPopupHideDay = bridge.findClass {
                searchPackages("com.samsung.android.game.cloudgame.sdk")
                matcher {
                    addInterface("com.sec.android.app.commonlib.doc.DataExchanger")
                }
            }.findMethod {
                matcher { name = "isWebViewPopupHideDay" }
            }.singleOrNull() ?: return null

            val requestMapPutMethod = bridge.findClass {
                searchPackages("com.samsung.android.game.cloudgame.sdk")
                matcher {
                    modifiers = Modifier.PUBLIC
                    superClass = "java.lang.Object"
                    usingStrings(
                        "IP20-SHELL",
                        "Occurred NoSuchAlgorithmException. So will be return default value(yyyyMMddHH). GalaxyApps's hashValue : ",
                    )
                }
            }.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    returnType = "void"
                    paramTypes(
                        String::class.java,
                        String::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    usingEqStrings("")
                }
            }.singleOrNull {
                "IMEI" !in it.usingStrings
            } ?: return null

            val adInventoryManager = bridge.getClassData(
                "com.sec.android.app.samsungapps.curate.ad.AdInventoryGroup"
            ) ?: return null

            val getAdDataGroupParentMethod = adInventoryManager.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    returnType = "com.sec.android.app.samsungapps.curate.ad.AdDataGroupParent"
                    paramTypes(List::class.java)
                    usingStrings("ListPortWithBanner", "ListLandSearchPage")
                }
            }.singleOrNull() ?: return null

            val checkAge = bridge.findClass {
                searchPackages("com.samsung.android.game.cloudgame.sdk")
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    addInterface("com.sec.android.app.commonlib.realnameage.IAgeLimitChecker")
                    superClass = "java.lang.Object"
                }
            }.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    returnType = "void"
                    name = "check"
                }
            }.singleOrNull() ?: return null

            return GalaxyStoreHookConfig(
                versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode,
                prepareRecommendPopupOnDownloading = prepareRecommendPopupOnDownloading
                    .map { it.toDexMethod().serialize() }
                    .toSet(),
                isWebViewPopupHideDay = isWebViewPopupHideDay.toDexMethod().serialize(),
                requestMapPutMethod = requestMapPutMethod.toDexMethod().serialize(),
                getAdDataGroupParentMethod = getAdDataGroupParentMethod.toDexMethod()
                    .serialize(),
                checkAge = checkAge.toDexMethod().serialize(),
            )
        }
    }


    context(xposedModule: XposedModule)
    private fun hook(classLoader: ClassLoader, hookConfig: GalaxyStoreHookConfig) {
        // 去除详情页点击安装后的的推荐弹窗
        hookConfig.prepareRecommendPopupOnDownloading.forEach {
            xposedModule.hook(DexMethod(it).getMethodInstance(classLoader)).intercept { null }
        }

        // 去除首页弹窗
        xposedModule.hook(DexMethod(hookConfig.isWebViewPopupHideDay).getMethodInstance(classLoader))
            .intercept { true }

        xposedModule.hook(DexMethod(hookConfig.requestMapPutMethod).getMethodInstance(classLoader))
            .intercept { chain ->
                when (chain.args[0]) {
                    // 禁止上传隐私
                    "pengtaiInfo", "tencentReportInfoSupport", "chinaInfo" -> null

                    // 禁止加载广告
                    "adInfoList" -> {
                        val newArgs = chain.args.toTypedArray()
                        newArgs[1] = "N"
                        chain.proceed(newArgs)
                    }

                    else -> chain.proceed()
                }
            }

        // 拦截广告加载完成回调
        val platformClass =
            classLoader.loadClass($$"com.sec.android.app.samsungapps.curate.ad.AdInventoryManager$PLATFORM")
        xposedModule.hook(
            classLoader.loadClass("com.sec.android.app.samsungapps.slotpage.GalaxyAppsMainActivity")
                .getDeclaredMethod("onAdAvailable", platformClass)
        ).intercept { null }

        // 清空广告列表
        val listClass = classLoader.loadClass("java.util.List")
        xposedModule.hook(
            classLoader.loadClass("com.sec.android.app.samsungapps.curate.ad.AdInventoryGroupSAP")
                .getDeclaredConstructor(listClass)
        ).intercept { chain ->
            val result = chain.proceed()
            val itemList = chain.thisObject.reflect["itemList"] as ArrayList<*>
            itemList.clear()
            result
        }

        // 替换为空的广告组
        xposedModule.hook(DexMethod(hookConfig.getAdDataGroupParentMethod).getMethodInstance(classLoader))
            .intercept {
                val adDataGroupParentClass =
                    classLoader.loadClass("com.sec.android.app.samsungapps.curate.ad.AdDataGroupParent")
                adDataGroupParentClass.constructors.first {
                    it.parameterCount == 0
                }.newInstance()
            }

        // 去除年龄验证
        xposedModule.hook(DexMethod(hookConfig.checkAge).getMethodInstance(classLoader)).intercept { chain ->
            chain.args.last()?.reflect?.call("onResult", true)
            null
        }
    }
}
