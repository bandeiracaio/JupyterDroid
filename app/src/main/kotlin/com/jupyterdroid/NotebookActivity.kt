package com.jupyterdroid

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.snackbar.Snackbar
import com.jupyterdroid.kernel.KernelManager
import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.NotebookJson
import com.jupyterdroid.ui.NotebookAdapter
import com.jupyterdroid.ui.PipInstallBottomSheet
import com.jupyterdroid.util.ErrorReporter
import com.jupyterdroid.util.NotebookFile
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NotebookActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_URI = "uri"
    }

    private lateinit var adapter: NotebookAdapter
    private lateinit var km: KernelManager
    private var notebookJson: NotebookJson = NotebookJson()
    private var currentFile: File? = null
    private var currentUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notebook)

        km = KernelManager.getInstance()

        val cells: MutableList<Cell> = when {
            intent.hasExtra(EXTRA_URI) -> {
                currentUri = Uri.parse(intent.getStringExtra(EXTRA_URI))
                try {
                    val (json, loaded) = NotebookFile.read(contentResolver, currentUri!!)
                    notebookJson = json
                    loaded.toMutableList()
                } catch (e: Exception) {
                    showCopyableError("Open notebook", e)
                    mutableListOf()
                }
            }
            intent.hasExtra(EXTRA_FILE_PATH) -> {
                currentFile = File(intent.getStringExtra(EXTRA_FILE_PATH)!!)
                try {
                    val (json, loaded) = NotebookFile.read(currentFile!!)
                    notebookJson = json
                    loaded.toMutableList()
                } catch (e: Exception) {
                    showCopyableError("Open notebook", e)
                    mutableListOf()
                }
            }
            else -> mutableListOf()
        }

        adapter = NotebookAdapter(
            cells,
            onRunCell = { position -> runCell(position) },
            markwon = Markwon.create(this),
            onDeleteRequested = { position -> deleteCellWithUndo(position) },
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) }
        )

        val recycler = findViewById<RecyclerView>(R.id.cellsRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveCell(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) deleteCellWithUndo(pos)
            }

            // Drag starts from the handle only — long-press inside a cell's
            // EditText means text selection, not reorder.
            override fun isLongPressDragEnabled() = false
        })
        itemTouchHelper.attachToRecyclerView(recycler)

        val bar = findViewById<BottomAppBar>(R.id.bottomAppBar)
        bar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_run_cell -> {
                    val pos = (recycler.layoutManager as LinearLayoutManager)
                        .findLastVisibleItemPosition()
                    if (pos >= 0) runCell(pos)
                    true
                }
                R.id.action_run_all -> { runAllCells(); true }
                R.id.action_add_code -> { adapter.addCodeCell(); true }
                R.id.action_add_md -> { adapter.addMarkdownCell(); true }
                R.id.action_pip -> {
                    PipInstallBottomSheet().show(supportFragmentManager, PipInstallBottomSheet.TAG)
                    true
                }
                R.id.action_save -> { save(); true }
                else -> false
            }
        }

        title = currentFile?.name
            ?: currentUri?.let { DocumentFile.fromSingleUri(this, it)?.name }
            ?: "Untitled.ipynb"
    }

    private lateinit var itemTouchHelper: ItemTouchHelper

    private fun deleteCellWithUndo(position: Int) {
        val cell = adapter.deleteCell(position)
        Snackbar.make(findViewById(R.id.cellsRecyclerView), "Cell deleted", Snackbar.LENGTH_LONG)
            .setAction("Undo") { adapter.restoreCell(position, cell) }
            .show()
    }

    private fun runCell(position: Int) {
        val cell = adapter.cells.getOrNull(position) as? Cell.Code ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    km.execute(cell.source)
                } catch (e: Exception) {
                    km.reset()
                    withContext(Dispatchers.Main) {
                        showCopyableError("Kernel crash", e, extra = cell.source)
                    }
                    null
                }
            }
            result?.let { adapter.updateCellOutput(cell, it) }
        }
    }

    private fun runAllCells() {
        // Sequential: one coroutine, forEach in order — parallel would cause race conditions in Python globals
        lifecycleScope.launch {
            adapter.cells.toList().forEach { c ->
                (c as? Cell.Code)?.let { cell ->
                    val result = withContext(Dispatchers.IO) {
                        try { km.execute(cell.source) } catch (e: Exception) { null }
                    }
                    result?.let { adapter.updateCellOutput(cell, it) }
                }
            }
        }
    }

    private fun showCopyableError(action: String, throwable: Throwable, extra: String? = null) {
        Snackbar.make(
            findViewById(R.id.cellsRecyclerView),
            action,
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Copy details") {
            ErrorReporter.copyFromThrowable(this, action, throwable, extra)
            Toast.makeText(this, "Copied — paste to Claude", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun save() {
        currentUri?.let { uri ->
            try {
                NotebookFile.write(contentResolver, uri, notebookJson, adapter.cells)
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showCopyableError("Save notebook", e)
            }
            return
        }
        val file = currentFile ?: run {
            val dir = getExternalFilesDir(null) ?: filesDir
            File(dir, "notebook_${System.currentTimeMillis()}.ipynb").also { currentFile = it }
        }
        try {
            NotebookFile.write(notebookJson, adapter.cells, file)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showCopyableError("Save notebook", e)
        }
    }

    override fun onPause() {
        super.onPause()
        save()
    }
}
