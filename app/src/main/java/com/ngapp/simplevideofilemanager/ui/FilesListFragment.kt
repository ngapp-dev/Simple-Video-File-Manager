package com.ngapp.simplevideofilemanager.ui

import android.Manifest
import android.app.Activity
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.ngapp.simplevideofilemanager.R
import com.ngapp.simplevideofilemanager.databinding.FragmentListFileBinding
import com.ngapp.simplevideofilemanager.ui.adapter.FilesListAdapter
import com.ngapp.simplevideofilemanager.utils.ViewBindingFragment
import com.ngapp.simplevideofilemanager.utils.haveQ
import com.ngapp.simplevideofilemanager.utils.launchAndCollectIn


class FilesListFragment :
    ViewBindingFragment<FragmentListFileBinding>(FragmentListFileBinding::inflate) {

    private val viewModel: FilesViewModel by viewModels()
    private lateinit var filesListAdapter: FilesListAdapter

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var recoverableActionLauncherDelete: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var recoverableActionLauncherMoveToTrash: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var recoverableActionLauncherFavourites: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPermissionResultListener()
        initRecoverableActionListener()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initList()
        initCallBacks()
        bindViewModel()

        if (hasPermission().not()) {
            requestPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updatePermissionState(hasPermission())
    }

    private fun initList() {
        filesListAdapter = FilesListAdapter(
            onItemClick = { file ->
                openFile(file)
            },
            onItemLongClick = { file, view ->
                openPopupMenu(file, view)
            }
        )
        with(binding.filesList) {
            adapter = filesListAdapter
            setHasFixedSize(true)


            val linearLayoutManager = GridLayoutManager(context, 3)
            layoutManager = linearLayoutManager
        }
    }

    private fun openPopupMenu(file: VideoFile, view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.inflate(R.menu.file_popup_menu)
        if (file.isFavourite == TRUE) {
            popup.menu.findItem(R.id.favouriteItem).setTitle(R.string.favourite_remove)
        } else if (file.isFavourite == FALSE) {
            popup.menu.findItem(R.id.favouriteItem).setTitle(R.string.favourite)
        }
        popup.setOnMenuItemClickListener { item: MenuItem? ->
            when (item!!.itemId) {
                R.id.favouriteItem -> {
                    addToFavouritesFile(file, popup)
                }
                R.id.deleteItem -> {
                    deleteFile(file)
                }
            }
            true
        }
        popup.show()
    }

    private fun initCallBacks() {
        binding.addFAB.setOnClickListener {
            val navController = findNavController()
            navController.navigate(FilesListFragmentDirections.actionDownloadFileListFragmentToDownloadFileFragment())
        }
    }

    private fun deleteFile(file: VideoFile) {
        if (file.isTrashed == CANT_BE) {
            viewModel.deleteFile(file.id)
        } else {
            viewModel.moveFileToTrash(file)
            val moveToTrashSnackbar = Snackbar.make(
                binding.root,
                R.string.move_to_trash,
                Snackbar.LENGTH_LONG
            )
            moveToTrashSnackbar.setAction(
                R.string.undo_string,
                object : View.OnClickListener {
                    override fun onClick(view: View) {
                        viewModel.restoreFileFromTrash(file)
                        val snackbarRestored =
                            Snackbar.make(
                                binding.root,
                                R.string.restored,
                                Snackbar.LENGTH_SHORT
                            )
                        snackbarRestored.show()
                    }
                })
            moveToTrashSnackbar.show()
        }
    }

    private fun addToFavouritesFile(file: VideoFile, popup: PopupMenu) {
        if (file.isFavourite == TRUE) {
            popup.menu.findItem(R.id.favouriteItem).setTitle(R.string.favourite_remove)
            viewModel.removeFileFromFavourites(file)
        } else if (file.isFavourite == FALSE) {
            popup.menu.findItem(R.id.favouriteItem).setTitle(R.string.favourite)
            viewModel.addFileToFavourites(file)
        }
    }

    private fun openFile(file: VideoFile) {
        val type = when (file.extension) {
            MEDIA_TYPE_IMAGE -> "image/*"
            MEDIA_TYPE_AUDIO -> "audio/*"
            MEDIA_TYPE_VIDEO -> "video/*"
            MEDIA_TYPE_DOCUMENT -> "text/*"
            else -> "*/*"

        }
        val intent = Intent(Intent.ACTION_VIEW, file.uri)
        intent.setDataAndType(file.uri, type)
        startActivity(intent)
    }


    private fun bindViewModel() {
        viewModel.toastFlow.launchAndCollectIn(viewLifecycleOwner) {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        }
        viewModel.filesFlow.launchAndCollectIn(viewLifecycleOwner) { files ->
            files?.size
            files?.filter { it.isTrashed == FALSE }
            filesListAdapter.submitList(files)
        }
        viewModel.permissionGrantedFlow.launchAndCollectIn(viewLifecycleOwner) {
            it?.let { updatePermissionUi(it) }
        }
        viewModel.recoverableActionDeleteFlow.launchAndCollectIn(viewLifecycleOwner) {
            it?.let { handleRecoverableActionDelete(it) }
        }
        viewModel.recoverableActionMoveToTrashFlow.launchAndCollectIn(viewLifecycleOwner) {
            it?.let { handleRecoverableActionMoveToTrash(it) }
        }
        viewModel.recoverableActionAddToFavouritesFlow.launchAndCollectIn(viewLifecycleOwner) {
            it?.let { handleRecoverableActionFavourites(it) }
        }
    }

    private fun updatePermissionUi(isGranted: Boolean) {
        binding.addFAB.isVisible = isGranted
    }

    private fun hasPermission(): Boolean {
        return PERMISSIONS.all {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun initPermissionResultListener() {
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionToGrantedMap: Map<String, Boolean> ->
            if (permissionToGrantedMap.values.all { it }) {
                viewModel.permissionGranted()
            } else {
                viewModel.permissionDenied()
            }
        }
    }

    private fun initRecoverableActionListener() {
        recoverableActionLauncherDelete = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { activityResult ->
            val isConfirmed = activityResult.resultCode == Activity.RESULT_OK
            if (isConfirmed) {
                viewModel.confirmDelete()
            } else {
                viewModel.declineDelete()
            }
        }
        recoverableActionLauncherMoveToTrash = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { activityResult ->
            val isConfirmed = activityResult.resultCode == Activity.RESULT_OK
            if (isConfirmed) {
                viewModel.confirmMoveToTrash()
            } else {
                viewModel.declineMoveToTrash()
            }
        }
        recoverableActionLauncherFavourites = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { activityResult ->
            val isConfirmed = activityResult.resultCode == Activity.RESULT_OK
            if (isConfirmed) {
                viewModel.confirmAddToFavourites()
            } else {
                viewModel.declineAddToFavourites()
            }
        }

    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(PERMISSIONS.toTypedArray())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRecoverableActionDelete(action: RemoteAction) {
        val request = IntentSenderRequest.Builder(action.actionIntent.intentSender)
            .build()
        recoverableActionLauncherDelete.launch(request)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRecoverableActionMoveToTrash(action: RemoteAction) {
        val request = IntentSenderRequest.Builder(action.actionIntent.intentSender)
            .build()
        recoverableActionLauncherMoveToTrash.launch(request)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRecoverableActionFavourites(action: RemoteAction) {
        val request = IntentSenderRequest.Builder(action.actionIntent.intentSender)
            .build()
        recoverableActionLauncherFavourites.launch(request)
    }

    companion object {
        private val PERMISSIONS = listOfNotNull(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
                .takeIf { haveQ().not() }
        )

        private const val MEDIA_TYPE_IMAGE = 1
        private const val MEDIA_TYPE_AUDIO = 2
        private const val MEDIA_TYPE_VIDEO = 3
        private const val MEDIA_TYPE_DOCUMENT = 6

        private const val TRUE = 1
        private const val FALSE = 0
        private const val CANT_BE = 3
    }


}