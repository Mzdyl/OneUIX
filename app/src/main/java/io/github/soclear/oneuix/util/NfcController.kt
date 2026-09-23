package io.github.soclear.oneuix.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object NfcController {
    private const val MODULE_DIR = "/data/adb/modules/nfc_sim"
    private const val CLI_PATH = "$MODULE_DIR/nfc-sim"

    fun cleanUid(raw: String): String {
        return raw.replace(Regex("[: -]"), "").uppercase()
    }

    fun formatUid(clean: String): String {
        return clean.chunked(2).joinToString(":")
    }

    fun validateUid(raw: String): Boolean {
        val clean = cleanUid(raw)
        val bytes = clean.length / 2
        return (bytes == 4 || bytes == 7) && clean.all { it in "0123456789ABCDEF" }
    }

    suspend fun ensureModuleDeployed(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempScript = File(context.cacheDir, "nfc-sim")
            context.assets.open("nfc-sim").use { input ->
                FileOutputStream(tempScript).use { output ->
                    input.copyTo(output)
                }
            }
            tempScript.setExecutable(true, false)

            val deployCmd = """
                mkdir -p $MODULE_DIR/system/bin
                cp -f ${tempScript.absolutePath} $CLI_PATH
                chmod 755 $CLI_PATH
                ln -sf $CLI_PATH $MODULE_DIR/system/bin/nfc-sim
                cat << 'EOF' > $MODULE_DIR/module.prop
id=nfc_sim
name=OneUIX NFC Card Simulation
version=1.0
versionCode=100
author=OneUIX
description=NFC UID simulation module for Samsung One UI 7+ / 8.5 (NXP SN100/SN220).
EOF
            """.trimIndent()
            runSuCommand(deployCmd).isSuccess
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun setUid(
        context: Context,
        uid: String,
        sak: String = "04",
        atqa: String = "00"
    ): Result<String> = withContext(Dispatchers.IO) {
        val clean = cleanUid(uid)
        if (!validateUid(clean)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid UID"))
        }
        ensureModuleDeployed(context)
        val formatted = formatUid(clean)
        val cleanSak = sak.trim().uppercase().ifEmpty { "04" }
        val cleanAtqa = atqa.trim().uppercase().ifEmpty { "00" }
        val res = runSuCommand("$CLI_PATH set $formatted $cleanSak $cleanAtqa")
        if (res.isSuccess) {
            Result.success(formatted)
        } else {
            Result.failure(Exception(res.output))
        }
    }

    suspend fun reset(context: Context): Result<String> = withContext(Dispatchers.IO) {
        ensureModuleDeployed(context)
        val res = runSuCommand("$CLI_PATH reset")
        if (res.isSuccess) {
            Result.success(res.output)
        } else {
            Result.failure(Exception(res.output))
        }
    }

    suspend fun getStatus(): NfcStatus = withContext(Dispatchers.IO) {
        val res = runSuCommand("$CLI_PATH status")
        var activeUid = ""
        var halPid = ""
        var nfcPid = ""
        var defaultRoute = ""
        if (res.isSuccess) {
            res.output.lines().forEach { line ->
                when {
                    line.contains("当前模拟卡号") -> {
                        activeUid = line.substringAfter(":").trim().substringBefore("[")
                    }
                    line.contains("NFC HAL 进程") -> {
                        halPid = line.substringAfter(":").trim()
                    }
                    line.contains("NFC 服务进程") -> {
                        nfcPid = line.substringAfter(":").trim()
                    }
                    line.contains("当前默认路由") -> {
                        defaultRoute = line.substringAfter(":").trim()
                    }
                }
            }
        }
        NfcStatus(activeUid = activeUid, halPid = halPid, nfcPid = nfcPid, defaultRoute = defaultRoute)
    }

    private fun runSuCommand(command: String): SuResult {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exit = process.waitFor()
            SuResult(isSuccess = exit == 0, output = stdout.ifEmpty { stderr })
        } catch (e: Throwable) {
            SuResult(isSuccess = false, output = e.message ?: "Execution error")
        }
    }
}

data class SuResult(val isSuccess: Boolean, val output: String)
data class NfcStatus(val activeUid: String, val halPid: String, val nfcPid: String, val defaultRoute: String)
