package io.github.soclear.oneuix.hook.systemui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.TraditionalChineseCalendar
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

object StatusBarClock {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun updateStatusBarClockEverySecond() {
        if (param.packageName != Package.SYSTEMUI) return
        setupSecondUpdate()
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setStatusBarClockStyle(
        format: String,
        needsSecondUpdate: Boolean = false,
    ) {
        if (param.packageName != Package.SYSTEMUI) return

        val autoDetectSecondUpdate = format.contains("ss") ||
            format.contains("SS") ||
            format.contains("{sec}")

        val shouldEnableSecondUpdate = needsSecondUpdate || autoDetectSecondUpdate

        if (shouldEnableSecondUpdate) {
            setupSecondUpdate()
        }

        try {
            val qsClockIndicatorClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockIndicatorView"
            )
            val bellSoundClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockBellSound"
            )
            val method = qsClockIndicatorClass.getDeclaredMethod("notifyTimeChanged", bellSoundClass)
            xposedModule.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    val clockTextView = chain.thisObject as TextView
                    val context = clockTextView.context

                    registerClockView(clockTextView)
                    clockFormat = format

                    if (format.contains("{temp}")) {
                        ensureBatteryReceiver(context)
                    }

                    if (shouldEnableSecondUpdate) {
                        ensureSecondUpdateRunning(context)
                    }

