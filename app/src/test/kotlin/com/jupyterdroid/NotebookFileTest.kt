package com.jupyterdroid

import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.NotebookJson
import com.jupyterdroid.util.NotebookFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    @Test
    fun `execute_result text plain is read into output`() {
        val nb = """
            {"nbformat":4,"nbformat_minor":5,"metadata":{},"cells":[
              {"cell_type":"code","source":["1 + 1"],"metadata":{},"execution_count":1,
               "outputs":[{"output_type":"execute_result","execution_count":1,
                           "data":{"text/plain":["2"]},"metadata":{}}]}
            ]}
        """.trimIndent()
        val (_, cells) = NotebookFile.read(nb)
        assertEquals("2", (cells[0] as Cell.Code).output)
    }

    @Test
    fun `stream stdout and execute_result text concatenate in document order`() {
        val nb = """
            {"nbformat":4,"nbformat_minor":5,"metadata":{},"cells":[
              {"cell_type":"code","source":["x"],"metadata":{},"execution_count":1,
               "outputs":[
                 {"output_type":"stream","name":"stdout","text":["hi\n"]},
                 {"output_type":"execute_result","execution_count":1,
                  "data":{"text/plain":["42"]},"metadata":{}}
               ]}
            ]}
        """.trimIndent()
        val (_, cells) = NotebookFile.read(nb)
        assertEquals("hi\n42", (cells[0] as Cell.Code).output)
    }

    @Test
    fun `display_data prefers image over its text_plain repr`() {
        val nb = """
            {"nbformat":4,"nbformat_minor":5,"metadata":{},"cells":[
              {"cell_type":"code","source":["plt.plot(x)"],"metadata":{},"execution_count":1,
               "outputs":[{"output_type":"display_data",
                           "data":{"image/png":["QUJD"],"text/plain":["<Figure size 640x480>"]},
                           "metadata":{}}]}
            ]}
        """.trimIndent()
        val (_, cells) = NotebookFile.read(nb)
        val c = cells[0] as Cell.Code
        assertEquals(listOf("QUJD"), c.images)
        assertEquals("", c.output)  // richer image wins; the <Figure …> repr is suppressed
    }

    @Test
    fun `markdown cells omit outputs and execution_count, code cells keep them`() {
        val serialized = NotebookFile.serialize(
            NotebookJson(),
            listOf(Cell.Markdown("# Hi"), Cell.Code(source = "x = 1", output = "1\n"))
        )
        val cells = Json.parseToJsonElement(serialized).jsonObject.getValue("cells").jsonArray
        val md = cells[0].jsonObject
        val code = cells[1].jsonObject

        assertEquals("markdown", md.getValue("cell_type").jsonPrimitive.content)
        assertTrue("markdown must not carry outputs", "outputs" !in md)
        assertTrue("markdown must not carry execution_count", "execution_count" !in md)
        assertTrue("code must carry outputs", "outputs" in code)
        assertTrue("code must carry execution_count", "execution_count" in code)
    }
}
