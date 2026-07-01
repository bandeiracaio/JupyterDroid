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
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val prefsName = "jupyterdroid"
    private val recentKey = "recent_notebooks_v2"
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
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            openNotebook(uri.toString())
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openNotebook(path: String) {
        saveRecent(path)
        val intent = Intent(this, NotebookActivity::class.java)
        if (path.startsWith("content://")) {
            intent.putExtra(NotebookActivity.EXTRA_URI, path)
        } else {
            intent.putExtra(NotebookActivity.EXTRA_FILE_PATH, path)
        }
        startActivity(intent)
    }

    private fun loadRecent(): List<String> =
        getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString(recentKey, "")
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun saveRecent(path: String) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val existing = loadRecent().filter { it != path }
        val updated = (listOf(path) + existing).take(20)
        prefs.edit().putString(recentKey, updated.joinToString("\n")).apply()
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
            holder.text.text = if (path.startsWith("content://")) {
                DocumentFile.fromSingleUri(holder.itemView.context, Uri.parse(path))?.name ?: path
            } else {
                path.substringAfterLast("/")
            }
            holder.itemView.setOnClickListener { onClick(path) }
        }

        override fun getItemCount() = paths.size

        fun update(newPaths: List<String>) {
            paths = newPaths
            notifyDataSetChanged()
        }
    }
}