                    val text = formatClockText(format, context)
                    if (clockTextView.text != text) {
                        clockTextView.text = text
                        clockTextView.contentDescription = text
                    }
                } catch (t: Throwable) {
                    xlog(t)
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    private var secondUpdateHandler: Handler? = null
    private var secondUpdateRunnable: Runnable? = null
    private val clockViews: MutableSet<TextView> = Collections.newSetFromMap(WeakHashMap())

    private fun registerClockView(view: TextView) {
        synchronized(clockViews) {
            clockViews.add(view)
        }
    }

    private fun updateAllClockViews() {
        if (clockFormat.isEmpty()) return
        synchronized(clockViews) {
            val iterator = clockViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next()
                if (!view.isAttachedToWindow) {
                    iterator.remove()
                    continue
                }
                try {
                    val text = formatClockText(clockFormat, view.context)
                    if (view.text != text) {
                        view.text = text
                        view.contentDescription = text
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    @Volatile
    private var secondUpdateHooksInstalled = false

    @Volatile
    private var clockFormat: String = ""

    @Volatile
    private var cachedTimeFormatter: DateTimeFormatter? = null
    @Volatile
    private var cachedTimeFormat: String = ""

    @Volatile
    private var cachedLunarDate: String = ""
    @Volatile
    private var cachedLunarDateDay: Int = -1

    @Volatile
    private var cachedSimpleDate: String = ""
    @Volatile
    private var cachedSimpleDateDay: Int = -1

    @Volatile
    private var cachedTempText: String = ""
    @Volatile
    private var isBatteryReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_BATTERY_CHANGED == intent.action) {
                updateTempFromIntent(intent)
            }
        }
    }

    private fun ensureBatteryReceiver(context: Context) {
        if (!isBatteryReceiverRegistered) {
            synchronized(this) {
                if (!isBatteryReceiverRegistered) {
                    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val stickyIntent = context.registerReceiver(batteryReceiver, filter)
                    if (stickyIntent != null) {
                        updateTempFromIntent(stickyIntent)
                    }
                    isBatteryReceiverRegistered = true
                }
            }
        }
    }

    private fun updateTempFromIntent(intent: Intent) {
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (tempRaw != -1) {
            val tempCelsius = tempRaw / 10.0f
            val newTemp = String.format(Locale.getDefault(), "%.1f°C", tempCelsius)
            if (newTemp != cachedTempText) {
                cachedTempText = newTemp
                if (clockFormat.contains("{temp}")) {
                    updateAllClockViews()
                }
            }
        } else {
            val sysTemp = readSysTemp()
            if (sysTemp != null && sysTemp != cachedTempText) {
                cachedTempText = sysTemp
                if (clockFormat.contains("{temp}")) {
                    updateAllClockViews()
                }
            }
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    @Synchronized
    private fun setupSecondUpdate() {
        if (secondUpdateHooksInstalled) return
        secondUpdateHooksInstalled = true

        try {
            val controllerClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockIndicatorViewController"
            )
            val onViewAttachedMethod = controllerClass.getDeclaredMethod("onViewAttached")
            xposedModule.hook(onViewAttachedMethod).intercept { chain ->
                val result = chain.proceed()
                val clockTextView = (chain.thisObject.reflect["mView"] as? TextView)
                    ?: (chain.thisObject.reflect["view"] as? TextView)
                if (clockTextView != null) {
                    clockTextView.fontFeatureSettings = "tnum"
                    registerClockView(clockTextView)
                }
                result
            }
        } catch (_: Throwable) {}

        try {
            val helperClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockQuickStarHelper"
            )
            val updateMethod = helperClass.getDeclaredMethod("updateSecondsClockHandler")
            xposedModule.hook(updateMethod).intercept { chain ->
                val result = chain.proceed()
                val mSecondsHandler = chain.thisObject.reflect["mSecondsHandler"]
                if (mSecondsHandler == null) {
                    val looper = Looper.myLooper()
                    if (looper != null) {
                        val handler = Handler(looper)
                        chain.thisObject.reflect["mSecondsHandler"] = handler
                        val mSecondTick = chain.thisObject.reflect["mSecondTick"] as? Runnable
                        if (mSecondTick != null) {
                            handler.post(mSecondTick)
                        }
                    }
                }
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }

        try {
            val indicatorClass = param.classLoader.loadClass(
                "com.android.systemui.statusbar.policy.QSClockIndicatorView"
            )
            indicatorClass.declaredMethods
                .filter { it.name == "onViewDetached" || it.name == "onDetachedFromWindow" }
                .forEach { method ->
                    xposedModule.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        val clockTextView = chain.thisObject as? TextView
                        if (clockTextView != null) {
                            synchronized(clockViews) {
                                clockViews.remove(clockTextView)
                                if (clockViews.isEmpty()) {
                                    stopSecondUpdate()
                                }
                            }
                        }
                        result
                    }
                }
        } catch (_: Throwable) {}
    }

    private fun ensureSecondUpdateRunning(context: Context) {
        if (secondUpdateHandler != null) return

        val mainHandler = Handler(Looper.getMainLooper())
        secondUpdateHandler = mainHandler
        val runnable = object : Runnable {
            override fun run() {
                val handler = secondUpdateHandler ?: return
                try {
                    val powerManager = context.getSystemService(PowerManager::class.java)
                    if (powerManager?.isInteractive == true && clockFormat.isNotEmpty()) {
                        updateAllClockViews()
                    }
                } catch (_: Throwable) {}
                handler.postDelayed(this, 1000)
            }
        }
        secondUpdateRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopSecondUpdate() {
        secondUpdateRunnable?.let { secondUpdateHandler?.removeCallbacks(it) }
        secondUpdateHandler = null
        secondUpdateRunnable = null
    }

    private fun formatClockText(format: String, context: Context): String {
        var result = format

        var timePattern = format
        if (timePattern.contains("{temp}")) timePattern = timePattern.replace("{temp}", "")
        if (timePattern.contains("{lunar}")) timePattern = timePattern.replace("{lunar}", "")
        if (timePattern.contains("{rate}")) timePattern = timePattern.replace("{rate}", "")
        if (timePattern.contains("{shichen}")) timePattern = timePattern.replace("{shichen}", "")
        if (timePattern.contains("{sec}")) timePattern = timePattern.replace("{sec}", "")
        if (timePattern.contains("{date}")) timePattern = timePattern.replace("{date}", "")
        timePattern = timePattern.trim()

        if (timePattern.isNotEmpty()) {
            try {
                val formatter = if (cachedTimeFormat == timePattern) {
                    cachedTimeFormatter
                } else {
                    val newFormatter = DateTimeFormatter.ofPattern(timePattern)
                    cachedTimeFormatter = newFormatter
                    cachedTimeFormat = timePattern
                    newFormatter
                }
                val now = LocalDateTime.now()
                val formattedTime = formatter?.format(now) ?: timePattern
                result = result.replace(timePattern, formattedTime)
            } catch (_: Throwable) {}
        }

        if (result.contains("{temp}")) {
            result = result.replace("{temp}", getBatteryTempText(context))
        }
        if (result.contains("{lunar}")) {
            result = result.replace("{lunar}", getLunarDateCached())
        }
        if (result.contains("{rate}")) {
            result = result.replace("{rate}", getRefreshRate(context))
        }
        if (result.contains("{shichen}")) {
            result = result.replace("{shichen}", getChineseTimeHour())
        }
        if (result.contains("{sec}")) {
            result = result.replace("{sec}", getSeconds())
        }
        if (result.contains("{date}")) {
            result = result.replace("{date}", getSimpleDateCached())
        }

        return result
    }

    private fun getLunarDateCached(): String {
        val today = LocalDate.now().dayOfYear
        if (cachedLunarDateDay != today) {
            cachedLunarDate = getLunarDate()
            cachedLunarDateDay = today
        }
        return cachedLunarDate
    }

    private fun getSimpleDateCached(): String {
        val today = LocalDate.now().dayOfYear
        if (cachedSimpleDateDay != today) {
            cachedSimpleDate = getSimpleDate()
            cachedSimpleDateDay = today
        }
        return cachedSimpleDate
    }

    private fun getBatteryTempText(context: Context): String {
        if (cachedTempText.isEmpty()) {
            ensureBatteryReceiver(context)
        }
        return cachedTempText
    }

    private val tempFilePaths = listOf(
        "/sys/class/power_supply/battery/temp",
    )

    private fun readSysTemp(): String? {
        for (path in tempFilePaths) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val content = file.readText().trim()
                    val tempRaw = content.toIntOrNull() ?: continue
                    val tempCelsius = if (tempRaw > 1000) {
                        tempRaw / 1000.0f
                    } else if (tempRaw > 100) {
                        tempRaw / 10.0f
                    } else {
                        tempRaw.toFloat()
                    }
                    if (tempCelsius in 0.0f..100.0f) {
                        return String.format(Locale.getDefault(), "%.1f°C", tempCelsius)
                    }
                }
            } catch (_: Throwable) {}
        }
        return null
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
}
