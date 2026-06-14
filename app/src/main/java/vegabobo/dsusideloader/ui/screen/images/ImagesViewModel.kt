package vegabobo.dsusideloader.ui.screen.images

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vegabobo.dsusideloader.IPrivilegedService
import vegabobo.dsusideloader.R
import vegabobo.dsusideloader.core.BaseViewModel
import vegabobo.dsusideloader.core.StorageManager
import vegabobo.dsusideloader.model.Session
import vegabobo.dsusideloader.service.PrivilegedProvider
import vegabobo.dsusideloader.util.FilenameUtils

@HiltViewModel
class ImagesViewModel @Inject constructor(
    val application: Application,
    override val dataStore: DataStore<Preferences>,
    private val storageManager: StorageManager,
    var session: Session,
) : BaseViewModel(dataStore) {

    private val _uiState = MutableStateFlow(ImagesUiState())
    val uiState: StateFlow<ImagesUiState> = _uiState.asStateFlow()

    private var pendingReplacementUri: Uri = Uri.EMPTY

    init {
        refreshImages()
    }

    fun refreshImages() {
        if (!session.isRoot()) {
            _uiState.update {
                it.copy(
                    operationState = ImagesOperationState.ERROR,
                    errorText = "Root or system mode is required.",
                )
            }
            return
        }

        _uiState.update { it.copy(operationState = ImagesOperationState.LOADING, errorText = "") }
        PrivilegedProvider.run(
            onFail = {
                _uiState.update {
                    it.copy(
                        operationState = ImagesOperationState.ERROR,
                        errorText = "Privileged service unavailable.",
                    )
                }
            },
        ) {
            val prefixes = getInstalledDsuPrefixes()
            val images = prefixes.flatMap { prefix ->
                runCatching {
                    getDsuBackingImages(prefix).map { imageName ->
                        DsuImageState(prefix = prefix, name = imageName)
                    }
                }.getOrDefault(emptyList())
            }.distinctBy { "${it.prefix}/${it.name}" }

            _uiState.update {
                it.copy(
                    images = images,
                    operationState = ImagesOperationState.IDLE,
                    errorText = "",
                )
            }
        }
    }

    fun onClickReplaceImage(image: DsuImageState) {
        _uiState.update {
            it.copy(
                pendingImage = image,
                errorText = "",
            )
        }
    }

    fun onReplacementFileSelectionResult(uri: Uri) {
        val filename = FilenameUtils.queryName(application.contentResolver, uri)
        val extension = filename.substringAfterLast(".", "").lowercase()
        if (extension != "img") {
            Toast.makeText(application, R.string.file_unsupported, Toast.LENGTH_SHORT).show()
            clearPendingReplacement()
            return
        }

        pendingReplacementUri = uri
        _uiState.update {
            it.copy(
                replacementFileName = filename,
                sheetDisplay = ImagesSheetDisplayState.CONFIRM_REPLACE_DSU_IMAGE,
                errorText = "",
            )
        }
    }

    fun confirmReplaceImage() {
        val image = uiState.value.pendingImage ?: return
        val uri = pendingReplacementUri
        if (uri == Uri.EMPTY) return

        dismissSheet(clearPendingReplacement = false)
        _uiState.update {
            it.copy(
                operationState = ImagesOperationState.REPLACING,
                currentImageName = image.name,
                errorText = "",
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val imageSize = storageManager.getFilesizeFromUri(uri)
            val fd = application.contentResolver.openFileDescriptor(uri, "r")
            if (fd == null) {
                onImageOperationError("Unable to open image file.")
                return@launch
            }
            replaceImage(image, fd, imageSize)
        }
    }

    private fun replaceImage(
        image: DsuImageState,
        fd: ParcelFileDescriptor,
        imageSize: Long,
    ) {
        PrivilegedProvider.run(
            onFail = {
                fd.close()
                onImageOperationError("Privileged service unavailable.")
            },
        ) {
            val error = try {
                replaceDsuBackingImage(image.prefix, image.name, fd, imageSize, true)
            } finally {
                fd.close()
            }
            if (error.isEmpty()) {
                clearPendingReplacement()
                refreshImages()
            } else {
                onImageOperationError(error)
            }
        }
    }

    fun showDeleteImageSheet(image: DsuImageState) {
        _uiState.update {
            it.copy(
                pendingImage = image,
                sheetDisplay = ImagesSheetDisplayState.DELETE_DSU_IMAGE,
            )
        }
    }

    fun dismissSheet() = dismissSheet(clearPendingReplacement = true)

    private fun dismissSheet(clearPendingReplacement: Boolean) {
        if (clearPendingReplacement) {
            clearPendingReplacement()
        }
        _uiState.update {
            it.copy(
                sheetDisplay = ImagesSheetDisplayState.NONE,
            )
        }
    }

    fun confirmDeleteImage() {
        val image = uiState.value.pendingImage ?: return
        dismissSheet()
        _uiState.update {
            it.copy(
                operationState = ImagesOperationState.DELETING,
                currentImageName = image.name,
                errorText = "",
            )
        }
        PrivilegedProvider.run(
            onFail = { onImageOperationError("Privileged service unavailable.") },
        ) {
            val error = deleteDsuBackingImage(image.prefix, image.name)
            if (error.isEmpty()) {
                refreshImages()
            } else {
                onImageOperationError(error)
            }
        }
    }

    private fun onImageOperationError(error: String) {
        clearPendingReplacement()
        _uiState.update {
            it.copy(
                operationState = ImagesOperationState.ERROR,
                errorText = error,
                sheetDisplay = ImagesSheetDisplayState.NONE,
            )
        }
    }

    private fun clearPendingReplacement() {
        pendingReplacementUri = Uri.EMPTY
        _uiState.update {
            it.copy(
                pendingImage = null,
                replacementFileName = "",
            )
        }
    }

    private fun IPrivilegedService.getInstalledDsuPrefixes(): List<String> {
        val slots = runCatching { getInstalledDsuSlots() }.getOrDefault(emptyList())
        val activeSlot = runCatching { getActiveDsuSlot() }.getOrDefault("")
        val imageDirPrefix = runCatching {
            getInstalledGsiImageDir().toDsuImagePrefix()
        }.getOrNull()

        return buildList {
            if (!imageDirPrefix.isNullOrEmpty()) {
                add(imageDirPrefix)
            }
            slots.forEach { slot ->
                add("$slot/$slot/")
            }
            if (activeSlot.isNotEmpty()) {
                add("$activeSlot/$activeSlot/")
            }
            add("dsu/dsu/")
        }.distinct()
    }

    private fun String.toDsuImagePrefix(): String {
        val prefix = removePrefix("/metadata/gsi/")
            .removePrefix("/data/gsi/")
            .trim('/')
        return if (prefix.isEmpty()) "" else "$prefix/"
    }
}
