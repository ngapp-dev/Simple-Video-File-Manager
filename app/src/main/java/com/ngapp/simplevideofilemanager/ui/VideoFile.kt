package com.ngapp.simplevideofilemanager.ui

import android.net.Uri

data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Int,
    val extension: Int,
    val isFavourite: Int,
    val isTrashed: Int
)
