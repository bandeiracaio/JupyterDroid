package com.jupyterdroid.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.kernel.ExecutionResult
import com.jupyterdroid.model.Cell

// ponytail: casts all cells to Cell.Code for now; Task 6 upgrades to multi-type adapter
class NotebookAdapter(
    val cells: MutableList<Cell>,
    private val onRunCell: (Int) -> Unit
) : RecyclerView.Adapter<CodeCellViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CodeCellViewHolder.create(parent)

    override fun onBindViewHolder(holder: CodeCellViewHolder, position: Int) {
        holder.bind(cells[position] as Cell.Code, position, ::updateSource)
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
