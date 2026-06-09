package com.filemanager.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filemanager.app.databinding.ItemFileBinding
import java.text.SimpleDateFormat
import java.util.*

class FileAdapter(
    private val onFileClick: (FileItem) -> Unit,
    private val onFileLongClick: (FileItem) -> Boolean
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FileItem) {
            binding.tvFileName.text = item.name
            binding.tvFileSize.text = formatSize(item.size)
            binding.tvFileDate.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(Date(item.lastModified))
            binding.ivFileIcon.setImageResource(getIcon(item))
            binding.root.setOnClickListener { onFileClick(item) }
            binding.root.setOnLongClickListener { onFileLongClick(item) }
        }

        private fun getIcon(item: FileItem): Int {
            if (item.isDirectory) return R.drawable.ic_folder
            return when (item.extension) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp" -> R.drawable.ic_image
                "mp4", "mkv", "avi", "mov", "3gp" -> R.drawable.ic_video
                "mp3", "wav", "aac", "flac", "ogg" -> R.drawable.ic_audio
                "pdf" -> R.drawable.ic_pdf
                "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> R.drawable.ic_document
                "zip", "rar", "7z", "tar", "gz" -> R.drawable.ic_archive
                "apk" -> R.drawable.ic_apk
                "txt", "log", "md" -> R.drawable.ic_text
                else -> R.drawable.ic_file
            }
        }

        private fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(a: FileItem, b: FileItem) = a.path == b.path
        override fun areContentsTheSame(a: FileItem, b: FileItem) = a == b
    }
}
