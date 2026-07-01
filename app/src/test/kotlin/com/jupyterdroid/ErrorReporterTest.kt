package com.jupyterdroid

import com.jupyterdroid.util.ErrorReporter
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorReporterTest {

    @Test
    fun `format includes action, message, stack trace, extra, and version`() {
        val text = ErrorReporter.format(
            action = "Kernel crash",
            message = "ZeroDivisionError: division by zero",
            stackTrace = "line 1\nline 2",
            extra = "print(1/0)",
            appVersion = "1.2 (3)"
        )

        assertTrue(text.contains("Kernel crash"))
        assertTrue(text.contains("ZeroDivisionError: division by zero"))
        assertTrue(text.contains("line 1\nline 2"))
        assertTrue(text.contains("print(1/0)"))
        assertTrue(text.contains("1.2 (3)"))
    }

    @Test
    fun `format omits stack trace and extra sections when null`() {
        val text = ErrorReporter.format(
            action = "Open notebook",
            message = "Permission denied",
            stackTrace = null,
            extra = null,
            appVersion = "1.2 (3)"
        )

        assertTrue(text.contains("Open notebook"))
        assertTrue(text.contains("Permission denied"))
        assertTrue(!text.contains("Stack trace:"))
        assertTrue(!text.contains("Cell source:"))
    }
}
