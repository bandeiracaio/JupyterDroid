package com.jupyterdroid.util

import android.content.ContentResolver
import android.net.Uri
import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.NotebookCellJson
import com.jupyterdroid.model.NotebookJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    fun read(resolver: ContentResolver, uri: Uri): Pair<NotebookJson, List<Cell>> {
        val text = resolver.openInputStream(uri)!!.use { it.reader().readText() }
        return read(text)
    }

    fun write(resolver: ContentResolver, uri: Uri, notebookJson: NotebookJson, cells: List<Cell>) {
        resolver.openOutputStream(uri, "wt")!!.use { it.write(serialize(notebookJson, cells).toByteArray()) }
    }

    fun serialize(notebookJson: NotebookJson, cells: List<Cell>): String {
        val updated = notebookJson.copy(cells = cells.map { it.toCellJson() })
        val tree = json.encodeToJsonElement(updated).jsonObject
        // encodeDefaults emits outputs/execution_count on every cell; nbformat
        // markdown cells must carry neither. Strip them from markdown cells only.
        val prunedCells = tree.getValue("cells").jsonArray.map { cell ->
            val obj = cell.jsonObject
            if (obj["cell_type"]?.jsonPrimitive?.content == "markdown") {
                JsonObject(obj - "outputs" - "execution_count")
            } else {
                obj
            }
        }
        val pruned = JsonObject(tree + ("cells" to JsonArray(prunedCells)))
        return json.encodeToString(JsonObject.serializer(), pruned)
    }

    private fun NotebookCellJson.toCell(): Cell = when (cellType) {
        "code" -> Cell.Code(
            source = source.joinToString(""),
            output = outputs.filter { it.outputType == "stream" && it.streamName == "stdout" }
                .flatMap { it["text"].asLines() }.joinToString(""),
            error = outputs.filter { it.outputType == "stream" && it.streamName == "stderr" }
                .flatMap { it["text"].asLines() }.joinToString(""),
            executionCount = executionCount,
            images = outputs
                .filter { it.outputType == "display_data" || it.outputType == "execute_result" }
                .mapNotNull { out ->
                    out["data"]?.jsonObject?.get("image/png")?.asLines()
                        ?.joinToString("")?.replace("\n", "")?.takeIf { it.isNotEmpty() }
                }
        )
        else -> Cell.Markdown(source = source.joinToString(""))
    }

    private val JsonObject.outputType: String?
        get() = this["output_type"]?.jsonPrimitive?.content
    private val JsonObject.streamName: String?
        get() = this["name"]?.jsonPrimitive?.content

    // nbformat allows "text"/"image/png" as either a string or a list of lines.
    private fun JsonElement?.asLines(): List<String> = when (this) {
        is JsonArray -> map { it.jsonPrimitive.content }
        is JsonPrimitive -> listOf(content)
        else -> emptyList()
    }

    private fun Cell.toCellJson(): NotebookCellJson = when (this) {
        is Cell.Code -> NotebookCellJson(
            cellType = "code",
            source = source.toNotebookLines(),
            outputs = buildList {
                if (output.isNotEmpty()) add(streamOutput("stdout", output))
                if (error.isNotEmpty()) add(streamOutput("stderr", error))
                images.forEach { add(imageOutput(it)) }
            },
            executionCount = executionCount
        )
        is Cell.Markdown -> NotebookCellJson(
            cellType = "markdown",
            source = source.toNotebookLines()
        )
    }

    private fun streamOutput(name: String, text: String) = JsonObject(
        mapOf(
            "output_type" to JsonPrimitive("stream"),
            "name" to JsonPrimitive(name),
            "text" to JsonArray(text.toNotebookLines().map { JsonPrimitive(it) })
        )
    )

    private fun imageOutput(b64: String) = JsonObject(
        mapOf(
            "output_type" to JsonPrimitive("display_data"),
            "data" to JsonObject(mapOf("image/png" to JsonPrimitive(b64 + "\n"))),
            "metadata" to JsonObject(emptyMap())
        )
    )

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
