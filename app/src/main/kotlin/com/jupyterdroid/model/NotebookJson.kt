package com.jupyterdroid.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class NotebookJson(
    val nbformat: Int = 4,
    @SerialName("nbformat_minor") val nbformatMinor: Int = 5,
    val metadata: JsonObject = defaultMetadata(),
    val cells: List<NotebookCellJson> = emptyList()
)

@Serializable
data class NotebookCellJson(
    @SerialName("cell_type") val cellType: String,
    val source: List<String> = emptyList(),
    val metadata: JsonObject = JsonObject(emptyMap()),
    val outputs: List<JsonObject> = emptyList(),
    @SerialName("execution_count") val executionCount: Int? = null
)

private fun defaultMetadata(): JsonObject = JsonObject(
    mapOf(
        "kernelspec" to JsonObject(
            mapOf(
                "display_name" to JsonPrimitive("Python 3"),
                "language" to JsonPrimitive("python"),
                "name" to JsonPrimitive("python3")
            )
        ),
        "language_info" to JsonObject(
            mapOf("name" to JsonPrimitive("python"))
        )
    )
)
