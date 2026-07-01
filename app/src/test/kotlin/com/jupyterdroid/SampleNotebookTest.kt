package com.jupyterdroid

import com.jupyterdroid.model.Cell
import com.jupyterdroid.util.NotebookFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SampleNotebookTest {

    @Test
    fun `sample notebook has ten cells alternating markdown and code`() {
        val (_, cells) = NotebookFile.read(File("src/main/assets/sample_titanic.ipynb"))

        assertEquals(10, cells.size)
        cells.forEachIndexed { i, cell ->
            if (i % 2 == 0) {
                assertTrue("cell $i should be Markdown", cell is Cell.Markdown)
            } else {
                assertTrue("cell $i should be Code", cell is Cell.Code)
            }
        }
    }

    @Test
    fun `sample notebook code cells reference the bundled dataset and use only stdlib`() {
        val (_, cells) = NotebookFile.read(File("src/main/assets/sample_titanic.ipynb"))
        val codeCells = cells.filterIsInstance<Cell.Code>()

        assertTrue(codeCells.first().source.contains("kernel_runner.data_path(\"titanic.csv\")"))
        codeCells.forEach { cell ->
            assertTrue(
                "cell should not import pandas: ${cell.source}",
                !cell.source.contains("import pandas")
            )
            assertTrue(
                "cell should not import matplotlib: ${cell.source}",
                !cell.source.contains("import matplotlib")
            )
        }
    }
}
