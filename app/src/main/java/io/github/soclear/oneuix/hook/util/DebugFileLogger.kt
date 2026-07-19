package io.github.soclear.oneuix.hook.util

import android.annotation.SuppressLint
import android.os.SystemClock
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object DebugFileLogger {

    private const val MAX_LOG_FILE_BYTES = 1_048_576L
    private const val LOG_DIR_PATH = "/storage/emulated/0/OneUIX"
    private const val LOG_FILE_NAME = "bixby.log"
    private const val LOG_FILE_BACKUP_NAME = "bixby.log.1"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
    private val pendingLines = mutableListOf<String>()
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "OneUIX-BixbyLog").apply { isDaemon = true }
    }

    private var flushScheduled = false

    @Volatile
    private var logDir: File? = null

    @Volatile
    private var processName: String = "unknown"

    val isEnabled = BuildConfig.DEBUG

    fun attachToProcess(lpparam: LoadPackageParam) {
        if (!isEnabled) return
        processName = lpparam.packageName
        bindLogDir()
    }

    fun log(tag: String, message: String) {
        if (!isEnabled) return
        synchronized(lock) {
            pendingLines += formatLine(tag, message)
            scheduleFlushLocked()
        }
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        val suffix = throwable?.let { " - ${it::class.java.simpleName}: ${it.message}" } ?: ""
        log(tag, message + suffix)
        throwable?.stackTraceToString()
            ?.lineSequence()
            ?.forEach { log(tag, it) }
    }

    private fun bindLogDir() {
        synchronized(lock) {
            logDir = resolveLogDir()
            if (logDir == null) {
                XposedBridge.log("[OneUIX-Bixby] DebugFileLogger failed to resolve log dir")
                return
            }
            pendingLines += formatLine("Logger", "attached dir=${logDir?.absolutePath}")
            scheduleFlushLocked()
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun resolveLogDir(): File? {
        val dir = File(LOG_DIR_PATH)
        try {
            if (dir.exists() || dir.mkdirs()) {
                dir.setReadable(true, false)
                dir.setWritable(true, false)
                dir.setExecutable(true, false)
                if (dir.canWrite()) return dir
            }
        } catch (t: Throwable) {
            XposedBridge.log("[OneUIX-Bixby] DebugFileLogger mkdir failed for ${dir.absolutePath}")
            XposedBridge.log(t)
        }
        ensureLogDirWithRoot(dir)
        return if (dir.exists()) dir else null
    }

    private fun scheduleFlushLocked() {
        if (flushScheduled || logDir == null) return
        flushScheduled = true
        writer.execute(::drainPendingLines)
    }

    private fun drainPendingLines() {
        while (true) {
            val content = synchronized(lock) {
                if (pendingLines.isEmpty()) {
                    flushScheduled = false
                    return
                }
                pendingLines.joinToString(separator = "\n", postfix = "\n").also {
                    pendingLines.clear()
                }
            }
            if (!appendContent(content)) {
                synchronized(lock) {
                    pendingLines.add(0, content.removeSuffix("\n"))
                    flushScheduled = false
                }
                return
            }
        }
    }

    private fun appendContent(content: String): Boolean {
        val dir = logDir ?: return false
        try {
            rotateIfNeeded(dir)
            val file = File(dir, LOG_FILE_NAME)
            file.parentFile?.mkdirs()
            file.appendText(content)
            file.setReadable(true, false)
            file.setWritable(true, false)
            return true
        } catch (t: Throwable) {
            if (appendWithRoot(File(dir, LOG_FILE_NAME), content)) {
                return true
            } else {
                XposedBridge.log("[OneUIX-Bixby] DebugFileLogger write failed for ${dir.absolutePath}")
                XposedBridge.log(t)
            }
        }
        return false
    }

    private fun rotateIfNeeded(dir: File) {
        val file = File(dir, LOG_FILE_NAME)
        if (!file.exists() || file.length() < MAX_LOG_FILE_BYTES) return
        val backup = File(dir, LOG_FILE_BACKUP_NAME)
        if (backup.exists()) backup.delete()
        file.renameTo(backup)
    }

    private fun formatLine(tag: String, message: String): String {
        val now = dateFormat.format(Date())
        val uptime = SystemClock.elapsedRealtime()
        return "$now [$processName] [$tag] (+${uptime}ms) $message"
    }

    private fun ensureLogDirWithRoot(dir: File) {
        runRootCommand(
            "mkdir -p '${dir.absolutePath}' && chmod 777 '${dir.absolutePath}'"
        )
    }

    private fun ensureLogFileWithRoot(file: File) {
        runRootCommand(
            "mkdir -p '${file.parentFile?.absolutePath}' && touch '${file.absolutePath}' && chmod 666 '${file.absolutePath}'"
        )
    }

    private fun appendWithRoot(file: File, content: String): Boolean {
        return try {
            ensureLogFileWithRoot(file)
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "cat >> '${file.absolutePath}'")
            )
            process.outputStream.bufferedWriter().use { it.write(content) }
            process.waitFor() == 0
        } catch (t: Throwable) {
            XposedBridge.log("[OneUIX-Bixby] DebugFileLogger root append failed for ${file.absolutePath}")
            XposedBridge.log(t)
            false
        }
    }

    private fun runRootCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor() == 0
        } catch (t: Throwable) {
            XposedBridge.log("[OneUIX-Bixby] DebugFileLogger root command failed: $command")
            XposedBridge.log(t)
            false
        }
    }
}
