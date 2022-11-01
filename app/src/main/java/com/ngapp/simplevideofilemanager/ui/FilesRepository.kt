package com.ngapp.simplevideofilemanager.ui

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.ngapp.simplevideofilemanager.app.Networking
import com.ngapp.simplevideofilemanager.utils.haveQ
import com.ngapp.simplevideofilemanager.utils.haveR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FilesRepository(
    private val context: Context
) {
    private var observer: ContentObserver? = null

    fun observeFiles(onChange: () -> Unit) {
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                onChange()
            }
        }
        context.contentResolver.registerContentObserver(
//            MediaStore.Files.getContentUri("external"),
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!
        )
    }

    fun unregisterObserver() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
    }

    suspend fun getFile(): List<VideoFile> {
        val files = mutableListOf<VideoFile>()
        withContext(Dispatchers.IO) {
            context.contentResolver.query(
//                MediaStore.Files.getContentUri("external"),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                null,
                null,
                null,
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id =
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val name =
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME))
                    val size =
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                    val extension =
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                    val uri =
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    var isFavourite = 3
                    var isTrashed = 3
                    if (haveR()) {
                        isFavourite =
                            cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                    MediaStore.MediaColumns.IS_FAVORITE
                                )
                            )
                        isTrashed = cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                MediaStore.MediaColumns.IS_TRASHED
                            )
                        )
                    }

                    files += VideoFile(
                        id,
                        uri,
                        name,
                        size,
                        extension,
                        isFavourite,
                        isTrashed
                    )
                }
            }
        }
        return files
    }


    suspend fun saveFile(name: String, url: String) {
        withContext(Dispatchers.IO) {
            val fileUrl = saveFileDetails(name, url)
            downloadFile(url, fileUrl)
            makeFileVisible(fileUrl)
        }
    }

    private fun getMimeType(url: String?): String? {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return type
    }

    private fun saveFileDetails(name: String, url: String): Uri {
        val volume = if (haveQ()) {
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        } else {
            MediaStore.VOLUME_EXTERNAL
        }
        val fileCollectionUri = MediaStore.Downloads.getContentUri(volume)
        val fileDetails = ContentValues().apply {
            put(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                name + url.substring(url.lastIndexOf("."))
            )
            put(MediaStore.Files.FileColumns.MIME_TYPE, getMimeType(url))
            if (haveQ()) {
                val dirDest = File(Environment.DIRECTORY_DOWNLOADS, "Files")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, "$dirDest${File.separator}")
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }

        }
        return context.contentResolver.insert(fileCollectionUri, fileDetails)!!
    }

    private fun makeFileVisible(fileUri: Uri) {
        if (haveQ().not()) return

        val fileDetails = ContentValues().apply {
            put(MediaStore.Files.FileColumns.IS_PENDING, 0)
        }
        context.contentResolver.update(fileUri, fileDetails, null, null)
    }

    suspend fun downloadFile(url: String, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            Networking.api
                .getFile(url)
                .byteStream()
                .use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
        }
    }

    suspend fun deleteFile(id: Long) {
        withContext(Dispatchers.IO) {
            val uri =
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            context.contentResolver.delete(uri, null, null)
        }
    }

    fun addFileToFavourites(file: VideoFile) {
        val fileDetails = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_FAVORITE, true)
        }
        context.contentResolver.update(file.uri, fileDetails, null, null)
    }

    fun removeFileFromFavourites(file: VideoFile) {
        val fileDetails = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_FAVORITE, false)
        }
        context.contentResolver.update(file.uri, fileDetails, null, null)
    }

    fun moveFileToTrash(file: VideoFile) {
        val fileDetails = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, true)
        }
        context.contentResolver.update(file.uri, fileDetails, null, null)
    }

    fun restoreFileFromTrash(file: VideoFile) {
        val fileDetails = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, false)
        }
        context.contentResolver.update(file.uri, fileDetails, null, null)
    }
}