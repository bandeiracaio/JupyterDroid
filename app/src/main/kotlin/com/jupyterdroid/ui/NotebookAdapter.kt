package com.jupyterdroid.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.R
import com.jupyterdroid.kernel.ExecutionResult
import com.jupyterdroid.model.Cell
import io.noties.markwon.Markwon

class NotebookAdapter(
    val cells: MutableList<Cell>,
    private val onRunCell: (Int) -> Unit,
    private val markwon: Markwon,
    private val onDeleteRequested: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
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
            is CodeCellViewHolder -> holder.bind(cells[position] as Cell.Code, ::updateSource)
            is MarkdownCellViewHolder -> holder.bind(cells[position] as Cell.Markdown, ::updateSource)
        }
        wireActions(holder)
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

    fun moveCell(from: Int, to: Int) {
        if (from == to || from !in cells.indices || to !in cells.indices) return
        cells.add(to, cells.removeAt(from))
        notifyItemMoved(from, to)
    }

    fun deleteCell(position: Int): Cell {
        val cell = cells.removeAt(position)
        notifyItemRemoved(position)
        return cell
    }

    fun restoreCell(position: Int, cell: Cell) {
        // ponytail: clamp, not full stale-position tracking — a shifted slot
        // beats a crash when other cells were deleted during the Undo window
        val pos = position.coerceIn(0, cells.size)
        cells.add(pos, cell)
        notifyItemInserted(pos)
    }

    // Identity (===), not equals: cells are data classes, so two cells with the
    // same content compare equal and indexOf would hit the wrong one.
    fun updateCellOutput(cell: Cell.Code, result: ExecutionResult) {
        val position = cells.indexOfFirst { it === cell }
        if (position == -1) return // cell deleted while executing
        cells[position] = cell.copy(
            output = result.output,
            error = result.error,
            executionCount = result.executionCount
        )
        notifyItemChanged(position)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireActions(holder: RecyclerView.ViewHolder) {
        val item = holder.itemView
        item.findViewById<View>(R.id.moveUpButton).setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos > 0) moveCell(pos, pos - 1) // pos > 0 also excludes NO_POSITION
        }
        item.findViewById<View>(R.id.moveDownButton).setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < cells.size - 1) moveCell(pos, pos + 1)
        }
        item.findViewById<View>(R.id.deleteCellButton).setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDeleteRequested(pos)
        }
        item.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(holder)
            false
        }
    }

    private fun updateSource(position: Int, source: String) {
        when (val cell = cells[position]) {
            is Cell.Code -> cells[position] = cell.copy(source = source)
            is Cell.Markdown -> cells[position] = cell.copy(source = source)
        }
    }
}
