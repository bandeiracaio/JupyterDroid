package com.jupyterdroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private val prefsName = "jupyterdroid"
    private val recentKey = "recent_notebooks"
    private lateinit var recentAdapter: RecentAdapter

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { openFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recentAdapter = RecentAdapter(loadRecent()) { path -> openNotebook(path) }

        val recycler = findViewById<RecyclerView>(R.id.recentFilesRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = recentAdapter

        findViewById<View>(R.id.newNotebookButton).setOnClickListener {
            startActivity(Intent(this, NotebookActivity::class.java))
        }

        findViewById<View>(R.id.openNotebookButton).setOnClickListener {
            openFileLauncher.launch(arrayOf("*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        recentAdapter.update(loadRecent())
    }

    private fun openFromUri(uri: Uri) {
        try {
            val file = File(cacheDir, "opened_${System.currentTimeMillis()}.ipynb")
            contentResolver.openInputStream(uri)!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            openNotebook(file.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openNotebook(path: String) {
        saveRecent(path)
        startActivity(
            Intent(this, NotebookActivity::class.java)
                .putExtra(NotebookActivity.EXTRA_FILE_PATH, path)
        )
    }

    private fun loadRecent(): List<String> =
        getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getStringSet(recentKey, emptySet())
            ?.sortedByDescending { File(it).lastModified() }
            ?: emptyList()

    private fun saveRecent(path: String) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(recentKey, mutableSetOf())!!.toMutableSet()
        set.add(path)
        prefs.edit().putStringSet(recentKey, set).apply()
    }

    inner class RecentAdapter(
        private var paths: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecentAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.fileNameText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notebook, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val path = paths[position]
            holder.text.text = path.substringAfterLast("/")
            holder.itemView.setOnClickListener { onClick(path) }
        }

        override fun getItemCount() = paths.size

        fun update(newPaths: List<String>) {
            paths = newPaths
            notifyDataSetChanged()
        }
    }
}
