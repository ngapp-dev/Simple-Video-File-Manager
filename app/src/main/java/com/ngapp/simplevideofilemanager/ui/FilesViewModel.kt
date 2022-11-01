package com.ngapp.simplevideofilemanager.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ngapp.simplevideofilemanager.R
import com.ngapp.simplevideofilemanager.utils.haveQ
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class FilesViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository = FilesRepository(application)

    private val filesMutableStateFlow = MutableStateFlow<List<VideoFile>?>(null)
    private val recoverableActionDeleteMutableStateFlow = MutableStateFlow<RemoteAction?>(null)
    private val recoverableActionMoveToTrashMutableStateFlow = MutableStateFlow<RemoteAction?>(null)
    private val recoverableActionAddToFavouritesMutableStateFlow =
        MutableStateFlow<RemoteAction?>(null)
    private val toastEventChannel = Channel<Int>(Channel.BUFFERED)
    private val saveSuccessEventChannel = Channel<Unit>(Channel.BUFFERED)
    private val loadingMutableStateFlow = MutableStateFlow(false)
    private val permissionGrantedMutableStateFlow = MutableStateFlow<Boolean?>(null)

    private var isObservingStarted: Boolean = false
    private var pendingDeleteId: Long? = null
    private var pendingMoveToTrash: VideoFile? = null
    private var pendingAddToFavourites: VideoFile? = null


    val filesFlow: Flow<List<VideoFile>?>
        get() = filesMutableStateFlow.asStateFlow()

    val recoverableActionDeleteFlow: Flow<RemoteAction?>
        get() = recoverableActionDeleteMutableStateFlow.asStateFlow()

    val recoverableActionMoveToTrashFlow: Flow<RemoteAction?>
        get() = recoverableActionMoveToTrashMutableStateFlow.asStateFlow()

    val recoverableActionAddToFavouritesFlow: Flow<RemoteAction?>
        get() = recoverableActionAddToFavouritesMutableStateFlow.asStateFlow()

    val toastFlow: Flow<Int>
        get() = toastEventChannel.receiveAsFlow()

    val saveSuccessFlow: Flow<Unit>
        get() = saveSuccessEventChannel.receiveAsFlow()

    val loadingFlow: Flow<Boolean>
        get() = loadingMutableStateFlow.asStateFlow()

    val permissionGrantedFlow: Flow<Boolean?>
        get() = permissionGrantedMutableStateFlow.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        repository.unregisterObserver()
    }

    fun updatePermissionState(isGranted: Boolean) = if (isGranted) {
        permissionGranted()
    } else {
        permissionDenied()
    }

    fun permissionGranted() {
        loadFiles()
        if (isObservingStarted.not()) {
            repository.observeFiles { loadFiles() }
            isObservingStarted = true
        }
        permissionGrantedMutableStateFlow.value = true
    }

    fun permissionDenied() {
        permissionGrantedMutableStateFlow.value = false
    }

    fun saveVideoWithDirectorySelection(url: String, uri: Uri) {
        loadingMutableStateFlow.value = true
        viewModelScope.launch {
            try {
                repository.downloadFile(url, uri)
                loadingMutableStateFlow.value = false
                saveSuccessEventChannel.send(Unit)
                toastEventChannel.send(R.string.download_success)
            } catch (t: CancellationException) {
                toastEventChannel.send(R.string.download_cancel)
                loadingMutableStateFlow.value = false
            } catch (t: IllegalArgumentException) {
                if (t.message?.contains("Unsupported MIME type")!!) {
                    toastEventChannel.send(R.string.download_error_format)
                    loadingMutableStateFlow.value = false
                } else {
                    loadingMutableStateFlow.value = false
                }
            } catch (t: Throwable) {
                loadingMutableStateFlow.value = false
                toastEventChannel.send(R.string.save_error)
            }
        }
    }

    fun deleteFile(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteFile(id)
                pendingDeleteId = null
            } catch (t: Throwable) {
                if (haveQ() && t is RecoverableSecurityException) {
                    pendingDeleteId = id
                    recoverableActionDeleteMutableStateFlow.value = t.userAction
                } else {
                    toastEventChannel.send(R.string.delete_error)
                }
            }
        }
    }

    fun moveFileToTrash(downloadFile: VideoFile) {
        viewModelScope.launch {
            try {
                repository.moveFileToTrash(downloadFile)
                pendingMoveToTrash = null
            } catch (t: Throwable) {
                if (haveQ() && t is RecoverableSecurityException) {
                    pendingMoveToTrash = downloadFile
                    recoverableActionMoveToTrashMutableStateFlow.value = t.userAction
                } else {
                    toastEventChannel.send(R.string.move_to_trash_error)
                }
            }
        }
    }

    fun restoreFileFromTrash(downloadFile: VideoFile) {
        viewModelScope.launch {
            try {
                repository.restoreFileFromTrash(downloadFile)
            } catch (t: Throwable) {
                toastEventChannel.send(R.string.remove_from_trash_error)
            }
        }
    }

    fun confirmDelete() {
        pendingDeleteId?.let {
            deleteFile(it)
        }
    }

    fun declineDelete() {
        pendingDeleteId = null
    }

    fun confirmMoveToTrash() {
        pendingMoveToTrash?.let {
            moveFileToTrash(it)
        }
    }

    fun declineMoveToTrash() {
        pendingMoveToTrash = null
    }

    fun confirmAddToFavourites() {
        pendingAddToFavourites?.let {
            addFileToFavourites(it)
        }
    }

    fun declineAddToFavourites() {
        pendingAddToFavourites = null
    }

    private fun loadFiles() {
        viewModelScope.launch {
            try {
                val files = repository.getFile()
                if (files == emptyList<VideoFile>()) {
                    Log.e("FilesViewModel", "file empty list")
                }
                filesMutableStateFlow.value = files
            } catch (t: Throwable) {
                filesMutableStateFlow.value = emptyList()
                toastEventChannel.send(R.string.load_list_error)
            }
        }
    }

    fun addFileToFavourites(downloadFile: VideoFile) {
        viewModelScope.launch {
            try {
                repository.addFileToFavourites(downloadFile)
                pendingAddToFavourites = null
            } catch (t: Throwable) {
                if (!haveQ()) {
                    toastEventChannel.send(R.string.feature_unavailable)
                } else if (haveQ() && t is RecoverableSecurityException) {
                    pendingAddToFavourites = downloadFile
                    recoverableActionAddToFavouritesMutableStateFlow.value = t.userAction
                } else {
                    toastEventChannel.send(R.string.feature_unavailable)
                }
            }
        }
    }

    fun removeFileFromFavourites(downloadVideoFile: VideoFile) {
        viewModelScope.launch {
            try {
                repository.removeFileFromFavourites(downloadVideoFile)
            } catch (t: Throwable) {
                if (!haveQ()) {
                    toastEventChannel.send(R.string.feature_unavailable)
                } else if (haveQ() && t is RecoverableSecurityException) {
                    pendingAddToFavourites = downloadVideoFile
                    recoverableActionAddToFavouritesMutableStateFlow.value = t.userAction
                } else {
                    toastEventChannel.send(R.string.remove_from_fav_error)
                }
            }
        }
    }
}