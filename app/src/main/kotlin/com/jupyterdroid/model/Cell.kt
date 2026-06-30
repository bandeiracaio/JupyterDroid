package com.jupyterdroid.model

sealed class Cell {
    data class Code(
        val source: String = "",
        val output: String = "",
        val error: String = "",
        val executionCount: Int? = null
    ) : Cell()

    data class Markdown(
        val source: String = ""
    ) : Cell()
}
