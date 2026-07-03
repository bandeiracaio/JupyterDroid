package com.jupyterdroid.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.R
import com.jupyterdroid.model.Cell
import com.jupyterdroid.util.ErrorReporter

class CodeCellViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val sourceEdit: EditText = view.findViewById(R.id.sourceEdit)
    val outputText: TextView = view.findViewById(R.id.outputText)
    val copyErrorButton: Button = view.findViewById(R.id.copyErrorButton)
    private var watcher: TextWatcher? = null
    private val syntaxColors = PythonHighlighter.Colors(
        keyword = ContextCompat.getColor(view.context, R.color.syntax_keyword),
        string = ContextCompat.getColor(view.context, R.color.syntax_string),
        comment = ContextCompat.getColor(view.context, R.color.syntax_comment),
        number = ContextCompat.getColor(view.context, R.color.syntax_number),
    )
    private var highlighting = false

    fun bind(cell: Cell.Code, onSourceChanged: (Int, String) -> Unit) {
        // Remove old watcher before setText to avoid feedback loop on rebind
        watcher?.let { sourceEdit.removeTextChangedListener(it) }
        sourceEdit.setText(cell.source)
        PythonHighlighter.highlight(sourceEdit.text, syntaxColors)

        watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (highlighting) return
                highlighting = true
                PythonHighlighter.highlight(s, syntaxColors)
                highlighting = false
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onSourceChanged(pos, s.toString())
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        }
        sourceEdit.addTextChangedListener(watcher)

        when {
            cell.error.isNotEmpty() -> {
                outputText.visibility = View.VISIBLE
                outputText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.error_red)
                )
                outputText.text = cell.error
                copyErrorButton.visibility = View.VISIBLE
                copyErrorButton.setOnClickListener {
                    ErrorReporter.copyFromText(itemView.context, "Cell execution", cell.error, extra = cell.source)
                    Toast.makeText(itemView.context, "Copied — paste to Claude", Toast.LENGTH_SHORT).show()
                }
            }
            cell.output.isNotEmpty() -> {
                outputText.visibility = View.VISIBLE
                outputText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.output_text)
                )
                outputText.text = cell.output
                copyErrorButton.visibility = View.GONE
            }
            else -> {
                outputText.visibility = View.GONE
                copyErrorButton.visibility = View.GONE
            }
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
