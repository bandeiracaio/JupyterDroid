package com.jupyterdroid.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jupyterdroid.R
import com.jupyterdroid.model.Cell
import com.jupyterdroid.util.ErrorReporter
import java.util.concurrent.Executors

class CodeCellViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val sourceEdit: EditText = view.findViewById(R.id.sourceEdit)
    val outputText: TextView = view.findViewById(R.id.outputText)
    val copyErrorButton: Button = view.findViewById(R.id.copyErrorButton)
    val imagesContainer: LinearLayout = view.findViewById(R.id.imagesContainer)
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
                try {
                    PythonHighlighter.highlight(s, syntaxColors)
                } finally {
                    highlighting = false
                }
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

        imagesContainer.removeAllViews()
        imagesContainer.visibility = if (cell.images.isEmpty()) View.GONE else View.VISIBLE
        for (b64 in cell.images) {
            val iv = ImageView(itemView.context).apply {
                adjustViewBounds = true
                tag = b64  // identifies which image this view is waiting for
            }
            imagesContainer.addView(
                iv,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            val cached = bitmapCache.get(b64)
            if (cached != null) {
                iv.setImageBitmap(cached)
            } else {
                // Decode off the main thread; a big matplotlib PNG decoded during bind
                // (scroll recycling, notifyItemChanged) is what caused the jank/ANRs.
                decodeExecutor.execute {
                    val bmp = decode(b64)
                    if (bmp != null) bitmapCache.put(b64, bmp)
                    iv.post {
                        // Guard against a recycled view now showing a different cell's image.
                        if (bmp != null && iv.tag == b64) iv.setImageBitmap(bmp)
                    }
                }
            }
        }
    }

    companion object {
        fun create(parent: ViewGroup): CodeCellViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cell_code, parent, false)
            return CodeCellViewHolder(view)
        }

        // Process-wide: decoded plots are cached so scrolling back never re-decodes.
        private val decodeExecutor = Executors.newSingleThreadExecutor()
        private val bitmapCache = object : LruCache<String, Bitmap>(
            (Runtime.getRuntime().maxMemory() / 8).toInt()  // ~1/8 of heap, in bytes
        ) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        private fun decode(b64: String): Bitmap? = try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: run { Log.w("CodeCellViewHolder", "Undecodable image output skipped"); null }
        } catch (e: IllegalArgumentException) {
            Log.w("CodeCellViewHolder", "Bad base64 image output skipped")
            null
        }
    }
}
