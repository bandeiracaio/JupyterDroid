package com.jupyterdroid.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.R
import com.jupyterdroid.model.Cell
import io.noties.markwon.Markwon

class MarkdownCellViewHolder(view: View, private val markwon: Markwon) : RecyclerView.ViewHolder(view) {
    private val sourceEdit: EditText = view.findViewById(R.id.sourceEdit)
    private val renderedText: TextView = view.findViewById(R.id.renderedText)
    private var textWatcher: TextWatcher? = null

    fun bind(cell: Cell.Markdown, position: Int, onSourceChanged: (Int, String) -> Unit) {
        sourceEdit.removeTextChangedListener(textWatcher)
        sourceEdit.setText(cell.source)
        render(cell.source)

        renderedText.setOnClickListener {
            renderedText.visibility = View.GONE
            sourceEdit.visibility = View.VISIBLE
            sourceEdit.requestFocus()
        }

        sourceEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) render(sourceEdit.text.toString())
        }

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) = onSourceChanged(position, s.toString())
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        }
        textWatcher = watcher
        sourceEdit.addTextChangedListener(watcher)
    }

    private fun render(source: String) {
        sourceEdit.visibility = View.GONE
        renderedText.visibility = View.VISIBLE
        if (source.isEmpty()) {
            renderedText.text = "Tap to edit markdown"
        } else {
            markwon.setMarkdown(renderedText, source)
        }
    }

    companion object {
        fun create(parent: ViewGroup, markwon: Markwon): MarkdownCellViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cell_markdown, parent, false)
            return MarkdownCellViewHolder(view, markwon)
        }
    }
}
