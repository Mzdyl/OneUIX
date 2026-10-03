package io.github.soclear.oneuix.util

import java.io.File
import java.util.concurrent.TimeUnit

data class CommandResult(val exitCode: Int, val output: String, val timedOut: Boolean = false) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut
}

internal fun runCommand(
    arguments: List<String>,
    timeoutMillis: Long = 15_000,
    outputDirectory: File? = null,
): CommandResult {
    val outputFile = File.createTempFile("oneuix-command-", ".log", outputDirectory)
    var process: Process? = null
    try {
        process = ProcessBuilder(arguments)
            .redirectErrorStream(true)
            .redirectOutput(outputFile)
            .start()
        process.outputStream.close()
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return CommandResult(-1, "Command timed out", timedOut = true)
        }
        return CommandResult(process.exitValue(), outputFile.inputStream().use {
            it.readNBytes(64 * 1024).toString(Charsets.UTF_8).trim()
        })
    } finally {
        process?.let {
            if (it.isAlive) it.destroyForcibly()
            it.inputStream.close()
            it.errorStream.close()
        }
        outputFile.delete()
    }
}

internal fun runRootCommand(
    command: String,
    outputDirectory: File,
    globalMountNamespace: Boolean = false,
): CommandResult {
    val arguments = if (globalMountNamespace) listOf("su", "-mm", "-c", command)
        else listOf("su", "-c", command)
    val result = runCommand(arguments, outputDirectory = outputDirectory)
    val unsupportedOption = listOf("invalid option", "unknown option", "unrecognized option")
        .any { result.output.contains(it, ignoreCase = true) }
    return if (globalMountNamespace && !result.isSuccess && !result.timedOut && unsupportedOption) {
        runCommand(listOf("su", "-c", command), outputDirectory = outputDirectory)
    } else result
}
