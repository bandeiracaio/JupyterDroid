package com.jupyterdroid

import com.jupyterdroid.util.PipMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipMessagesTest {

    @Test
    fun `native build failure explains the on-device limitation`() {
        val msg = PipMessages.failure(
            "matplotlib", "",
            "ERROR: Failed building wheel for matplotlib\nBUILDING MATPLOTLIB"
        )
        assertTrue(msg, msg.contains("native code"))
        assertTrue(msg, msg.contains("matplotlib"))
    }

    @Test
    fun `missing package reports not found`() {
        val msg = PipMessages.failure(
            "nosuchpkg", "", "ERROR: No matching distribution found for nosuchpkg"
        )
        assertTrue(msg, msg.contains("No installable"))
    }

    @Test
    fun `other failures surface the stderr`() {
        val msg = PipMessages.failure("x", "", "connection reset by peer")
        assertTrue(msg, msg.contains("connection reset by peer"))
    }

    @Test
    fun `empty output falls back to a generic message`() {
        assertEquals("Failed to install 'x'.", PipMessages.failure("x", "", ""))
    }
}
