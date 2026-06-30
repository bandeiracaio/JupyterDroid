package com.jupyterdroid

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.snackbar.Snackbar
import com.jupyterdroid.kernel.KernelManager
import com.jupyterdroid.model.Cell
import com.jupyterdroid.model.NotebookJson
import com.jupyterdroid.ui.NotebookAdapter
import com.jupyterdroid.ui.PipInstallBottomSheet
import com.jupyterdroid.util.NotebookFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NotebookActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
    }

    private lateinit var adapter: NotebookAdapter
    private lateinit var km: KernelManager
    private var notebookJson: NotebookJson = NotebookJson()
    private var currentFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notebook)

        km = KernelManager.getInstance(this)

        val cells: MutableList<Cell> = intent.getStringExtra(EXTRA_FILE_PATH)?.let { path ->
            currentFile = File(path)
            try {
                val (json, loaded) = NotebookFile.read(currentFile!!)
                notebookJson = json
                loaded.toMutableList()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to open: ${e.message}", Toast.LENGTH_LONG).show()
                mutableListOf()
            }
        } ?: mutableListOf()

        adapter = NotebookAdapter(cells) { position -> runCell(position) }

        val recycler = findViewById<RecyclerView>(R.id.cellsRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

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

        title = currentFile?.name ?: "Untitled.ipynb"
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
                        Snackbar.make(
                            findViewById(R.id.cellsRecyclerView),
                            "Kernel restarted",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    null
                }
            }
            result?.let { adapter.updateCellOutput(position, it) }
        }
    }

    private fun runAllCells() {
        // Sequential: one coroutine, forEach in order — parallel would cause race conditions in Python globals
        lifecycleScope.launch {
            adapter.cells.indices.forEach { i ->
                if (adapter.cells[i] is Cell.Code) {
                    val cell = adapter.cells[i] as Cell.Code
                    val result = withContext(Dispatchers.IO) {
                        try { km.execute(cell.source) } catch (e: Exception) { null }
                    }
                    result?.let { adapter.updateCellOutput(i, it) }
                }
            }
        }
    }

    private fun save() {
        val file = currentFile ?: run {
            val dir = getExternalFilesDir(null) ?: filesDir
            File(dir, "notebook_${System.currentTimeMillis()}.ipynb").also { currentFile = it }
        }
        try {
            NotebookFile.write(notebookJson, adapter.cells, file)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        save()
    }
}
