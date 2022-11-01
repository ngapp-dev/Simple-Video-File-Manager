package com.ngapp.simplevideofilemanager.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ngapp.simplevideofilemanager.R
import com.ngapp.simplevideofilemanager.databinding.ItemFileBinding
import com.ngapp.simplevideofilemanager.ui.VideoFile
import com.ngapp.simplevideofilemanager.utils.getSize

class FilesListAdapter(
    private val onItemClick: (file: VideoFile) -> Unit,
    private val onItemLongClick: (file: VideoFile, view: View) -> Unit
) : ListAdapter<VideoFile, FilesListAdapter.Holder>(FilesDiffUtilCallBack()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return Holder(
            ItemFileBinding.inflate(inflater, parent, false),
            onItemClick,
            onItemLongClick
        )
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    class Holder(
        private val binding: ItemFileBinding,
        private val onItemClick: (file: VideoFile) -> Unit,
        private val onItemLongClick: (file: VideoFile, view: View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            file: VideoFile
        ) {

            binding.root.setOnClickListener {
                onItemClick(file)
            }

            binding.root.setOnLongClickListener {
                onItemLongClick(file, binding.root)
                true
            }
            binding.nameTextView.text = file.name
            binding.sizeTextView.text = "Size: ${getSize(file.size)}"

            val fileImage = when (file.extension) {
                MEDIA_TYPE_AUDIO -> R.drawable.ic_audio
                MEDIA_TYPE_DOCUMENT -> R.drawable.ic_document
                else -> R.drawable.broken_image
            }

            Glide.with(itemView)
                .load(file.uri)
                .error(fileImage)
                .into(binding.previewImageView)

            when (file.isFavourite) {
                TRUE -> {
                    binding.favouriteImageView.isGone = false
                }
                FALSE -> {
                    binding.favouriteImageView.isGone = true
                }
                else -> {
                    binding.favouriteImageView.isGone = true
                }
            }

        }
    }


    class FilesDiffUtilCallBack : DiffUtil.ItemCallback<VideoFile>() {
        override fun areItemsTheSame(oldItem: VideoFile, newItem: VideoFile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoFile, newItem: VideoFile): Boolean {
            return oldItem == newItem
        }

    }

    companion object {
        private const val MEDIA_TYPE_AUDIO = 2
        private const val MEDIA_TYPE_DOCUMENT = 6

        private const val TRUE = 1
        private const val FALSE = 0
    }
}