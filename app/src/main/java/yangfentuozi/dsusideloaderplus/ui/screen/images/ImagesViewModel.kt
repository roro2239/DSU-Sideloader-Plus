package yangfentuozi.dsusideloaderplus.ui.screen.images

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
import yangfentuozi.dsusideloaderplus.IPrivilegedService
import yangfentuozi.dsusideloaderplus.R
import yangfentuozi.dsusideloaderplus.core.BaseViewModel
import yangfentuozi.dsusideloaderplus.core.StorageManager
import yangfentuozi.dsusideloaderplus.model.Session
import yangfentuozi.dsusideloaderplus.service.PrivilegedProvider
import yangfentuozi.dsusideloaderplus.util.FilenameUtils

@HiltViewModel
class ImagesViewModel @Inject constructor(
    val application: Application,
    override val dataStore: DataStore<Preferences>,
    private val storageManager: StorageManager,
    var session: Session,
) : BaseViewModel(dataStore) {

    private val _uiState = MutableStateFlow(ImagesUiState())
    val uiState: StateFlow<ImagesUiState> = _uiState.asStateFlow()

    private var pendingNewImageUri: Uri = Uri.EMPTY
    private var pendingReplacementUri: Uri = Uri.EMPTY
    private var pendingImportUris: List<Uri> = emptyList()

    init {
        refreshImages()
    }

    fun refreshImages() {
        if (!session.isRoot()) {
            _uiState.update {
                it.copy(
                    operationState = ImagesOperationState.ERROR,
                    errorText = application.getString(R.string.dsu_image_requires_root_or_system),
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
                        errorText = application.getString(R.string.privileged_service_unavailable),
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
            val fileRoots = images.toDsuFileRoots()
            val currentRoot = uiState.value.currentFileRoot
                .takeIf { root -> fileRoots.any { it.id == root } }
                ?: fileRoots.firstOrNull()?.id
                ?: ""

            _uiState.update {
                it.copy(
                    images = images,
                    dsuFileRoots = fileRoots,
                    availablePrefixes = prefixes,
                    currentFileRoot = currentRoot,
                    currentFilePath = if (currentRoot.isEmpty()) "" else it.currentFilePath,
                    dsuFiles = if (currentRoot.isEmpty()) emptyList() else it.dsuFiles,
                    operationState = ImagesOperationState.IDLE,
                    errorText = "",
                )
            }
            if (currentRoot.isNotEmpty()) {
                refreshDsuFiles()
            }
        }
    }

    fun refreshDsuFiles() {
        if (!session.isRoot()) {
            return
        }

        if (uiState.value.currentFileRoot.isEmpty()) {
            _uiState.update {
                it.copy(
                    dsuFiles = emptyList(),
                    operationState = ImagesOperationState.IDLE,
                    dsuFileErrorText = application.getString(R.string.no_browsable_dsu_images),
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                operationState = ImagesOperationState.LOADING_FILES,
                dsuFileErrorText = "",
            )
        }
        PrivilegedProvider.run(
            onFail = {
                _uiState.update {
                    it.copy(
                        operationState = ImagesOperationState.IDLE,
                        dsuFileErrorText = application.getString(R.string.privileged_service_unavailable),
                    )
                }
            },
        ) {
            val root = uiState.value.currentFileRoot
            val path = uiState.value.currentFilePath
            val result = listDsuFiles(root, path)
            val error = result.firstOrNull()?.takeIf { it.startsWith("E\t") }
            if (error != null) {
                _uiState.update {
                    it.copy(
                        dsuFiles = emptyList(),
                        operationState = ImagesOperationState.IDLE,
                        dsuFileErrorText = error.removePrefix("E\t").toDsuFileErrorText(),
                    )
                }
                return@run
            }

            _uiState.update {
                it.copy(
                    dsuFiles = result.mapNotNull { line -> line.toDsuFileState(root, path) },
                    operationState = ImagesOperationState.IDLE,
                    dsuFileErrorText = "",
                    errorText = "",
                )
            }
        }
    }

    fun openDsuFileRoot(root: String) {
        _uiState.update {
            it.copy(
                currentFileRoot = root,
                currentFilePath = "",
                dsuFiles = emptyList(),
            )
        }
        refreshDsuFiles()
    }

    fun openDsuDirectory(file: DsuFileState) {
        if (!file.isDirectory) return
        _uiState.update {
            it.copy(
                currentFileRoot = file.root,
                currentFilePath = file.path,
                dsuFiles = emptyList(),
            )
        }
        refreshDsuFiles()
    }

    fun openParentDsuDirectory() {
        val currentPath = uiState.value.currentFilePath
        if (currentPath.isEmpty()) return
        _uiState.update {
            it.copy(
                currentFilePath = currentPath.substringBeforeLast("/", ""),
                dsuFiles = emptyList(),
            )
        }
        refreshDsuFiles()
    }

    fun onSharedImportSelectionResult(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pendingImportUris = uris
        _uiState.update {
            it.copy(
                pendingImportFilenames = uris.map { uri ->
                    FilenameUtils.queryName(application.contentResolver, uri)
                },
                sheetDisplay = ImagesSheetDisplayState.CONFIRM_IMPORT_SHARED_FILES,
                errorText = "",
            )
        }
    }

    fun confirmImportSharedFiles() {
        val uris = pendingImportUris
        if (uris.isEmpty()) return

        dismissSheet(clearPendingSelection = false)
        _uiState.update {
            it.copy(
                operationState = ImagesOperationState.IMPORTING,
                currentImageName = uris.size.toString(),
                errorText = "",
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            importSharedFiles(uris)
        }
    }

    fun onClickExportDsuFile(file: DsuFileState) {
        if (file.isDirectory) return
        _uiState.update {
            it.copy(
                pendingDsuFile = file,
                errorText = "",
            )
        }
    }

    fun onDsuFileExportSelectionResult(uri: Uri) {
        val file = uiState.value.pendingDsuFile ?: return
        _uiState.update {
            it.copy(
                operationState = ImagesOperationState.EXPORTING,
                currentImageName = file.name,
                errorText = "",
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val fd = application.contentResolver.openFileDescriptor(uri, "wt")
            if (fd == null) {
                onImageOperationError(application.getString(R.string.unable_to_open_output_file))
                return@launch
            }
            exportDsuSharedFile(file, fd)
        }
    }

    fun onClickAddImage(): Boolean {
        val prefix = getDefaultImagePrefix()
        if (prefix.isBlank()) {
            onImageOperationError(application.getString(R.string.no_installed_dsu_prefix_found))
            return false
        }
        _uiState.update {
            it.copy(
                pendingPrefix = prefix,
                newImageName = "",
                newImageNameError = false,
                errorText = "",
            )
        }
        return true
    }

    fun onNewImageFileSelectionResult(uri: Uri) {
        val filename = FilenameUtils.queryName(application.contentResolver, uri)
        val extension = filename.substringAfterLast(".", "").lowercase()
        if (extension != "img") {
            Toast.makeText(application, R.string.file_unsupported, Toast.LENGTH_SHORT).show()
            clearPendingSelection()
            return
        }

        val imageName = filename.substringBeforeLast(".").trim() + "_gsi"
        pendingNewImageUri = uri
        _uiState.update {
            it.copy(
                newImageName = imageName,
                newImageNameError = !isNewImageNameValid(imageName, it.pendingPrefix),
                replacementFileName = filename,
                sheetDisplay = ImagesSheetDisplayState.CONFIRM_ADD_DSU_IMAGE,
                errorText = "",
            )
        }
    }

    fun onNewImageNameChange(imageName: String) {
        _uiState.update {
            it.copy(
                newImageName = imageName,
                newImageNameError = !isNewImageNameValid(imageName, it.pendingPrefix),
            )
        }
    }

    fun confirmAddImage() {
        val prefix = uiState.value.pendingPrefix
        val imageName = uiState.value.newImageName.trim()
        if (!isNewImageNameValid(imageName, prefix)) {
            _uiState.update { it.copy(newImageNameError = true) }
            return
        }
        val uri = pendingNewImageUri
        if (uri == Uri.EMPTY) return

        dismissSheet(clearPendingSelection = false)
        _uiState.update {
            it.copy(
                operationState = ImagesOperationState.ADDING,
                currentImageName = imageName,
                errorText = "",
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val imageSize = storageManager.getFilesizeFromUri(uri)
            val fd = application.contentResolver.openFileDescriptor(uri, "r")
            if (fd == null) {
                onImageOperationError(application.getString(R.string.unable_to_open_image_file))
                return@launch
            }
            addImage(prefix, imageName, fd, imageSize)
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
            clearPendingSelection()
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

    fun onClickExportImage(image: DsuImageState) {
        _uiState.update {
            it.copy(
                pendingImage = image,
                errorText = "",
            )
        }
    }

    fun onExportFileSelectionResult(uri: Uri) {
        val image = uiState.value.pendingImage ?: return
        _uiState.update {
            it.copy(
                operationState = ImagesOperationState.EXPORTING,
                currentImageName = image.name,
                errorText = "",
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val fd = application.contentResolver.openFileDescriptor(uri, "wt")
            if (fd == null) {
                onImageOperationError(application.getString(R.string.unable_to_open_output_image_file))
                return@launch
            }
            exportImage(image, fd)
        }
    }

    fun confirmReplaceImage() {
        val image = uiState.value.pendingImage ?: return
        val uri = pendingReplacementUri
        if (uri == Uri.EMPTY) return

        dismissSheet(clearPendingSelection = false)
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
                onImageOperationError(application.getString(R.string.unable_to_open_image_file))
                return@launch
            }
            replaceImage(image, fd, imageSize)
        }
    }

    private fun exportImage(
        image: DsuImageState,
        fd: ParcelFileDescriptor,
    ) {
        PrivilegedProvider.run(
            onFail = {
                fd.close()
                onImageOperationError(application.getString(R.string.privileged_service_unavailable))
            },
        ) {
            val error = try {
                exportDsuBackingImage(image.prefix, image.name, fd)
            } finally {
                fd.close()
            }
            if (error.isEmpty()) {
                clearPendingSelection()
                _uiState.update {
                    it.copy(
                        operationState = ImagesOperationState.IDLE,
                        errorText = "",
                    )
                }
            } else {
                onImageOperationError(error)
            }
        }
    }

    private fun addImage(
        prefix: String,
        imageName: String,
        fd: ParcelFileDescriptor,
        imageSize: Long,
    ) {
        PrivilegedProvider.run(
            onFail = {
                fd.close()
                onImageOperationError(application.getString(R.string.privileged_service_unavailable))
            },
        ) {
            val error = try {
                addDsuBackingImage(prefix, imageName, fd, imageSize, true)
            } finally {
                fd.close()
            }
            if (error.isEmpty()) {
                clearPendingSelection()
                refreshImages()
            } else {
                onImageOperationError(error)
            }
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
                onImageOperationError(application.getString(R.string.privileged_service_unavailable))
            },
        ) {
            val error = try {
                replaceDsuBackingImage(image.prefix, image.name, fd, imageSize, true)
            } finally {
                fd.close()
            }
            if (error.isEmpty()) {
                clearPendingSelection()
                refreshImages()
            } else {
                onImageOperationError(error)
            }
        }
    }

    private fun importSharedFiles(uris: List<Uri>) {
        val sharedRoot = uiState.value.dsuFileRoots.firstOrNull { it.isSharedStorage }
        if (sharedRoot == null) {
            _uiState.update {
                it.copy(
                    operationState = ImagesOperationState.IDLE,
                    dsuFileErrorText = application.getString(R.string.dsu_userdata_image_not_found),
                )
            }
            return
        }

        PrivilegedProvider.run(
            onFail = { onImageOperationError(application.getString(R.string.privileged_service_unavailable)) },
        ) {
            for (uri in uris) {
                val filename = FilenameUtils.queryName(application.contentResolver, uri)
                val fileSize = storageManager.getFilesizeFromUri(uri)
                val fd = application.contentResolver.openFileDescriptor(uri, "r")
                if (fd == null) {
                    onImageOperationError(
                        application.getString(R.string.unable_to_open_input_file, filename),
                    )
                    return@run
                }
                val error = importFileToDsuShared(sharedRoot.id, fd, filename, fileSize, true)
                if (error.isNotEmpty()) {
                    onImageOperationError(error.toDsuFileErrorText())
                    return@run
                }
            }
            clearPendingSelection()
            refreshDsuFiles()
        }
    }

    private fun exportDsuSharedFile(
        file: DsuFileState,
        fd: ParcelFileDescriptor,
    ) {
        PrivilegedProvider.run(
            onFail = {
                fd.close()
                onImageOperationError(application.getString(R.string.privileged_service_unavailable))
            },
        ) {
            val error = exportDsuFile(file.root, file.path, fd)
            if (error.isEmpty()) {
                clearPendingSelection()
                _uiState.update {
                    it.copy(
                        operationState = ImagesOperationState.IDLE,
                        errorText = "",
                    )
                }
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

    fun dismissSheet() = dismissSheet(clearPendingSelection = true)

    private fun dismissSheet(clearPendingSelection: Boolean) {
        if (clearPendingSelection) {
            clearPendingSelection()
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
            onFail = { onImageOperationError(application.getString(R.string.privileged_service_unavailable)) },
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
        clearPendingSelection()
        _uiState.update {
            it.copy(
                operationState = ImagesOperationState.ERROR,
                errorText = error,
                sheetDisplay = ImagesSheetDisplayState.NONE,
            )
        }
    }

    private fun clearPendingSelection() {
        pendingNewImageUri = Uri.EMPTY
        pendingReplacementUri = Uri.EMPTY
        pendingImportUris = emptyList()
        _uiState.update {
            it.copy(
                pendingImage = null,
                pendingDsuFile = null,
                pendingPrefix = "",
                newImageName = "",
                newImageNameError = false,
                pendingImportFilenames = emptyList(),
                replacementFileName = "",
            )
        }
    }

    private fun getDefaultImagePrefix(): String {
        return uiState.value.images.firstOrNull()?.prefix
            ?: uiState.value.availablePrefixes.firstOrNull()
            ?: ""
    }

    private fun isNewImageNameValid(imageName: String, prefix: String): Boolean {
        val trimmedImageName = imageName.trim()
        return trimmedImageName.isNotBlank() &&
            IMAGE_NAME_REGEX.matches(trimmedImageName) &&
            uiState.value.images.none {
                it.prefix == prefix && it.name == trimmedImageName
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

    private fun String.toDsuFileState(root: String, parentPath: String): DsuFileState? {
        val parts = split("\t")
        if (parts.size < 3) return null

        val name = parts[1]
        val path = listOf(parentPath, name)
            .filter { it.isNotEmpty() }
            .joinToString("/")

        return DsuFileState(
            root = root,
            path = path,
            name = name,
            isDirectory = parts[0] == "D",
            size = parts[2].toLongOrNull() ?: 0L,
        )
    }

    private fun List<DsuImageState>.toDsuFileRoots(): List<DsuFileRootState> {
        return flatMap { image ->
            if (image.name.normalizedDsuPartitionName() == "userdata") {
                listOf(
                    DsuFileRootState(
                        id = image.toRootId("media/0"),
                        label = "/sdcard",
                        isSharedStorage = true,
                    ),
                )
            } else {
                listOf(
                    DsuFileRootState(
                        id = image.toRootId(""),
                        label = "/${image.name}",
                    ),
                )
            }
        }.distinctBy { it.id }
    }

    private fun DsuImageState.toRootId(relativeRoot: String): String {
        return "$prefix\t$name\t$relativeRoot"
    }

    private fun String.toDsuFileErrorText(): String {
        return when (this) {
            ERROR_DSU_USERDATA_OFFLINE_UNAVAILABLE ->
                application.getString(R.string.dsu_userdata_offline_unavailable)

            else -> this
        }
    }

    private fun String.normalizedDsuPartitionName(): String {
        return removeSuffix(".img")
            .removeSuffix("_gsi")
            .lowercase()
    }

    private companion object {
        val IMAGE_NAME_REGEX = Regex("[A-Za-z0-9_.-]+")
        const val ERROR_DSU_USERDATA_OFFLINE_UNAVAILABLE = "DSU_USERDATA_OFFLINE_UNAVAILABLE"
    }
}
