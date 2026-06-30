package com.jupyterdroid.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.kernel.ExecutionResult
import com.jupyterdroid.model.Cell
import io.noties.markwon.Markwon

class NotebookAdapter(
    val cells: MutableList<Cell>,
    private val onRunCell: (Int) -> Unit,
    private val markwon: Markwon
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CODE = 0
        private const val TYPE_MARKDOWN = 1
    }

    override fun getItemViewType(position: Int) = when (cells[position]) {
        is Cell.Code -> TYPE_CODE
        is Cell.Markdown -> TYPE_MARKDOWN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            TYPE_CODE -> CodeCellViewHolder.create(parent)
            else -> MarkdownCellViewHolder.create(parent, markwon)
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CodeCellViewHolder -> holder.bind(
                cells[position] as Cell.Code, position, ::updateSource, onRunCell
            )
            is MarkdownCellViewHolder -> holder.bind(
                cells[position] as Cell.Markdown, position, ::updateSource
            )
        }
    }

    override fun getItemCount() = cells.size

    fun addCodeCell() {
        cells.add(Cell.Code())
        notifyItemInserted(cells.size - 1)
    }

    fun addMarkdownCell() {
        cells.add(Cell.Markdown())
        notifyItemInserted(cells.size - 1)
    }

    fun updateCellOutput(position: Int, result: ExecutionResult) {
        val cell = cells[position] as? Cell.Code ?: return
        cells[position] = cell.copy(
            output = result.output,
            error = result.error,
            executionCount = result.executionCount
        )
        notifyItemChanged(position)
    }

    private fun updateSource(position: Int, source: String) {
        when (val cell = cells[position]) {
            is Cell.Code -> cells[position] = cell.copy(source = source)
            is Cell.Markdown -> cells[position] = cell.copy(source = source)
        }
    }
}
