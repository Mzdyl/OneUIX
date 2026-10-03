package io.github.soclear.oneuix.util

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class CommandRunnerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    @Test
    fun combinesErrorOutputAndReportsExitStatus() {
        assumeTrue(File("/bin/sh").canExecute())
        val result = runCommand(listOf("/bin/sh", "-c", "printf error >&2; exit 7"))
        assertFalse(result.isSuccess)
        assertEquals(7, result.exitCode)
        assertEquals("error", result.output)
    }

    @Test
    fun outputLargerThanAPipeDoesNotDeadlock() {
        assumeTrue(File("/bin/sh").canExecute())
        val result = runCommand(listOf("/bin/sh", "-c", "head -c 131072 /dev/zero >&2"), 5_000)
        assertTrue(result.isSuccess)
        assertEquals(65_536, result.output.length)
    }

    @Test
    fun hungCommandReturnsWithinTimeout() {
        assumeTrue(File("/bin/sh").canExecute())
        val start = System.nanoTime()
        val result = runCommand(listOf("/bin/sh", "-c", "exec sleep 10"), 100, temporaryFolder.root)
        assertFalse(result.isSuccess)
        assertTrue(result.timedOut)
        assertTrue(temporaryFolder.root.listFiles()!!.isEmpty())
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000)
    }
}
