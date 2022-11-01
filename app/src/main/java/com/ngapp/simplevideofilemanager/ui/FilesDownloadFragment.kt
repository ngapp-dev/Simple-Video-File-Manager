package com.ngapp.simplevideofilemanager.ui

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ngapp.simplevideofilemanager.R
import com.ngapp.simplevideofilemanager.databinding.DialogFileDownloadBinding
import com.ngapp.simplevideofilemanager.utils.NotificationChannels
import com.ngapp.simplevideofilemanager.utils.launchAndCollectIn

class FilesDownloadFragment : BottomSheetDialogFragment() {

    private var _dialogBinding: DialogFileDownloadBinding? = null
    private val dialogBinding get() = _dialogBinding!!
    private val viewModelDownload: FilesViewModel by viewModels()

    private lateinit var createDocumentLauncher: ActivityResultLauncher<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _dialogBinding = DialogFileDownloadBinding.inflate(LayoutInflater.from(context))
        val view = dialogBinding.root

        val link = dialogBinding.linkEditText.text.toString()
        initCallBacks()
        initCreateDocumentLauncher(link)

        return view
    }

    private fun initCallBacks() {
        dialogBinding.saveButton.setOnClickListener {
            val name = dialogBinding.nameEditText.text.toString()
            val link = dialogBinding.linkEditText.text.toString()
            val fileExtension = try {
                link.substring(link.lastIndexOf("."))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), it.toString(), Toast.LENGTH_SHORT).show()
            }
            createDocumentLauncher.launch(name + fileExtension)
            showProgressNotification()
            viewModelDownload.toastFlow.launchAndCollectIn(viewLifecycleOwner) { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
            viewModelDownload.saveSuccessFlow.launchAndCollectIn(viewLifecycleOwner) {
                dismiss()
            }
            viewModelDownload.loadingFlow.launchAndCollectIn(viewLifecycleOwner) { isLoading ->
                updateLoadingState(isLoading)
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        if (dialogBinding.saveButton.isEnabled == false) {
            Toast.makeText(requireContext(), R.string.download_cancel, Toast.LENGTH_SHORT).show()
        }
        super.onCancel(dialog)
    }

    private fun updateLoadingState(isLoading: Boolean) {
        dialogBinding.isLoadingProgressBar.isVisible = isLoading
        dialogBinding.saveButton.isEnabled = isLoading.not()
        dialogBinding.linkEditText.isEnabled = isLoading.not()
        dialogBinding.nameEditText.isEnabled = isLoading.not()
        if (isLoading.not()) {
            cancelProgressNotification()
        }

    }

    private fun initCreateDocumentLauncher(url: String) {
        createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument()
        ) { uri ->
            handleCreateFile(url, uri)
        }
    }

    private fun handleCreateFile(url: String, uri: Uri?) {
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.save_not, Toast.LENGTH_SHORT).show()
            return
        }
        viewModelDownload.saveVideoWithDirectorySelection(url, uri)
    }

    private fun showProgressNotification() {
        val notificationBuilder = NotificationCompat.Builder(
            requireContext(),
            NotificationChannels.BACKGROUND_CHANNEL_ID
        )
            .setContentTitle(getString(R.string.file_downloading))
            .setContentText(getString(R.string.file_download_progress))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.drawable.ic_download)

        val notification = notificationBuilder
            .setProgress(99999, 1, true)
            .build()

        NotificationManagerCompat.from(requireContext())
            .notify(PROGRESS_NOTIFICATION_ID, notification)
    }

    private fun cancelProgressNotification() {
        val notificationBuilder = NotificationCompat.Builder(
            requireContext(),
            NotificationChannels.BACKGROUND_CHANNEL_ID
        )
            .setContentTitle(getString(R.string.file_downloading))
            .setContentText(getString(R.string.file_download_progress))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(R.drawable.ic_download)

        val finalNotification = notificationBuilder
            .setContentText(getString(R.string.download_success))
            .setProgress(0, 0, false)
            .build()


        NotificationManagerCompat.from(requireContext())
            .notify(PROGRESS_NOTIFICATION_ID, finalNotification)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _dialogBinding = null
    }

    companion object {
        private const val PROGRESS_NOTIFICATION_ID = 775511
    }
}