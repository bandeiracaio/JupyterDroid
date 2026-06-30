package com.jupyterdroid

import com.jupyterdroid.model.Cell
import com.jupyterdroid.util.NotebookFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NotebookFileTest {

    @Test
    fun `read parses code and markdown cells`() {
        val fixture = File(javaClass.classLoader!!.getResource("fixture.ipynb")!!.file)
        val (_, cells) = NotebookFile.read(fixture)

        assertEquals(2, cells.size)
        assertTrue(cells[0] is Cell.Code)
        assertTrue(cells[1] is Cell.Markdown)

        val code = cells[0] as Cell.Code
        assertEquals("print('hello')", code.source)
        assertEquals("hello\n", code.output)
        assertEquals(1, code.executionCount)

        val md = cells[1] as Cell.Markdown
        assertEquals("# Hello\nWorld", md.source)
    }

    @Test
    fun `round trip preserves JSON structure`() {
        val fixture = File(javaClass.classLoader!!.getResource("fixture.ipynb")!!.file)
        val (notebookJson, cells) = NotebookFile.read(fixture)

        val tmp = createTempFile("test", ".ipynb")
        NotebookFile.write(notebookJson, cells, tmp)

        val original = Json.parseToJsonElement(fixture.readText())
        val written = Json.parseToJsonElement(tmp.readText())
        assertEquals(original, written)
    }
}
