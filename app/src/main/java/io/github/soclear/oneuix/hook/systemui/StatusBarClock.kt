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
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.TraditionalChineseCalendar
import io.github.soclear.oneuix.hook.util.log
import io.github.soclear.oneuix.hook.util.logError
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
    fun updateStatusBarClockEverySecond(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        setupSecondUpdate(loadPackageParam)
    }

    fun setStatusBarClockStyle(
        loadPackageParam: LoadPackageParam,
        format: String,
        needsSecondUpdate: Boolean = false,
    ) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return

        // Note: {temp} is intentionally event-driven (via battery receiver) and does NOT force a 1-second polling loop
        val autoDetectSecondUpdate = format.contains("ss") ||
            format.contains("SS") ||
            format.contains("{sec}")

        val shouldEnableSecondUpdate = needsSecondUpdate || autoDetectSecondUpdate

        if (shouldEnableSecondUpdate) {
            setupSecondUpdate(loadPackageParam)
        }

        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val clockTextView = param.thisObject as TextView
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
        if (isBatteryReceiverRegistered) return
        synchronized(this) {
            if (isBatteryReceiverRegistered) return
            try {
                val appContext = context.applicationContext ?: context
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val stickyIntent = appContext.registerReceiver(batteryReceiver, filter)
                isBatteryReceiverRegistered = true
                if (stickyIntent != null) {
                    updateTempFromIntent(stickyIntent)
                } else if (cachedTempText.isEmpty()) {
                    cachedTempText = readSysTemp() ?: ""
                }
            } catch (t: Throwable) {
                logError("Failed to register battery receiver", t)
                if (cachedTempText.isEmpty()) {
                    cachedTempText = readSysTemp() ?: ""
                }
            }
        }
    }

    private fun updateTempFromIntent(intent: Intent) {
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        if (tempRaw > 0) {
            val tempCelsius = tempRaw / 10.0f
            val newTemp = String.format(Locale.getDefault(), "%.1f°C", tempCelsius)
            if (newTemp != cachedTempText) {
                cachedTempText = newTemp
                if (clockFormat.contains("{temp}")) {
                    updateAllClockViews()
                }
            }
        }
    }

    @Synchronized
    private fun setupSecondUpdate(loadPackageParam: LoadPackageParam) {
        if (secondUpdateHooksInstalled) return
        secondUpdateHooksInstalled = true

        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.policy.QSClockIndicatorViewController",
                loadPackageParam.classLoader,
                "onViewAttached",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val clockTextView = getObjectField(param.thisObject, "view") as TextView
                        clockTextView.fontFeatureSettings = "tnum"
                        registerClockView(clockTextView)
                    }
                }
            )
        } catch (_: Throwable) {}

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

        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.policy.QSClockIndicatorView",
                loadPackageParam.classLoader,
                "onViewDetached",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val clockTextView = param.thisObject as? TextView
                        if (clockTextView != null) {
                            synchronized(clockViews) {
                                clockViews.remove(clockTextView)
                                if (clockViews.isEmpty()) {
                                    stopSecondUpdate()
                                }
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun ensureSecondUpdateRunning(context: Context) {
        if (secondUpdateHandler != null) return

        val mainHandler = Handler(Looper.getMainLooper())
        secondUpdateHandler = mainHandler
        secondUpdateRunnable = object : Runnable {
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
        mainHandler.post(secondUpdateRunnable!!)
        log("ensureSecondUpdateRunning: started")
    }

    private fun stopSecondUpdate() {
        secondUpdateRunnable?.let { secondUpdateHandler?.removeCallbacks(it) }
        secondUpdateHandler = null
        secondUpdateRunnable = null
        log("stopSecondUpdate: stopped")
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

    private fun getDateStyleText(context: Context, style: Int): String? {
        return when (style) {
            0 -> getSimpleDate()
            1 -> getLunarDate()
            2 -> getBatteryTempText(context)
            3 -> getRefreshRate(context)
            4 -> getChineseTimeHour()
            5 -> getSeconds()
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
}
