package com.jupyterdroid

import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.NotebookJson
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

    @Test
    fun `serialize and read(text) round trip through a string`() {
        val fixture = File(javaClass.classLoader!!.getResource("fixture.ipynb")!!.file)
        val (notebookJson, cells) = NotebookFile.read(fixture)

        val text = NotebookFile.serialize(notebookJson, cells)
        val (rereadJson, rereadCells) = NotebookFile.read(text)

        assertEquals(notebookJson, rereadJson)
        assertEquals(cells, rereadCells)
    }

    @Test
    fun `image outputs round-trip as display_data`() {
        val cell = Cell.Code(source = "plot", images = listOf("QUJD"))
        val serialized = NotebookFile.serialize(NotebookJson(), listOf(cell))
        assertTrue(serialized.contains("display_data"))
        assertTrue(serialized.contains("image/png"))

        val (_, cells) = NotebookFile.read(serialized)
        assertEquals(listOf("QUJD"), (cells[0] as Cell.Code).images)
    }

    @Test
    fun `desktop jupyter list-form image png parses`() {
        val nb = """
            {"nbformat":4,"nbformat_minor":5,"metadata":{},"cells":[
              {"cell_type":"code","source":["x"],"metadata":{},"execution_count":1,
               "outputs":[{"output_type":"execute_result","execution_count":1,
                           "data":{"image/png":["QUJD\n","REVG"]},"metadata":{}}]}
            ]}
        """.trimIndent()
        val (_, cells) = NotebookFile.read(nb)
        assertEquals(listOf("QUJDREVG"), (cells[0] as Cell.Code).images)
    }

    @Test
    fun `stream and image outputs coexist`() {
        val cell = Cell.Code(source = "s", output = "hi\n", images = listOf("QUJD"))
        val (_, cells) = NotebookFile.read(NotebookFile.serialize(NotebookJson(), listOf(cell)))
        val roundTripped = cells[0] as Cell.Code
        assertEquals("hi\n", roundTripped.output)
        assertEquals(listOf("QUJD"), roundTripped.images)
    }
}
