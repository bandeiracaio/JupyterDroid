package com.jupyterdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupyterdroid.kernel.KernelManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KernelManagerTest {

    private lateinit var km: KernelManager

    @Before
    fun setup() {
        km = KernelManager.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        km.reset()
    }

    @Test
    fun executeReturnsStdout() {
        val result = km.execute("print('hello')")
        assertEquals("hello\n", result.output)
        assertEquals("", result.error)
        assertEquals(1, result.executionCount)
    }

    @Test
    fun executeReturnsErrorOnException() {
        val result = km.execute("1/0")
        assertTrue(result.error.contains("ZeroDivisionError"))
        assertEquals("", result.output)
    }

    @Test
    fun statePersistedAcrossCells() {
        km.execute("x = 42")
        val result = km.execute("print(x)")
        assertEquals("42\n", result.output)
        assertEquals(2, result.executionCount)
    }
}
