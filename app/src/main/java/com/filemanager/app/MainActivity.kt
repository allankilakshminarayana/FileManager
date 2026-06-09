package com.filemanager.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.filemanager.app.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FileAdapter
    private var currentPath: File = Environment.getExternalStorageDirectory()
    private var allFiles: List<FileItem> = emptyList()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = FileAdapter(
            onFileClick = { handleFileClick(it) },
            onFileLongClick = { showFileOptions(it); true }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabHome.setOnClickListener {
            currentPath = Environment.getExternalStorageDirectory()
            loadFiles(currentPath)
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) loadFiles(currentPath)
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("This app needs full storage access to work properly.")
                    .setPositiveButton("Grant") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")))
                    }
                    .setNegativeButton("Cancel") { _, _ -> loadFiles(currentPath) }
                    .show()
            } else {
                loadFiles(currentPath)
            }
        } else {
            if (!hasStoragePermission()) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE)
            } else {
                loadFiles(currentPath)
            }
        }
    }

    private fun loadFiles(directory: File) {
        currentPath = directory
        supportActionBar?.title = if (directory == Environment.getExternalStorageDirectory())
            "Internal Storage" else directory.name
        supportActionBar?.subtitle = directory.absolutePath

        val files = directory.listFiles()
            ?.map { file ->
                FileItem(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                    lastModified = file.lastModified(),
                    extension = file.extension.lowercase()
                )
            }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

        allFiles = files
        adapter.submitList(files)
        binding.tvEmpty.visibility = if (files.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvFileCount.text = "${files.size} items"
    }

    private fun handleFileClick(fileItem: FileItem) {
        val file = File(fileItem.path)
        if (file.isDirectory) {
            loadFiles(file)
        } else {
            try {
                val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Open with..."))
            } catch (e: Exception) {
                Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFileOptions(fileItem: FileItem) {
        val file = File(fileItem.path)
        AlertDialog.Builder(this)
            .setTitle(fileItem.name)
            .setItems(arrayOf("Rename", "Delete", "Properties")) { _, which ->
                when (which) {
                    0 -> showRenameDialog(file)
                    1 -> confirmDelete(file)
                    2 -> showProperties(fileItem)
                }
            }.show()
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(this).apply {
            setText(file.nameWithoutExtension)
            selectAll()
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val ext = if (file.isFile && file.extension.isNotEmpty()) ".${file.extension}" else ""
                    if (file.renameTo(File(file.parent, newName + ext))) {
                        Toast.makeText(this, "Renamed", Toast.LENGTH_SHORT).show()
                        loadFiles(currentPath)
                    } else {
                        Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Delete \"${file.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                Toast.makeText(this, if (ok) "Deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                if (ok) loadFiles(currentPath)
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showProperties(fileItem: FileItem) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        AlertDialog.Builder(this)
            .setTitle("Properties")
            .setMessage("""
                Name: ${fileItem.name}
                Path: ${fileItem.path}
                Type: ${if (fileItem.isDirectory) "Folder" else fileItem.extension.uppercase().ifEmpty { "File" }}
                Size: ${formatSize(fileItem.size)}
                Modified: ${sdf.format(Date(fileItem.lastModified))}
            """.trimIndent())
            .setPositiveButton("OK", null).show()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(text: String?): Boolean {
                adapter.submitList(
                    if (text.isNullOrBlank()) allFiles
                    else allFiles.filter { it.name.contains(text, ignoreCase = true) }
                )
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_new_folder) {
            val input = EditText(this).apply { hint = "Folder name"; setPadding(48, 24, 48, 24) }
            AlertDialog.Builder(this)
                .setTitle("New Folder")
                .setView(input)
                .setPositiveButton("Create") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        if (File(currentPath, name).mkdir()) {
                            Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show()
                            loadFiles(currentPath)
                        } else {
                            Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null).show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val parent = currentPath.parentFile
        if (parent != null && currentPath != Environment.getExternalStorageDirectory()) {
            loadFiles(parent)
        } else {
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadFiles(currentPath)
        }
    }
}
