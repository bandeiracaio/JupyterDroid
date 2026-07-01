package com.jupyterdroid

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.NotebookJson
import com.jupyterdroid.util.NotebookFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NotebookFileUriTest {

    private val resolver = InstrumentationRegistry.getInstrumentation()
        .targetContext.contentResolver

    @Test
    fun writeThenReadRoundTripsThroughUri() {
        val tempFile = File.createTempFile("uri_test", ".ipynb")
        val uri = Uri.fromFile(tempFile)
        val cells = listOf(Cell.Code(source = "print('hi')"), Cell.Markdown(source = "# Title"))

        NotebookFile.write(resolver, uri, NotebookJson(), cells)
        val (_, readCells) = NotebookFile.read(resolver, uri)

        assertEquals(cells, readCells)
    }

    @Test
    fun writeTruncatesPreviousLongerContent() {
        val tempFile = File.createTempFile("uri_truncate_test", ".ipynb")
        val uri = Uri.fromFile(tempFile)

        NotebookFile.write(
            resolver, uri, NotebookJson(),
            listOf(Cell.Code(source = "a very long line of source code that will be overwritten"))
        )
        NotebookFile.write(resolver, uri, NotebookJson(), listOf(Cell.Code(source = "x")))

        val (_, cells) = NotebookFile.read(resolver, uri)
        assertEquals(1, cells.size)
        assertEquals("x", (cells[0] as Cell.Code).source)
        // No leftover bytes from the longer first write past the JSON's closing brace.
        assertTrue(tempFile.readText().trim().endsWith("}"))
    }
}
