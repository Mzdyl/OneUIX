package io.github.soclear.oneuix.hook.systemui

import android.content.Context
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
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

object StatusBarClock {
    fun updateStatusBarClockEverySecond(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        setupSecondUpdate(loadPackageParam)
    }

    fun setStatusBarClockStyle(loadPackageParam: LoadPackageParam, format: String, needsSecondUpdate: Boolean = false) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return

        val autoDetectSecondUpdate = format.contains("ss") || 
                                format.contains("SS") ||
                                format.contains("{sec}") ||
                                format.contains("{temp}") ||
                                format.contains("{rate}")
        
        val shouldEnableSecondUpdate = needsSecondUpdate || autoDetectSecondUpdate

        if (shouldEnableSecondUpdate) {
            setupSecondUpdate(loadPackageParam)
        }

        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val clockTextView = param.thisObject as TextView
                    val context = clockTextView.context
                    
                    clockIndicatorViewRef = WeakReference(clockTextView)
                    clockFormat = format
                    
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

    private var secondUpdateHandler: Handler? = null
    private var secondUpdateRunnable: Runnable? = null
    private var clockIndicatorViewRef: WeakReference<TextView>? = null
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

    private fun setupSecondUpdate(loadPackageParam: LoadPackageParam) {
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
                        stopSecondUpdate()
                        clockIndicatorViewRef = null
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun ensureSecondUpdateRunning() {
        if (secondUpdateHandler != null) return
        
        secondUpdateHandler = Handler(Looper.getMainLooper())
        secondUpdateRunnable = object : Runnable {
            override fun run() {
                clockIndicatorViewRef?.get()?.let { view ->
                    try {
                        val powerManager = view.context.getSystemService(PowerManager::class.java)
                        if (powerManager?.isInteractive == true && clockFormat.isNotEmpty()) {
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
    
    private fun stopSecondUpdate() {
        secondUpdateRunnable?.let { secondUpdateHandler?.removeCallbacks(it) }
        secondUpdateHandler = null
        secondUpdateRunnable = null
        log("stopSecondUpdate: stopped")
    }

    private val clockPlaceholders = mapOf(
        "{temp}" to "\u0001TEMP\u0001",
        "{lunar}" to "\u0002LUNAR\u0002",
        "{rate}" to "\u0003RATE\u0003",
        "{shichen}" to "\u0004SHICHEN\u0004",
        "{sec}" to "\u0005SEC\u0005",
        "{date}" to "\u0006DATE\u0006"
    )

    private fun formatClockText(format: String, context: Context): String {
        var processedFormat = format
        for ((variable, placeholder) in clockPlaceholders) {
            processedFormat = processedFormat.replace(variable, placeholder)
        }
        
        val timeFormat = processedFormat
            .replace("\u0001TEMP\u0001", "")
            .replace("\u0002LUNAR\u0002", "")
            .replace("\u0003RATE\u0003", "")
            .replace("\u0004SHICHEN\u0004", "")
            .replace("\u0005SEC\u0005", "")
            .replace("\u0006DATE\u0006", "")
            .trim()
        
        val now = LocalDateTime.now()
        var result = processedFormat
        if (timeFormat.isNotEmpty()) {
            try {
                val formatter = if (cachedTimeFormat == timeFormat) {
                    cachedTimeFormatter
                } else {
                    val newFormatter = DateTimeFormatter.ofPattern(timeFormat)
                    cachedTimeFormatter = newFormatter
                    cachedTimeFormat = timeFormat
                    newFormatter
                }
                val formattedTime = formatter?.format(now) ?: timeFormat
                result = result.replace(timeFormat, formattedTime)
            } catch (_: Throwable) {
            }
        }
        
        val sb = StringBuilder(result)
        
        val tempIdx = sb.indexOf("\u0001TEMP\u0001")
        if (tempIdx >= 0) {
            val temp = getBatteryTempText(context) ?: ""
            sb.replace(tempIdx, tempIdx + 7, temp)
        }
        
        val lunarIdx = sb.indexOf("\u0002LUNAR\u0002")
        if (lunarIdx >= 0) {
            val lunar = getLunarDateCached()
            sb.replace(lunarIdx, lunarIdx + 7, lunar)
        }
        
        val rateIdx = sb.indexOf("\u0003RATE\u0003")
        if (rateIdx >= 0) {
            val rate = getRefreshRate(context)
            sb.replace(rateIdx, rateIdx + 7, rate)
        }
        
        val shichenIdx = sb.indexOf("\u0004SHICHEN\u0004")
        if (shichenIdx >= 0) {
            val shichen = getChineseTimeHour()
            sb.replace(shichenIdx, shichenIdx + 9, shichen)
        }
        
        val secIdx = sb.indexOf("\u0005SEC\u0005")
        if (secIdx >= 0) {
            val sec = getSeconds()
            sb.replace(secIdx, secIdx + 6, sec)
        }
        
        val dateIdx = sb.indexOf("\u0006DATE\u0006")
        if (dateIdx >= 0) {
            val date = getSimpleDateCached()
            sb.replace(dateIdx, dateIdx + 7, date)
        }
        
        return sb.toString()
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

    @Volatile
    private var lastTempText: String? = null
    @Volatile
    private var lastTempTime: Long = 0
    
    private fun getBatteryTempText(context: Context): String? {
        val now = System.currentTimeMillis()
        
        if (lastTempText != null && (now - lastTempTime) < 1000) {
            return lastTempText
        }
        
        val sysTemp = readSysTemp()
        if (sysTemp != null) {
            lastTempText = sysTemp
            lastTempTime = now
            return sysTemp
        }
        
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

    private val tempFilePaths = listOf(
        "/sys/class/power_supply/battery/temp",
    )

    private fun readSysTemp(): String? {
        for (path in tempFilePaths) {
            try {
                val file = java.io.File(path)
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
