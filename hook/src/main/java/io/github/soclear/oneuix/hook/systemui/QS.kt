package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.TraditionalChineseCalendar
import io.github.soclear.oneuix.hook.util.DIAG_MARKER
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.diagBoth
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import kotlin.math.roundToInt

@SuppressLint("PrivateApi")
object QS {
    enum class QsBar {
        MediaPlayer,
        NearbyDevicesAndDeviceControl,
        SecurityFooter,
        DataUsage,
        SmartViewAndModes,
    }


    context(xposedModule: XposedModule)
    fun hideDeviceControlQsTile() = afterAttachTry(Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val qsTileHostClass = classLoader.loadClass("com.android.systemui.qs.QSTileHost")
        val createTileMethod = qsTileHostClass.getDeclaredMethod("createTile", String::class.java)
        xposedModule.hook(createTileMethod).intercept { chain ->
            if (chain.args.firstOrNull() == "DeviceControl") {
                null
            } else {
                chain.proceed()
            }
        }
    }

    context(xposedModule: XposedModule)
    fun hideSmartViewQsTile() = afterAttachTry(Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val qsTileHostClass = classLoader.loadClass("com.android.systemui.qs.QSTileHost")
        val createTileMethod = qsTileHostClass.getDeclaredMethod("createTile", String::class.java)
        xposedModule.hook(createTileMethod).intercept { chain ->
            if (chain.args.firstOrNull() == "custom(com.samsung.android.smartmirroring/.tile.SmartMirroringTile)") {
                null
            } else {
                chain.proceed()
            }
        }
    }

    // related classes: BarFactory BarController  BarOrderInteractor
    context(xposedModule: XposedModule)
    fun hideQsBar(qsBarSet: Set<QsBar>) =
        afterAttachTry(qsBarSet.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val hideBarCallback = XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val view = chain.thisObject.reflect["mBarRootView"] as? View
                view?.visibility = View.GONE
                result
            }

