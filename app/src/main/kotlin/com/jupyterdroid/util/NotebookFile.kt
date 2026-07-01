package com.jupyterdroid.util

import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.CellOutputJson
import com.jupyterdroid.model.NotebookCellJson
import com.jupyterdroid.model.NotebookJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object NotebookFile {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun read(file: File): Pair<NotebookJson, List<Cell>> = read(file.readText())

    fun read(text: String): Pair<NotebookJson, List<Cell>> {
        val nb = json.decodeFromString<NotebookJson>(text)
        return Pair(nb, nb.cells.map { it.toCell() })
    }

    fun write(notebookJson: NotebookJson, cells: List<Cell>, file: File) {
        file.writeText(serialize(notebookJson, cells))
    }

    fun serialize(notebookJson: NotebookJson, cells: List<Cell>): String {
        val updated = notebookJson.copy(cells = cells.map { it.toCellJson() })
        return json.encodeToString(updated)
    }

    private fun NotebookCellJson.toCell(): Cell = when (cellType) {
        "code" -> Cell.Code(
            source = source.joinToString(""),
            output = outputs.filter { it.outputType == "stream" && it.name == "stdout" }
                .flatMap { it.text }.joinToString(""),
            error = outputs.filter { it.outputType == "stream" && it.name == "stderr" }
                .flatMap { it.text }.joinToString(""),
            executionCount = executionCount
        )
        else -> Cell.Markdown(source = source.joinToString(""))
    }

    private fun Cell.toCellJson(): NotebookCellJson = when (this) {
        is Cell.Code -> NotebookCellJson(
            cellType = "code",
            source = source.toNotebookLines(),
            outputs = buildList {
                if (output.isNotEmpty())
                    add(CellOutputJson(name = "stdout", text = output.toNotebookLines()))
                if (error.isNotEmpty())
                    add(CellOutputJson(name = "stderr", text = error.toNotebookLines()))
            },
            executionCount = executionCount
        )
        is Cell.Markdown -> NotebookCellJson(
            cellType = "markdown",
            source = source.toNotebookLines()
        )
    }

    // .ipynb format: each line ends with \n except the last.
    // filter removes the trailing empty string produced by split when input ends with \n.
    // ponytail: filter instead of special-casing; upgrade if blank lines at end are ever meaningful
    private fun String.toNotebookLines(): List<String> {
        if (isEmpty()) return emptyList()
        val parts = split("\n")
        return parts.mapIndexed { i, part -> if (i < parts.size - 1) "$part\n" else part }
            .filter { it.isNotEmpty() }
    }
}
