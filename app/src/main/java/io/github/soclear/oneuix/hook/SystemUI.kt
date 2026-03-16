package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import java.lang.ref.WeakReference
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.TraditionalChineseCalendar
import io.github.soclear.oneuix.hook.util.log
import io.github.soclear.oneuix.hook.util.logError
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt


object SystemUI {
    enum class QsBar {
        MediaPlayer,
        NearbyDevicesAndDeviceControl,
        SecurityFooter,
        SmartViewAndModes,
    }

    fun setStatusBarPaddingDp(loadPackageParam: LoadPackageParam, left: Float?, right: Float?) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        if (left == null && right == null) return
        try {
            val clazz = findClass(
                "com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmCenterCutout",
                loadPackageParam.classLoader
            )
            if (left != null) {
                findAndHookMethod(
                    clazz,
                    "calculateLeftPadding",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Int {
                            val inputProperties =
                                getObjectField(param.thisObject, "inputProperties")
                            val density = getObjectField(inputProperties, "density") as Float
                            return (left * density).roundToInt()
                        }
                    }
                )
            }
            if (right != null) {
                findAndHookMethod(
                    clazz,
                    "calculateRightPadding",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Int {
                            val inputProperties =
                                getObjectField(param.thisObject, "inputProperties")
                            val density = getObjectField(inputProperties, "density") as Float
                            return (right * density).roundToInt()
                        }
                    }
                )
            }
        } catch (t: Throwable) {
            logError("setStatusBarPaddingDp failed", t)
        }
    }

    fun setBatteryIconScale(loadPackageParam: LoadPackageParam, widthScale: Float?, heightScale: Float?) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || widthScale == null && heightScale == null) return
        try {
            findAndHookMethod(
                "com.android.systemui.battery.BatteryMeterView",
                loadPackageParam.classLoader,
                "scaleBatteryMeterViewsLegacy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val mBatteryIconView = getObjectField(param.thisObject, "mBatteryIconView") as ImageView
                        mBatteryIconView.layoutParams = mBatteryIconView.layoutParams.apply {
                            if (widthScale != null) {
                                width = (width * widthScale).roundToInt()
                            }
                            if (heightScale != null) {
                                height = (height * heightScale).roundToInt()
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            logError("setBatteryIconScale failed", t)
        }
    }

    fun hideBatteryPercentageSign(resparam: InitPackageResourcesParam) {
        if (resparam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            return
        }
        val batterMeterFormat = "status_bar_settings_${
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "uniform_"
            else ""
        }battery_meter_format"
        resparam.res.setReplacement(Package.SYSTEMUI, "string", batterMeterFormat, "%d")
    }

    fun disableScreenshotCaptureSound(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            val screenshotCaptureSoundClass = findClass(
                "com.android.systemui.screenshot.${
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "sep."
                    else ""
                }ScreenshotCaptureSound", loadPackageParam.classLoader
            )
            hookAllMethods(screenshotCaptureSoundClass, "play", returnConstant(null))
        } catch (t: Throwable) {
            logError("disableScreenshotCaptureSound failed", t)
        }
    }


    fun hideDeviceControlQsTile(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT != Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) return
        try {
            findAndHookMethod(
                "com.android.systemui.qs.QSTileHost",
                loadPackageParam.classLoader,
                "createTile",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == "DeviceControl") {
                            param.result = null
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            logError("hideDeviceControlQsTile failed", t)
        }
    }


    // related classes: BarFactory BarController  BarOrderInteractor
    fun hideQsBar(loadPackageParam: LoadPackageParam, qsBarSet: Set<QsBar>) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            qsBarSet.isEmpty() ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            return
        }

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = getObjectField(param.thisObject, "mBarRootView") as View?
                view?.visibility = View.GONE
            }
        }

        if (QsBar.NearbyDevicesAndDeviceControl in qsBarSet) {
            try {
                if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                    findAndHookMethod(
                        "com.android.systemui.qs.bar.BottomLargeTileBar",
                        loadPackageParam.classLoader,
                        "showBar",
                        Boolean::class.javaPrimitiveType,
                        callback
                    )
                } else {
                    findAndHookMethod(
                        "com.android.systemui.qs.bar.LargeTileBar",
                        loadPackageParam.classLoader,
                        "updateLayout",
                        LinearLayout::class.java,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val string = getObjectField(param.thisObject, "TAG") as String
                                if (string == "BottomLargeTileBar") {
                                    val view =
                                        getObjectField(param.thisObject, "mBarRootView") as View?
                                    view?.visibility = View.GONE
                                }
                            }
                        }
                    )
                }
            } catch (t: Throwable) {
                logError("hideQsBar NearbyDevicesAndDeviceControl failed", t)
            }
        }

        if (QsBar.MediaPlayer in qsBarSet) {
            try {
                findAndHookMethod(
                    "com.android.systemui.qs.bar.QSMediaPlayerBar",
                    loadPackageParam.classLoader,
                    "inflateViews",
                    ViewGroup::class.java,
                    callback
                )
            } catch (t: Throwable) {
                logError("hideQsBar MediaPlayer failed", t)
            }
        }

        if (QsBar.SecurityFooter in qsBarSet) {
            try {
                if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                    findAndHookMethod(
                        "com.android.systemui.qs.bar.BarItemImpl",
                        loadPackageParam.classLoader,
                        "showBar",
                        Boolean::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val tag = getObjectField(param.thisObject, "TAG")
                                if (tag == "SecurityFooterBar") {
                                    param.args[0] = false
                                }
                            }
                        }
                    )
                    /* 另一种实现方式
                    findAndHookMethod(
                        "com.android.systemui.qs.QSSecurityFooter$3",
                        loadPackageParam.classLoader,
                        "run",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val qsSecurityFooter =
                                    XposedHelpers.getSurroundingThis(param.thisObject)
                                val securityFooterBar =
                                    getObjectField(qsSecurityFooter, "mVisibilityChangedListener")
                                val view =
                                    getObjectField(securityFooterBar, "mBarRootView") as View?
                                view?.visibility = View.GONE
                            }
                        }
                    )
                    */
                } else {
                    findAndHookMethod(
                        "com.android.systemui.qs.bar.SecurityFooterBar",
                        loadPackageParam.classLoader,
                        "onVisibilityChanged",
                        Int::class.javaPrimitiveType,
                        callback
                    )
                }
            } catch (t: Throwable) {
                logError("hideQsBar SecurityFooter failed", t)
            }
        }

        if (QsBar.SmartViewAndModes in qsBarSet) {
            try {
                findAndHookMethod(
                    "com.android.systemui.qs.bar.SmartViewLargeTileBar",
                    loadPackageParam.classLoader,
                    "showBar",
                    Boolean::class.javaPrimitiveType,
                    callback
                )
            } catch (t: Throwable) {
                logError("hideQsBar SmartViewAndModes failed", t)
            }
        }

        // 横屏
        try {
            val nearbyDevicesAndDeviceControl = QsBar.NearbyDevicesAndDeviceControl in qsBarSet
            val smartViewAndModes = QsBar.SmartViewAndModes in qsBarSet
            if (!nearbyDevicesAndDeviceControl && !smartViewAndModes) {
                return
            }
            findAndHookMethod(
                "com.android.systemui.qs.bar.TopLargeTileBar",
                loadPackageParam.classLoader,
                "addTile",
                $$"com.android.systemui.qs.SecQSPanelControllerBase$TileRecord",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val tile = getObjectField(param.args[0], "tile")
                        val tileSpec = callMethod(tile, "getTileSpec")
                        val flag = when (tileSpec) {
                            "DeviceControl" if nearbyDevicesAndDeviceControl -> true
                            "custom(com.samsung.android.mydevice/.quicksettings.MyDeviceTileService)" if nearbyDevicesAndDeviceControl -> true
                            "custom(com.samsung.android.smartmirroring/.tile.SmartMirroringTile)" if smartViewAndModes -> true
                            "custom(com.samsung.android.app.routines/.LifestyleModeTile)" if smartViewAndModes -> true
                            else -> false
                        }
                        if (flag) {
                            param.result = null
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            logError("hideQsBar TopLargeTileBar failed", t)
        }
    }


    fun alwaysExpandQsTileChunk(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        try {
            findAndHookMethod(
                "com.android.systemui.qs.bar.TileChunkLayoutBar",
                loadPackageParam.classLoader,
                "setContainerHeight",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = getObjectField(param.thisObject, "mContainerExpandedHeight")
                    }
                }
            )
        } catch (t: Throwable) {
            logError("alwaysExpandQsTileChunk setContainerHeight failed", t)
        }

        try {
            findAndHookMethod(
                "com.android.systemui.qs.bar.TileChunkLayoutBar",
                loadPackageParam.classLoader,
                "inflateViews",
                ViewGroup::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val scrollIndicator = getObjectField(
                            param.thisObject,
                            "mScrollIndicatorClickContainer"
                        ) as View
                        scrollIndicator.visibility = View.GONE
                    }
                }
            )
        } catch (t: Throwable) {
            logError("alwaysExpandQsTileChunk inflateViews failed", t)
        }
    }


    fun alwaysShowTimeDateOnQs(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        try {
            // 单独
            findAndHookMethod(
                "com.android.systemui.qs.animator.PanelTransitionAnimator",
                loadPackageParam.classLoader,
                "setQs",
                "com.android.systemui.plugins.qs.QS",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                            setObjectField(param.thisObject, "clockDateContainer", null)
                            return
                        }
                        val context = getObjectField(param.thisObject, "context") as Context
                        setObjectField(param.thisObject, "clockDateContainer", View(context))
                    }
                }
            )
        } catch (t: Throwable) {
            logError("alwaysShowTimeDateOnQs setQs failed", t)
        }

        try {
            // 两者
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val mContext = getObjectField(param.thisObject, "mContext") as Context
                    setObjectField(param.thisObject, "mClockDateContainer", View(mContext))
                }
            }
            if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                findAndHookMethod(
                    "com.android.systemui.qs.animator.LegacyQsExpandAnimator",
                    loadPackageParam.classLoader,
                    "updateViews$2",
                    callback
                )
            } else {
                findAndHookMethod(
                    "com.android.systemui.qs.animator.QsExpandAnimator",
                    loadPackageParam.classLoader,
                    "updateViews",
                    callback
                )
            }
        } catch (t: Throwable) {
            logError("alwaysShowTimeDateOnQs updateViews failed", t)
        }
    }

    fun setQsClockStyle(
        loadPackageParam: LoadPackageParam,
        monospaced: Boolean,
        modifyTextSize: Boolean,
        textSize: Float
    ) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || !monospaced && !modifyTextSize) {
            return
        }
        // 布局见 res/layout/sec_qqs_date_buttons.xml
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val clockView = getObjectField(param.thisObject, "mClockView") as TextView
                // 启用 tabular (等宽) 数字: 'tnum' 1
                // 禁用 proportional (不等宽) 数字: 'pnum' 0
                if (monospaced) {
                    clockView.fontFeatureSettings = "'tnum' 1, 'pnum' 0"
                }
                if (modifyTextSize) {
                    clockView.textSize = textSize

                    val density = clockView.context.resources.displayMetrics.density
                    // 15sp 到 70sp
                    val ratio = 0.00218181f * textSize * textSize + 0.16727272f * textSize
                    val padding = -(density * ratio).roundToInt()
                    clockView.apply {
                        setPadding(paddingLeft, padding, paddingRight, padding)
                    }
                }
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.qs.SecQuickStatusBarHeader",
                loadPackageParam.classLoader,
                "onFinishInflate",
                callback
            )
        } catch (t: Throwable) {
            logError("setQsClockStyle failed", t)
        }
    }


    fun updateStatusBarClockEverySecond(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        // 直接使用 setupSecondUpdate
        setupSecondUpdate(loadPackageParam)
    }

    // 状态栏时钟格式设置
    // 支持时间格式变量：
    // {temp} - 电池温度 (如 25.0°C)
    // {lunar} - 农历日期 (如 三月十七)
    // {rate} - 屏幕刷新率 (如 120Hz)
    // {shichen} - 中国时辰 (如 午时)
    // {sec} - 秒数 (如 45s)
    // {date} - 系统日期 (如 3/16 Sun)
    // 示例: "HH:mm {temp}" → "12:30 25.0°C"
    fun setStatusBarClockStyle(loadPackageParam: LoadPackageParam, format: String, needsSecondUpdate: Boolean = false) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return

        // 自动检测是否需要每秒更新
        // 包含秒(ss)、时辰、温度、刷新率时都需要每秒更新
        val autoDetectSecondUpdate = format.contains("ss") || 
                                format.contains("SS") ||
                                format.contains("{sec}") ||
                                format.contains("{temp}") ||
                                format.contains("{rate}")
        
        val shouldEnableSecondUpdate = needsSecondUpdate || autoDetectSecondUpdate

        // 如果需要每秒更新，先启用秒更新机制
        if (shouldEnableSecondUpdate) {
            setupSecondUpdate(loadPackageParam)
        }

        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val clockTextView = param.thisObject as TextView
                    val context = clockTextView.context
                    
                    // 保存引用用于自定义定时器（使用 WeakReference 避免内存泄漏）
                    clockIndicatorViewRef = WeakReference(clockTextView)
                    clockFormat = format
                    
                    // 启动自定义每秒更新（如果还没启动）
                    ensureSecondUpdateRunning()
                    
                    val text = formatClockText(format, context)
                    clockTextView.text = text
                    clockTextView.contentDescription = text
                    param.result = null
                } catch (t: Throwable) {
                    logError("setStatusBarClockStyle callback error", t)
                }
            }
        }

        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.policy.QSClockIndicatorView",
                loadPackageParam.classLoader,
                "notifyTimeChanged",
                "com.android.systemui.statusbar.policy.QSClockBellSound",
                callback
            )
            log("setStatusBarClockStyle hooked: format=$format, needsSecondUpdate=$shouldEnableSecondUpdate")
        } catch (t: Throwable) {
            logError("setStatusBarClockStyle failed", t)
        }
    }

    // 每秒更新相关变量（使用 WeakReference 避免内存泄漏）
    private var secondUpdateHandler: Handler? = null
    private var secondUpdateRunnable: Runnable? = null
    private var clockIndicatorViewRef: WeakReference<TextView>? = null
    private var clockFormat: String = ""

    private fun setupSecondUpdate(loadPackageParam: LoadPackageParam) {
        // 设置等宽字体（数字对齐）
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.policy.QSClockIndicatorViewController",
                loadPackageParam.classLoader,
                "onViewAttached",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val clockTextView = getObjectField(param.thisObject, "view") as TextView
                        clockTextView.fontFeatureSettings = "tnum"
                    }
                }
            )
        } catch (_: Throwable) {}

        // Hook updateSecondsClockHandler 启动系统内置的秒更新
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.policy.QSClockQuickStarHelper",
                loadPackageParam.classLoader,
                "updateSecondsClockHandler",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val mSecondsHandler = getObjectField(param.thisObject, "mSecondsHandler")
                        if (mSecondsHandler == null) {
                            val looper = Looper.myLooper() ?: return
                            val handler = Handler(looper)
                            setObjectField(param.thisObject, "mSecondsHandler", handler)
                            val mSecondTick = getObjectField(param.thisObject, "mSecondTick") as? Runnable ?: return
                            handler.post(mSecondTick)
                            log("setupSecondUpdate: started system second update")
                        }
                    }
                }
            )
            log("setupSecondUpdate: hook installed")
        } catch (t: Throwable) {
            logError("setupSecondUpdate failed", t)
        }
    }

    // 启动自定义每秒更新（在 callback 第一次被调用时启动）
    private fun ensureSecondUpdateRunning() {
        if (secondUpdateHandler != null) return
        
        secondUpdateHandler = Handler(Looper.getMainLooper())
        secondUpdateRunnable = object : Runnable {
            override fun run() {
                clockIndicatorViewRef?.get()?.let { view ->
                    try {
                        if (clockFormat.isNotEmpty()) {
                            val text = formatClockText(clockFormat, view.context)
                            view.text = text
                            view.contentDescription = text
                        }
                    } catch (_: Throwable) {}
                }
                secondUpdateHandler?.postDelayed(this, 1000)
            }
        }
        secondUpdateHandler?.post(secondUpdateRunnable!!)
        log("ensureSecondUpdateRunning: started")
    }

    // 格式化时钟文本，替换变量
    // 支持: {temp}, {lunar}, {rate}, {shichen}, {sec}, {date}
    private fun formatClockText(format: String, context: Context): String {
        // 使用唯一占位符替换变量，避免影响时间格式解析
        val placeholders = mapOf(
            "{temp}" to "\u0001TEMP\u0001",
            "{lunar}" to "\u0002LUNAR\u0002",
            "{rate}" to "\u0003RATE\u0003",
            "{shichen}" to "\u0004SHICHEN\u0004",
            "{sec}" to "\u0005SEC\u0005",
            "{date}" to "\u0006DATE\u0006"
        )
        
        // 1. 替换变量为占位符
        var processedFormat = format
        for ((variable, placeholder) in placeholders) {
            processedFormat = processedFormat.replace(variable, placeholder)
        }
        
        // 2. 尝试用 DateTimeFormatter 解析整个格式
        var result: String
        try {
            val formatter = DateTimeFormatter.ofPattern(processedFormat)
            result = formatter.format(LocalDateTime.now())
        } catch (_: Throwable) {
            // 如果失败，尝试提取时间部分单独格式化
            val timeOnly = processedFormat
                .replace("\u0001TEMP\u0001", "")
                .replace("\u0002LUNAR\u0002", "")
                .replace("\u0003RATE\u0003", "")
                .replace("\u0004SHICHEN\u0004", "")
                .replace("\u0005SEC\u0005", "")
                .replace("\u0006DATE\u0006", "")
                .trim()
            
            if (timeOnly.isNotEmpty()) {
                try {
                    val timeFormatter = DateTimeFormatter.ofPattern(timeOnly)
                    val formattedTime = timeFormatter.format(LocalDateTime.now())
                    result = processedFormat.replace(timeOnly, formattedTime)
                } catch (_: Throwable) {
                    // 时间格式无效，保留原始格式
                    result = processedFormat
                }
            } else {
                result = processedFormat
            }
        }
        
        // 3. 替换占位符为实际值
        if (result.contains("\u0001TEMP\u0001")) {
            result = result.replace("\u0001TEMP\u0001", getBatteryTempText(context) ?: "")
        }
        if (result.contains("\u0002LUNAR\u0002")) {
            result = result.replace("\u0002LUNAR\u0002", getLunarDate())
        }
        if (result.contains("\u0003RATE\u0003")) {
            result = result.replace("\u0003RATE\u0003", getRefreshRate(context))
        }
        if (result.contains("\u0004SHICHEN\u0004")) {
            result = result.replace("\u0004SHICHEN\u0004", getChineseTimeHour())
        }
        if (result.contains("\u0005SEC\u0005")) {
            result = result.replace("\u0005SEC\u0005", getSeconds())
        }
        if (result.contains("\u0006DATE\u0006")) {
            result = result.replace("\u0006DATE\u0006", getSimpleDate())
        }
        
        return result
    }

    // 获取电池温度（带1秒缓存）
    private var lastTempText: String? = null
    private var lastTempTime: Long = 0
    
    private fun getBatteryTempText(context: Context): String? {
        val now = System.currentTimeMillis()
        
        // 1秒内返回缓存
        if (lastTempText != null && (now - lastTempTime) < 1000) {
            return lastTempText
        }
        
        // 方法1：尝试从系统文件读取（更实时）
        val sysTemp = readSysTemp()
        if (sysTemp != null) {
            lastTempText = sysTemp
            lastTempTime = now
            return sysTemp
        }
        
        // 方法2：使用 BatteryManager 广播
        return try {
            val appContext = context.applicationContext
            val batteryIntent = appContext.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            
            if (batteryIntent != null) {
                val tempRaw = batteryIntent.getIntExtra("temperature", 0)
                if (tempRaw > 0) {
                    val tempCelsius = tempRaw / 10.0f
                    val tempText = String.format(Locale.getDefault(), "%.1f°C", tempCelsius)
                    lastTempText = tempText
                    lastTempTime = now
                    tempText
                } else {
                    lastTempText
                }
            } else {
                lastTempText
            }
        } catch (_: Throwable) {
            lastTempText
        }
    }
    
    // 从系统文件读取温度（更实时）
    private fun readSysTemp(): String? {
        // 常见的电池温度文件路径
        val tempPaths = listOf(
            "/sys/class/power_supply/battery/temp",           // 通用
//          "/sys/class/thermal/thermal_zone0/temp",          // CPU/电池温度
//          "/sys/class/thermal/thermal_zone1/temp",
        )
        
        for (path in tempPaths) {
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    val content = file.readText().trim()
                    val tempRaw = content.toIntOrNull() ?: continue
                    // 不同路径的单位可能不同，通常是毫摄氏度或十分之一摄氏度
                    val tempCelsius = if (tempRaw > 1000) {
                        tempRaw / 1000.0f  // 毫摄氏度
                    } else if (tempRaw > 100) {
                        tempRaw / 10.0f    // 十分之一摄氏度
                    } else {
                        tempRaw.toFloat()  // 摄氏度
                    }
                    if (tempCelsius in 0.0f..100.0f) {
                        return String.format(Locale.getDefault(), "%.1f°C", tempCelsius)
                    }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun getDateStyleText(context: Context, style: Int): String? {
        return when (style) {
            0 -> getSimpleDate()           // 月/日 星期
            1 -> getLunarDate()            // 农历日期
            2 -> getBatteryTempText(context)  // 电池温度（复用缓存）
            3 -> getRefreshRate(context)   // 屏幕刷新率
            4 -> getChineseTimeHour()      // 中国时辰
            5 -> getSeconds()              // 秒数
            else -> null
        }
    }

    private fun getSimpleDate(): String {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val weekdays = arrayOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val weekday = weekdays[calendar.get(Calendar.DAY_OF_WEEK)]
        return "$month/$day $weekday"
    }

    private fun getLunarDate(): String {
        return try {
            TraditionalChineseCalendar.getMonthAndDay()
        } catch (_: Throwable) {
            getSimpleDate()
        }
    }

    private fun getRefreshRate(context: Context): String {
        val refreshRate = context.display?.refreshRate?.toInt() ?: -1
        return "${refreshRate}Hz"
    }

    private fun getChineseTimeHour(): String {
        val hour = LocalTime.now().hour
        return when (hour) {
            0, 23 -> "子时"
            1, 2 -> "丑时"
            3, 4 -> "寅时"
            5, 6 -> "卯时"
            7, 8 -> "辰时"
            9, 10 -> "巳时"
            11, 12 -> "午时"
            13, 14 -> "未时"
            15, 16 -> "申时"
            17, 18 -> "酉时"
            19, 20 -> "戌时"
            21, 22 -> "亥时"
            else -> "未知"
        }
    }

    private fun getSeconds(): String {
        return "${LocalTime.now().second}s"
    }

    fun hideSecureFolderStatusBarIcon(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0] == "managed_profile") {
                    param.result = null
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                findAndHookMethod(
                    "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl",
                    loadPackageParam.classLoader,
                    "setIcon",
                    String::class.java,
                    "com.android.systemui.statusbar.phone.StatusBarIconHolder",
                    callback
                )
            } else {
                findAndHookMethod(
                    "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
                    loadPackageParam.classLoader,
                    "setIcon",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    CharSequence::class.java,
                    callback
                )
            }
        } catch (t: Throwable) {
            logError("hideSecureFolderStatusBarIcon failed", t)
        }
    }

    fun setStatusBarMaxNotificationIcons(loadPackageParam: LoadPackageParam, max: Int) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            max < 0 ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.NotificationIconContainer",
                loadPackageParam.classLoader,
                "shouldForceOverflow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[3] = max
                    }
                }
            )

            findAndHookMethod(
                "com.android.systemui.statusbar.phone.NotificationIconContainer",
                loadPackageParam.classLoader,
                "initResources",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        setIntField(param.thisObject, "mMaxStaticIcons", Int.MAX_VALUE)
                    }
                }
            )
        } catch (t: Throwable) {
            logError("setStatusBarMaxNotificationIcons failed", t)
        }
    }


    fun doubleTapStatusBarToSleep(loadPackageParam: LoadPackageParam) {
        val callback = object : XC_MethodHook() {
            var lastTapTime = 0L

            override fun beforeHookedMethod(param: MethodHookParam) {
                val event = param.args[0] as MotionEvent
                if (event.action != MotionEvent.ACTION_DOWN) {
                    return
                }
                val currentTime = System.nanoTime()
                val interval = currentTime - lastTapTime
                if (interval >= 40_000_000L && interval <= 300_000_000L) {
                    lastTapTime = 0L
                    val view = param.thisObject as View
                    lockScreen(view.context)
                    param.result = true
                } else {
                    lastTapTime = currentTime
                }
            }

            fun lockScreen(context: Context) {
                val powerManager = context.getSystemService(PowerManager::class.java)
                callMethod(powerManager, "goToSleep", SystemClock.uptimeMillis())
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                loadPackageParam.classLoader,
                "onTouchEvent",
                MotionEvent::class.java,
                callback
            )
        } catch (t: Throwable) {
            logError("doubleTapStatusBarToSleep failed", t)
        }
    }


    fun showTraditionalChineseDateOnQS(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        try {
            val qsShortenDateClass = findClass(
                "com.android.systemui.statusbar.policy.QSShortenDate",
                loadPackageParam.classLoader
            )
            findAndHookConstructor(
                qsShortenDateClass,
                Context::class.java,
                AttributeSet::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val textView = param.thisObject as TextView
                        textView.apply {
                            isSingleLine = false
                            setLines(2)
                            setPadding(paddingLeft, -10, paddingRight, -10)
                            setLineSpacing(0f, 0.8f)
                            val density = context.resources.displayMetrics.density
                            translationY = -10 * density
                        }
                    }
                }
            )
            findAndHookMethod(
                qsShortenDateClass,
                "notifyTimeChanged",
                "com.android.systemui.statusbar.policy.QSClockBellSound",
                object : XC_MethodReplacement() {
                    var previousDate = ""

                    @SuppressLint("SetTextI18n")
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val shortDateText = getObjectField(param.args[0], "ShortDateText") as String
                        if (shortDateText == previousDate) return null
                        previousDate = shortDateText
                        val traditionalChineseDate = TraditionalChineseCalendar.getMonthAndDay()
                        val dateTextView = param.thisObject as TextView
                        dateTextView.text = "$shortDateText\n$traditionalChineseDate"
                        return null
                    }
                }
            )
        } catch (t: Throwable) {
            logError("showTraditionalChineseDateOnQS failed", t)
        }
    }

    fun addVolumeProgressToQsBar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        var textView: TextView? = null

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val slider = getObjectField(param.thisObject, "mSlider") as View
                    val sliderParent = slider.parent as FrameLayout
                    textView = TextView(sliderParent.context).apply {
                        setTextColor(Color.WHITE)
                        val volumeSeekBar = getObjectField(param.thisObject, "mVolumeSeekBar")
                        val progress = getIntField(volumeSeekBar, "progress")
                        text = progress.toString()
                    }
                    val layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        marginEnd = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            8.0f,
                            sliderParent.context.resources.displayMetrics
                        ).roundToInt()
                    }
                    sliderParent.addView(textView, layoutParams)
                } catch (t: Throwable) {
                    logError("addVolumeProgressToQsBar inflateViews callback failed", t)
                }
            }
        }

        try {
            findAndHookMethod(
                "com.android.systemui.qs.bar.VolumeBar",
                loadPackageParam.classLoader,
                "inflateViews",
                ViewGroup::class.java,
                callback
            )
            findAndHookMethod(
                "com.android.systemui.qs.bar.VolumeToggleSeekBar\$VolumeSeekbarChangeListener",
                loadPackageParam.classLoader,
                "onProgressChanged",
                SeekBar::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        textView?.text = param.args[1].toString()
                    }
                }
            )
        } catch (t: Throwable) {
            logError("addVolumeProgressToQsBar failed", t)
        }
    }


    fun addBrightnessProgressToQsBar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return
        val textViewList = mutableListOf<TextView>()

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val view = getObjectField(param.thisObject, "mView")
                    val slider = getObjectField(view, "mSlider") as View
                    val frameLayout = slider.parent as FrameLayout

                    val textView = TextView(frameLayout.context).apply {
                        setTextColor(Color.WHITE)
                    }
                    val layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        marginEnd = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            8.0f,
                            frameLayout.context.resources.displayMetrics
                        ).roundToInt()
                    }
                    textViewList.add(textView)
                    frameLayout.addView(textView, layoutParams)
                } catch (t: Throwable) {
                    logError("addBrightnessProgressToQsBar onViewAttached callback failed", t)
                }
            }
        }

        try {
            findAndHookMethod(
                "com.android.systemui.settings.brightness.BrightnessSliderController",
                loadPackageParam.classLoader,
                "onViewAttached",
                callback
            )

            findAndHookMethod(
                "com.android.systemui.settings.brightness.BrightnessSliderController$2",
                loadPackageParam.classLoader,
                "onProgressChanged",
                SeekBar::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val progress = param.args[1].toString()
                        textViewList.forEach {
                            it.text = progress
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            logError("addBrightnessProgressToQsBar failed", t)
        }
    }

    fun hideAODStatusBar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            return
        }
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val batteryView = getObjectField(param.thisObject, "mView") as View
                    if (batteryView.tag == "PluginFaceWidgetManager") {
                        val parentView = batteryView.parent.parent as View
                        parentView.visibility = View.GONE
                    }
                } catch (t: Throwable) {
                    logError("hideAODStatusBar callback failed", t)
                }
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.battery.BatteryMeterViewController",
                loadPackageParam.classLoader,
                "onViewAttached",
                callback
            )
        } catch (t: Throwable) {
            logError("hideAODStatusBar failed", t)
        }
    }

    fun aodLockSupportLunar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return

        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.args[0] == "CscFeature_Calendar_EnableLocalHolidayDisplay") {
                    param.result = "CHINA"
                }
            }
        }

        try {
            findAndHookMethod(
                "com.samsung.android.feature.SemCscFeature",
                loadPackageParam.classLoader,
                "getString",
                String::class.java,
                String::class.java,
                callback
            )

            findAndHookMethod(
                "com.samsung.android.feature.SemCscFeature",
                loadPackageParam.classLoader,
                "getString",
                String::class.java,
                callback
            )
        } catch (t: Throwable) {
            logError("aodLockSupportLunar failed", t)
        }
    }

    // 谷歌即圈即搜 - 启用中国区 CTS 支持
    fun enableGoogleSearch(loadPackageParam: LoadPackageParam, enabled: Boolean) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            val settingsHelperClass = findClass(
                "com.android.systemui.util.SettingsHelper",
                loadPackageParam.classLoader
            )

            // isCNSupportCTS 返回 true，启用中国区 CTS 支持
            XposedBridge.hookAllMethods(
                settingsHelperClass,
                "isCNSupportCTS",
                returnConstant(enabled)
            )

            log("Google Search CTS support for CN -> $enabled")
        } catch (t: Throwable) {
            logError("Failed to enable Google Search CTS support", t)
        }
    }

    fun setCustomCarrierName(loadPackageParam: LoadPackageParam, carrierName: String) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "com.android.keyguard.CarrierTextManager", loadPackageParam.classLoader, "postToCallback",
                "com.android.keyguard.CarrierTextManager\$CarrierTextCallbackInfo", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val carrierTextCallbackInfo = param.args[0] ?: return
                            setObjectField(carrierTextCallbackInfo, "carrierText", carrierName)
                            setObjectField(carrierTextCallbackInfo, "carrierTextShort", carrierName)
                        } catch (t: Throwable) {
                            logError("setCustomCarrierName callback error", t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            logError("setCustomCarrierName failed", t)
        }
    }

}