            if (QsBar.NearbyDevicesAndDeviceControl in qsBarSet && ONE_UI_VERSION < 80500) {
                try {
                    if (applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                        val bottomLargeTileBarClass = classLoader.loadClass(
                            "com.android.systemui.qs.bar.BottomLargeTileBar"
                        )
                        val showBarMethod = bottomLargeTileBarClass.getDeclaredMethod(
                            "showBar",
                            Boolean::class.javaPrimitiveType
                        )
                        xposedModule.hook(showBarMethod).intercept(hideBarCallback)
                    } else {
                        val largeTileBarClass = classLoader.loadClass(
                            "com.android.systemui.qs.bar.LargeTileBar"
                        )
                        val updateLayoutMethod = largeTileBarClass.getDeclaredMethod(
                            "updateLayout",
                            LinearLayout::class.java
                        )
                        xposedModule.hook(updateLayoutMethod).intercept { chain ->
                            val result = chain.proceed()
                            val string = chain.thisObject.reflect["TAG"] as? String
                            if (string == "BottomLargeTileBar") {
                                val view = chain.thisObject.reflect["mBarRootView"] as? View
                                view?.visibility = View.GONE
                            }
                            result
                        }
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
            }

            if (QsBar.MediaPlayer in qsBarSet && ONE_UI_VERSION < 80500) {
                try {
                    val qsMediaPlayerBarClass = classLoader.loadClass(
                        "com.android.systemui.qs.bar.QSMediaPlayerBar"
                    )
                    val inflateViewsMethod = qsMediaPlayerBarClass.getDeclaredMethod(
                        "inflateViews",
                        ViewGroup::class.java
                    )
                    xposedModule.hook(inflateViewsMethod).intercept(hideBarCallback)
                } catch (t: Throwable) {
                    xlog(t)
                }
            }

            if (QsBar.SecurityFooter in qsBarSet) {
                try {
                    if (ONE_UI_VERSION >= 80500) {
                        val bottomBannerTransformClass = runCatching {
                            classLoader.loadClass(
                                $$"com.android.systemui.samsung.quicksetting.ui.banner.BottomBannerViewModel$1$1"
                            )
                        }.getOrNull()
                        bottomBannerTransformClass?.declaredMethods
                            ?.filter { it.name == "invoke" }
                            ?.forEach { method ->
                                xposedModule.hook(method).intercept { chain ->
                                    if (chain.args.getOrNull(1) is Boolean) {
                                        val newArgs = chain.args.toTypedArray()
                                        newArgs[1] = false
                                        chain.proceed(newArgs)
                                    } else {
                                        chain.proceed()
                                    }
                                }
                            }
                    } else if (applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                        val barItemImplClass = classLoader.loadClass(
                            "com.android.systemui.qs.bar.BarItemImpl"
                        )
                        val showBarMethod = barItemImplClass.getDeclaredMethod(
                            "showBar",
                            Boolean::class.javaPrimitiveType
                        )
                        xposedModule.hook(showBarMethod).intercept { chain ->
                            val tag = chain.thisObject.reflect["TAG"]
                            if (tag == "SecurityFooterBar") {
                                val newArgs = chain.args.toTypedArray()
                                newArgs[0] = false
                                chain.proceed(newArgs)
                            } else {
                                chain.proceed()
                            }
                        }
                    } else {
                        val securityFooterBarClass = classLoader.loadClass(
                            "com.android.systemui.qs.bar.SecurityFooterBar"
                        )
                        val onVisibilityChangedMethod = securityFooterBarClass.getDeclaredMethod(
                            "onVisibilityChanged",
                            Int::class.javaPrimitiveType
                        )
                        xposedModule.hook(onVisibilityChangedMethod).intercept(hideBarCallback)
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
            }

            if (QsBar.DataUsage in qsBarSet) {
                try {
                    if (ONE_UI_VERSION >= 80500) {
                        val bottomBannerTransformClass = runCatching {
                            classLoader.loadClass(
                                $$"com.android.systemui.samsung.quicksetting.ui.banner.BottomBannerViewModel$1$1"
                            )
                        }.getOrNull()
                        bottomBannerTransformClass?.declaredMethods
                            ?.filter { it.name == "invoke" }
                            ?.forEach { method ->
                                xposedModule.hook(method).intercept { chain ->
                                    if (chain.args.getOrNull(2) is Boolean) {
                                        val newArgs = chain.args.toTypedArray()
                                        newArgs[2] = false
                                        chain.proceed(newArgs)
                                    } else {
                                        chain.proceed()
                                    }
                                }
                            }
                    } else {
                        val dataUsageBarClass = classLoader.loadClass(
                            "com.android.systemui.qs.bar.DataUsageBar"
                        )
                        val isAvailableMethod = dataUsageBarClass.getDeclaredMethod("isAvailable")
                        xposedModule.hook(isAvailableMethod).intercept { false }
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
            }

            if (QsBar.SmartViewAndModes in qsBarSet && ONE_UI_VERSION < 80500) {
                try {
                    val barItemImplClass = classLoader.loadClass(
                        "com.android.systemui.qs.bar.BarItemImpl"
                    )
                    val showBarMethod = barItemImplClass.getDeclaredMethod(
                        "showBar",
                        Boolean::class.javaPrimitiveType
                    )
                    xposedModule.hook(showBarMethod).intercept { chain ->
                        val tag = chain.thisObject.reflect["TAG"]
                        if (tag == "SmartViewLargeTileBar") {
                            val newArgs = chain.args.toTypedArray()
                            newArgs[0] = false
                            chain.proceed(newArgs)
                        } else {
                            chain.proceed()
                        }
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
            }

            // 横屏
            try {
                if (ONE_UI_VERSION >= 80500) return@afterAttachTry
                val nearbyDevicesAndDeviceControl = QsBar.NearbyDevicesAndDeviceControl in qsBarSet
                val smartViewAndModes = QsBar.SmartViewAndModes in qsBarSet
                if (!nearbyDevicesAndDeviceControl && !smartViewAndModes) {
                    return@afterAttachTry
                }
                val topLargeTileBarClass = classLoader.loadClass(
                    "com.android.systemui.qs.bar.TopLargeTileBar"
                )
                val tileRecordClass = classLoader.loadClass(
                    $$"com.android.systemui.qs.SecQSPanelControllerBase$TileRecord"
                )
                val addTileMethod = topLargeTileBarClass.getDeclaredMethod("addTile", tileRecordClass)
                xposedModule.hook(addTileMethod).intercept { chain ->
                    val tile = chain.args[0]?.reflect?.get("tile")
                    val tileSpec = tile?.reflect?.call("getTileSpec")
                    val flag = when (tileSpec) {
                        "DeviceControl" if nearbyDevicesAndDeviceControl -> true
                        "custom(com.samsung.android.mydevice/.quicksettings.MyDeviceTileService)" if nearbyDevicesAndDeviceControl -> true
                        "custom(com.samsung.android.smartmirroring/.tile.SmartMirroringTile)" if smartViewAndModes -> true
                        "custom(com.samsung.android.app.routines/.LifestyleModeTile)" if smartViewAndModes -> true
                        else -> false
                    }
                    if (flag) {
                        null
                    } else {
                        chain.proceed()
                    }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }

    context(xposedModule: XposedModule)
    fun alwaysExpandQsTileChunk() =
        afterAttachTry(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && ONE_UI_VERSION < 80500) {
            try {
                val tileChunkLayoutBarClass = classLoader.loadClass(
                    "com.android.systemui.qs.bar.TileChunkLayoutBar"
                )
                val setContainerHeightMethod = tileChunkLayoutBarClass.getDeclaredMethod(
                    "setContainerHeight",
                    Int::class.javaPrimitiveType
                )
                xposedModule.hook(setContainerHeightMethod).intercept { chain ->
                    val expandedHeight = chain.thisObject.reflect["mContainerExpandedHeight"] as? Int
                    if (expandedHeight != null) {
                        val newArgs = chain.args.toTypedArray()
                        newArgs[0] = expandedHeight
                        chain.proceed(newArgs)
                    } else {
                        chain.proceed()
                    }
                }

                val inflateViewsMethod = tileChunkLayoutBarClass.getDeclaredMethod(
                    "inflateViews",
                    ViewGroup::class.java
                )
                xposedModule.hook(inflateViewsMethod).intercept { chain ->
                    val result = chain.proceed()
                    val scrollIndicator = chain.thisObject.reflect["mScrollIndicatorClickContainer"] as? View
                    scrollIndicator?.visibility = View.GONE
                    result
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }

    context(xposedModule: XposedModule)
    fun alwaysShowTimeDateOnQs() =
        afterAttachTry(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && ONE_UI_VERSION < 80500) {
            val isLegacyAnimator = applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA
            try {
                // The panel transition owns clockDateContainer, independently of the expand animator.
                val panelTransitionAnimatorClass = classLoader.loadClass(
                    "com.android.systemui.qs.animator.PanelTransitionAnimator"
                )
                val qsClass = classLoader.loadClass("com.android.systemui.plugins.qs.QS")
                val setQsMethod = panelTransitionAnimatorClass.reflect.findMethodExact("setQs", qsClass)
                xposedModule.hook(setQsMethod).intercept { chain ->
                    val result = chain.proceed()
                    if (isLegacyAnimator) {
                        chain.thisObject.reflect["clockDateContainer"] = null
                    } else {
                        val context = chain.thisObject.reflect["context"] as? Context
                        if (context != null) {
                            chain.thisObject.reflect["clockDateContainer"] = View(context)
                        }
                    }
                    result
                }
            } catch (t: Throwable) {
                xlog(t)
            }

            try {
                val callback = XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    val mContext = chain.thisObject.reflect["mContext"] as? Context
                    if (mContext != null) {
                        chain.thisObject.reflect["mClockDateContainer"] = View(mContext)
                    }
                    result
                }
                val expandAnimatorClass = classLoader.loadClass(
                    if (isLegacyAnimator) {
                        "com.android.systemui.qs.animator.LegacyQsExpandAnimator"
                    } else {
                        "com.android.systemui.qs.animator.QsExpandAnimator"
                    }
                )
                val method = expandAnimatorClass.getDeclaredMethod(
                    if (isLegacyAnimator) $$"updateViews$2" else "updateViews"
                )
                xposedModule.hook(method).intercept(callback)
            } catch (t: Throwable) {
                xlog(t)
            }
        }

    context(xposedModule: XposedModule)
    fun setQsClockStyle(
        monospaced: Boolean,
        modifyTextSize: Boolean,
        textSize: Float
    ) = afterAttachTry(monospaced || modifyTextSize) {
        // 布局在 res/layout/sec_qqs_date_buttons.xml
        val headerClass = classLoader.loadClass(
            "com.android.systemui.qs.SecQuickStatusBarHeader"
        )
        val onFinishInflateMethod = headerClass.getDeclaredMethod("onFinishInflate")
        xposedModule.hook(onFinishInflateMethod).intercept { chain ->
            val result = chain.proceed()
            try {
                val clockView = chain.thisObject.reflect["mClockView"] as? TextView
                if (clockView != null) {
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
                        clockView.setPadding(
                            clockView.paddingLeft,
                            padding,
                            clockView.paddingRight,
                            padding
                        )
                    }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
            result
        }
    }

    context(xposedModule: XposedModule)
    fun supportOutdoorMode() {
        val outdoorModeRowTag = "io.github.soclear.oneuix.outdoor_mode_row"

        fun isOutdoorModeEnabled(context: Context): Boolean {
            return (android.provider.Settings.System::class.java.reflect.call(
                "getIntForUser",
                context.contentResolver,
                "display_outdoor_mode",
                0,
                -2
            ) as Int) != 0
        }

        fun setOutdoorModeEnabled(context: Context, enabled: Boolean) {
            android.provider.Settings.System::class.java.reflect.call(
                "putIntForUser",
                context.contentResolver,
                "display_outdoor_mode",
                if (enabled) 1 else 0,
                -2
            )
        }

        @SuppressLint("DiscouragedApi")
        fun addOutdoorModeRow(
            context: Context,
            detailView: ViewGroup,
            switchPreferenceClass: Class<*>
        ) {
            try {
                val outdoorContainer = switchPreferenceClass.reflect.call(
                    "inflateSwitch",
                    context,
                    detailView
                ) as View
                outdoorContainer.tag = outdoorModeRowTag

                val res = context.resources
                val titleId = res.getIdentifier(
                    "sec_brightness_outdoor_mode_title",
                    "string",
                    Package.SYSTEMUI
                )
                val summaryId = res.getIdentifier(
                    "sec_brightness_outdoor_mode_summary",
                    "string",
                    Package.SYSTEMUI
                )
                val titleViewId = res.getIdentifier("title", "id", Package.SYSTEMUI)
                val summaryViewId = res.getIdentifier("title_summary", "id", Package.SYSTEMUI)
                val switchViewId = res.getIdentifier("title_switch", "id", Package.SYSTEMUI)
                if (titleId == 0 || titleViewId == 0 || switchViewId == 0) return

                outdoorContainer.findViewById<TextView>(titleViewId)?.text =
                    res.getString(titleId)

                outdoorContainer.findViewById<TextView>(summaryViewId)?.apply {
                    text = if (summaryId != 0) res.getString(summaryId) else ""
                    visibility = if (summaryId != 0) View.VISIBLE else View.GONE
                }

                val outdoorSwitch: CompoundButton? = outdoorContainer.findViewById(switchViewId)
                outdoorSwitch?.isChecked = isOutdoorModeEnabled(context)
                outdoorSwitch?.setOnCheckedChangeListener { _, isChecked ->
                    setOutdoorModeEnabled(context, isChecked)
                }
                outdoorContainer.setOnClickListener {
                    val switch = outdoorSwitch ?: return@setOnClickListener
                    switch.isChecked = !switch.isChecked
                }

                // Keep the row directly below Samsung's Adaptive brightness row.
                val index = minOf(2, detailView.childCount)
                detailView.addView(outdoorContainer, index)
            } catch (t: Throwable) {
                xlog(t)
            }
        }

        afterAttachTry {
            val switchPreferenceClass = classLoader.loadClass(
                "com.android.systemui.qs.SecQSSwitchPreference"
            )
            val brightnessDetailClass = runCatching {
                classLoader.loadClass("com.android.systemui.settings.brightness.BrightnessDetailAdapter")
            }.getOrNull() ?: runCatching {
                classLoader.loadClass($$"com.android.systemui.settings.brightness.BrightnessDetail$1")
            }.getOrNull()

            if (brightnessDetailClass != null) {
                val createDetailViewMethod = brightnessDetailClass.getDeclaredMethod(
                    "createDetailView",
                    Context::class.java,
                    View::class.java,
                    ViewGroup::class.java
                )
                xposedModule.hook(createDetailViewMethod).intercept { chain ->
                    val result = chain.proceed()
                    val context = chain.args[0] as Context
                    val detailView = result as? ViewGroup
                    if (detailView != null) {
                        addOutdoorModeRow(context, detailView, switchPreferenceClass)
                    }
                    result
                }
            }
        }
    }

    context(xposedModule: XposedModule)
    fun showTraditionalChineseDateOnQS() = afterAttachTry(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    ) {
        val qsShortenDateClass = classLoader.loadClass(
            "com.android.systemui.statusbar.policy.QSShortenDate"
        )
        qsShortenDateClass.declaredConstructors.forEach { constructor ->
            xposedModule.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                val textView = chain.thisObject as? TextView
                textView?.apply {
                    isSingleLine = false
                    setLines(2)
                    ellipsize = null
                    setPadding(paddingLeft, -10, paddingRight, -10)
                    setLineSpacing(0f, 0.8f)
                    val density = context.resources.displayMetrics.density
                    translationY = -10 * density
                }
                result
            }
        }

        val qsClockBellSoundClass = classLoader.loadClass(
            "com.android.systemui.statusbar.policy.QSClockBellSound"
        )
        val notifyTimeChangedMethod = qsShortenDateClass.getDeclaredMethod(
            "notifyTimeChanged",
            qsClockBellSoundClass
        )
        var previousDate = ""
        var result = ""
        xposedModule.hook(notifyTimeChangedMethod).intercept { chain ->
            val shortDateText = chain.args[0]?.reflect?.get("ShortDateText") as? String ?: ""
            if (shortDateText != previousDate) {
                previousDate = shortDateText
                result = "$shortDateText\n${TraditionalChineseCalendar.getMonthAndDay()}"
            }
            val dateTextView = chain.thisObject as? TextView
            if (dateTextView?.text != result) {
                dateTextView?.text = result
            }
            null
        }
    }

    context(xposedModule: XposedModule)
    fun addVolumeProgressToQsBar() = afterAttachTry(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && ONE_UI_VERSION < 80500
    ) {
        var textView: TextView? = null

        val volumeBarClass = classLoader.loadClass("com.android.systemui.qs.bar.VolumeBar")
        val inflateViewsMethod = volumeBarClass.getDeclaredMethod("inflateViews", ViewGroup::class.java)
        xposedModule.hook(inflateViewsMethod).intercept { chain ->
            val result = chain.proceed()
            try {
                val slider = chain.thisObject.reflect["mSlider"] as? View
                val sliderParent = slider?.parent as? FrameLayout
                if (sliderParent != null) {
                    val volumeSeekBar = chain.thisObject.reflect["mVolumeSeekBar"]
                    val progress = volumeSeekBar?.reflect?.get("progress") as? Int ?: 0
                    textView = TextView(sliderParent.context).apply {
                        setTextColor(Color.WHITE)
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
                }
            } catch (t: Throwable) {
                xlog(t)
            }
            result
        }

        val listenerClass = classLoader.loadClass(
            $$"com.android.systemui.qs.bar.VolumeToggleSeekBar$VolumeSeekbarChangeListener"
        )
        val onProgressChangedMethod = listenerClass.getDeclaredMethod(
            "onProgressChanged",
            SeekBar::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        xposedModule.hook(onProgressChangedMethod).intercept { chain ->
            val result = chain.proceed()
            textView?.text = chain.args[1].toString()
            result
        }
    }

    context(xposedModule: XposedModule)
    fun addBrightnessProgressToQsBar() = afterAttachTry(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && ONE_UI_VERSION < 80500
    ) {
        val textViewList = mutableListOf<TextView>()

        val controllerClass = classLoader.loadClass(
            "com.android.systemui.settings.brightness.BrightnessSliderController"
        )
        val onViewAttachedMethod = controllerClass.getDeclaredMethod("onViewAttached")
        xposedModule.hook(onViewAttachedMethod).intercept { chain ->
            val result = chain.proceed()
            try {
                val view = chain.thisObject.reflect["mView"]
                val slider = view?.reflect?.get("mSlider") as? View
                val frameLayout = slider?.parent as? FrameLayout
                if (frameLayout != null) {
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
                }
            } catch (t: Throwable) {
                xlog(t)
            }
            result
        }

        val listenerClass = classLoader.loadClass(
            $$"com.android.systemui.settings.brightness.BrightnessSliderController$2"
        )
        val onProgressChangedMethod = listenerClass.getDeclaredMethod(
            "onProgressChanged",
            SeekBar::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        xposedModule.hook(onProgressChangedMethod).intercept { chain ->
            val result = chain.proceed()
            val progress = chain.args[1].toString()
            textViewList.forEach {
                it.text = progress
            }
            result
        }
    }

    // ------------------------------------------------------------------
    // 去掉 SystemUI 对 bar tile 自定义图额外套的 Modifier.alpha(0.5f)
    //
    // 反编译证据（One UI 8 / SM-S9280ZCU5DZDP）：
    //   IconProgressBarWithTile.kt:140   亮度/音量        Image(fillMaxSize().clip(shape).alpha(0.5f))
    //   QuickTileBox.kt:753              按钮背景        同上
    //   EmptyMediaPlayer.kt:528          媒体播放器(空态) 同上
    // quick tile（QuickTileKt.kt）没有这一层 alpha，所以 bar 才显得更淡。
    //
    // 手法：AlphaKt.alpha(Modifier, float) 是普通 static 方法，Compose 自己就有
    // 「alpha == 1f 直接 return 入参」的短路分支，这里命中时直接返回 args[0]，
    // 等价于这一层 alpha 不存在，不改变任何其它渲染语义。
    // ------------------------------------------------------------------
    private val BAR_TILE_ALPHA_CALLERS = arrayOf(
        "com.android.systemui.samsung.quicksetting.ui.tiles.IconProgressBarWithTileKt",
        "com.android.systemui.samsung.quicksetting.ui.tiles.QuickTileBoxKt\$\$ExternalSyntheticLambda8",
        "com.android.systemui.samsung.quicksetting.ui.tiles.EmptyMediaPlayerKt\$\$ExternalSyntheticLambda6",
    )

    private fun callerIsQsBarTile(): Boolean {
        val st = Throwable().stackTrace
        for (i in 1 until minOf(st.size, 12)) {
            val name = st[i].className
            // 精确类名优先；退化为按包前缀判断，避免 Compose 编译产物改名导致漏命中
            // 只认这三个 bar tile 的类（含其 lambda 类名），
            // 不要用整个 quicksetting 包前缀，否则会误伤夜间模式的整体变暗遮罩
            if (name.contains("IconProgressBarWithTileKt")) return true
            if (name.contains("QuickTileBoxKt")) return true
            if (name.contains("EmptyMediaPlayerKt")) return true
        }
        return false
    }

    // ---------------------------------------------------------------
    // 去掉 bar tile 的 alpha(0.5)：让 亮度/音量/按钮背景/媒体播放器 的自定义图
    // 与 quick tile 一样实。几何上报（QsGeom）已废弃 —— 几何改走 SystemUI
    // DataStore 的 grid 规格，进程内语义树上报不再需要，这里只留 alpha hook。
    // DIAG=true 只在诊断构建里用，正式版必须是 false（不往 logcat 写任何东西）。
    // ---------------------------------------------------------------
    const val DIAG = false

    context(xposedModule: XposedModule)
    fun removeQsBarTileAlpha() = afterAttachTry(Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        val alphaKt = classLoader.loadClass("androidx.compose.ui.draw.AlphaKt")
        // 用反射拿方法，避免给 hook 模块引入 compose 编译期依赖
        val alphaMethod = alphaKt.declaredMethods.firstOrNull {
            it.name == "alpha" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[1] == Float::class.javaPrimitiveType
        } ?: run {
            if (DIAG) android.util.Log.e("OneUIXDiag", "[$DIAG_MARKER] alpha: AlphaKt.alpha(Modifier,float) NOT FOUND")
            return@afterAttachTry
        }
        if (DIAG) android.util.Log.e("OneUIXDiag", "[$DIAG_MARKER] alpha: hook installed -> " + alphaMethod)
        val hit = java.util.concurrent.atomic.AtomicInteger(0)
        val seen = java.util.concurrent.atomic.AtomicInteger(0)
        xposedModule.hook(alphaMethod).intercept { chain ->
            val alpha = chain.args[1] as Float
            if (DIAG && alpha == 0.5f) {
                val n = seen.incrementAndGet()
                if (n <= 6) {
                    val st = Throwable().stackTrace
                    android.util.Log.e(
                        "OneUIXDiag",
                        "[$DIAG_MARKER] alpha 0.5f #$n caller=" + (st.getOrNull(1)?.className ?: "?") +
                            " whitelisted=" + callerIsQsBarTile()
                    )
                }
            }
            if (alpha == 0.5f && callerIsQsBarTile()) {
                if (DIAG && hit.incrementAndGet() == 1) {
                    android.util.Log.e("OneUIXDiag", "[$DIAG_MARKER] alpha: FIRST HIT -> returned original Modifier")
                }
                chain.args[0]
            } else {
                chain.proceed()
            }
        }
    }

    // ---------------------------------------------------------------
    // 无 UI 刷新 QS 主题
    //
    // 外部一条广播 → 在 SystemUI 进程内冒充 SystemUI 调 QsTuner 的 provider：
    //   call("grantUriPermissions()", bundle("button_info_list_json" = json))
    // 拿到 granted_count 后，再写 Settings.Secure + notifyChange（和 QsTuner 内部流程一致）。
    //
    // 为什么必须在 SystemUI 进程里做：
    //   1) QsTuner 的 provider 有调用方校验：
    //      Rune.canSupportPeaceUX() && !"com.android.systemui".equals(callingPackage) → return null
    //      （shell 里 `content call` 实测拿到 null）
    //   2) shell 的 `content call --extra KEY:TYPE:VALUE` 按 ':' 分段，JSON 里的 content:// 会解析崩
    //   3) 只有拿到 URIGrant 之后 SystemUI 才能读 content://...fileprovider/customImages/*.png，
    //      否则所有 tile 掉回默认样式
    //
    // 刷新的真相：URI 不变 → SystemUI 按 URI 命中解码缓存，图不会更新。
    // 所以这条例行必须配合"换文件名（时间戳命名）"的部署产物，它负责的是"授权 + 触发重读"。
    //
    // 用法（root shell）：
    //   am broadcast -a io.github.soclear.oneuix.action.QS_REFRESH --es json "$(cat list.json)"
    //
    // 回执：<systemui filesDir>/oneuix_qs_refresh.status
    //   （SystemUI 自己的 data 目录 root 可读；不要写 /data/local/tmp，SELinux 会挡）
    // ---------------------------------------------------------------
    const val ACTION_QS_REFRESH = "io.github.soclear.oneuix.action.QS_REFRESH"
    private const val QSTUNER_PROVIDER_URI = "content://com.samsung.android.qstuner.provider"
    private const val KEY_BUTTON_INFO = "quickstar_button_info_list"
    private const val REFRESH_STATUS_FILE = "oneuix_qs_refresh.status"

    context(xposedModule: XposedModule)
    fun registerQsRefreshReceiver() = afterAttachTry {
        val app = this
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                val json = intent.getStringExtra("json")
                if (json == null) {
                    if (DIAG) diagBoth(xposedModule, "QS_REFRESH received without json, ignore")
                    return
                }
                // binder 调用放到后台线程，别占 SystemUI 主线程
                Thread {
                    var err = ""
                    val granted = try {
                        val out = app.contentResolver.call(
                            android.net.Uri.parse(QSTUNER_PROVIDER_URI),
                            "grantUriPermissions()",
                            null,
                            android.os.Bundle().apply { putString("button_info_list_json", json) }
                        )
                        out?.getInt("granted_count", -1) ?: -1
                    } catch (t: Throwable) {
                        err += "call=${t.javaClass.simpleName};"
                        -1
                    }
                    var settingsOk = false
                    try {
                        android.provider.Settings.Secure.putString(
                            app.contentResolver, KEY_BUTTON_INFO, json
                        )
                        app.contentResolver.notifyChange(
                            android.provider.Settings.Secure.getUriFor(KEY_BUTTON_INFO), null
                        )
                        settingsOk = true
                    } catch (t: Throwable) {
                        err += "settings=${t.javaClass.simpleName};"
                    }
                    val ts = System.currentTimeMillis()
                    if (DIAG) {
                        diagBoth(
                            xposedModule,
                            "QS_REFRESH granted=$granted settings=$settingsOk err=$err"
                        )
                    }
                    // 回执必须落盘：正式包不打日志，这是唯一的可观测点
                    runCatching {
                        java.io.File(app.filesDir, REFRESH_STATUS_FILE).writeText(
                            "granted=$granted at $ts\n" +
                                "json_len=${json.length}\n" +
                                "settings=$settingsOk\n" +
                                "err=$err\n"
                        )
                    }
                }.start()
            }
        }
        app.registerReceiver(
            receiver,
            android.content.IntentFilter(ACTION_QS_REFRESH),
            android.content.Context.RECEIVER_EXPORTED
        )
        if (DIAG) diagBoth(xposedModule, "QS_REFRESH receiver registered (exported)")
    }
}
