package io.github.soclear.oneuix.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

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
        return (clean.length == 8 || clean.length == 14) && clean.all { it in "0123456789ABCDEF" }
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
                set -e
                mkdir -p $MODULE_DIR/system/bin
                cp -f "${tempScript.absolutePath}" $CLI_PATH
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
            runSuCommand(context, deployCmd).isSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
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
        val cleanSak = sak.trim().uppercase().ifEmpty { "04" }
        val cleanAtqa = atqa.trim().uppercase().ifEmpty { "00" }
        if (!cleanSak.matches(Regex("[0-9A-F]{1,2}")) ||
            !cleanAtqa.matches(Regex("(?:[0-9A-F]{1,2}|[0-9A-F]{4})"))
        ) {
            return@withContext Result.failure(IllegalArgumentException("Invalid SAK or ATQA"))
        }
        if (!ensureModuleDeployed(context)) {
            return@withContext Result.failure(IllegalStateException("NFC module deployment failed"))
        }
        val formatted = formatUid(clean)
        val res = runSuCommand(context, "$CLI_PATH set $formatted $cleanSak $cleanAtqa")
        if (res.isSuccess) {
            Result.success(formatted)
        } else {
            Result.failure(Exception(res.output))
        }
    }

    suspend fun reset(context: Context): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureModuleDeployed(context)) {
            return@withContext Result.failure(IllegalStateException("NFC module deployment failed"))
        }
        val res = runSuCommand(context, "$CLI_PATH reset")
        if (res.isSuccess) {
            Result.success(res.output)
        } else {
            Result.failure(Exception(res.output))
        }
    }

    suspend fun getStatus(context: Context): NfcStatus = withContext(Dispatchers.IO) {
        val res = runSuCommand(context, "$CLI_PATH status")
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

    private suspend fun runSuCommand(context: Context, command: String): SuResult = runInterruptible(Dispatchers.IO) {
        try {
            val result = runRootCommand(command, outputDirectory = context.cacheDir, globalMountNamespace = true)
            SuResult(isSuccess = result.isSuccess, output = result.output)
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            SuResult(isSuccess = false, output = e.message ?: "Execution error")
        }
    }

}

data class SuResult(val isSuccess: Boolean, val output: String)
data class NfcStatus(val activeUid: String, val halPid: String, val nfcPid: String, val defaultRoute: String)
