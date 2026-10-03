package io.github.soclear.oneuix.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcControllerTest {
    @Test
    fun acceptsFourAndSevenByteUids() {
        listOf("01:02:03:04", "01-02-ab-cd", "01020304050607", "01 02 03 04 05 06 07")
            .forEach { assertTrue(it, NfcController.validateUid(it)) }
    }

    @Test
    fun rejectsPartialBytesAndInvalidHex() {
        listOf("01020304F", "01020304050607F", "", "010203", "0102030405", "010203GG")
            .forEach { assertFalse(it, NfcController.validateUid(it)) }
    }
}
