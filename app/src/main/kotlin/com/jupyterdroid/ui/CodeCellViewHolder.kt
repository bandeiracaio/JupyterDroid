package com.jupyterdroid.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.R
import com.jupyterdroid.model.Cell

class CodeCellViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val executionCount: TextView = view.findViewById(R.id.executionCount)
    private val runButton: ImageButton = view.findViewById(R.id.runButton)
    val sourceEdit: EditText = view.findViewById(R.id.sourceEdit)
    private val outputContainer: ViewGroup = view.findViewById(R.id.outputContainer)
    private val outputText: TextView = view.findViewById(R.id.outputText)
    private var watcher: TextWatcher? = null

    fun bind(cell: Cell.Code, position: Int, onSourceChanged: (Int, String) -> Unit, onRun: (Int) -> Unit) {
        watcher?.let { sourceEdit.removeTextChangedListener(it) }
        sourceEdit.setText(cell.source)

        executionCount.text = if (cell.executionCount != null) "[${cell.executionCount}]" else "[ ]"
        runButton.setOnClickListener { onRun(position) }

        watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) = onSourceChanged(position, s.toString())
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        }
        sourceEdit.addTextChangedListener(watcher)

        when {
            cell.error.isNotEmpty() -> {
                outputContainer.visibility = View.VISIBLE
                outputText.setTextColor(ContextCompat.getColor(itemView.context, R.color.error_red))
                outputText.text = cell.error
            }
            cell.output.isNotEmpty() -> {
                outputContainer.visibility = View.VISIBLE
                outputText.setTextColor(ContextCompat.getColor(itemView.context, R.color.output_text))
                outputText.text = cell.output
            }
            else -> outputContainer.visibility = View.GONE
        }
    }

    companion object {
        fun create(parent: ViewGroup): CodeCellViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cell_code, parent, false)
            return CodeCellViewHolder(view)
        }
    }
}
