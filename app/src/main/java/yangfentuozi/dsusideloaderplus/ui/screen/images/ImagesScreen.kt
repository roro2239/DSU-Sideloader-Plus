package yangfentuozi.dsusideloaderplus.ui.screen.images

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import yangfentuozi.dsusideloaderplus.R
import yangfentuozi.dsusideloaderplus.ui.components.ApplicationScreen
import yangfentuozi.dsusideloaderplus.ui.components.DialogLikeBottomSheet
import yangfentuozi.dsusideloaderplus.ui.components.LazySplicedColumnGroup
import yangfentuozi.dsusideloaderplus.ui.components.SettingsItem
import yangfentuozi.dsusideloaderplus.ui.components.TopBar
import yangfentuozi.dsusideloaderplus.ui.components.buttons.PrimaryButton
import yangfentuozi.dsusideloaderplus.ui.components.buttons.SecondaryButton
import yangfentuozi.dsusideloaderplus.ui.screen.Destinations
import yangfentuozi.dsusideloaderplus.ui.sdialogs.ConfirmDSUImageAddSheet
import yangfentuozi.dsusideloaderplus.ui.sdialogs.ConfirmDSUImageReplacementSheet
import yangfentuozi.dsusideloaderplus.ui.sdialogs.DeleteDSUImageSheet
import yangfentuozi.dsusideloaderplus.ui.util.launcherAcResult
import yangfentuozi.dsusideloaderplus.ui.util.launcherAcResultMulti
import yangfentuozi.dsusideloaderplus.util.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Images(
    navigate: (String) -> Unit,
    imagesViewModel: ImagesViewModel = hiltViewModel(),
) {
    val uiState by imagesViewModel.uiState.collectAsStateWithLifecycle()
    val bottomPadding = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 26.dp
    var chooseFile = Intent(Intent.ACTION_OPEN_DOCUMENT)
    chooseFile.type = "*/*"
    chooseFile.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream"))
    chooseFile = Intent.createChooser(chooseFile, "")

    val chooseSharedFiles = Intent.createChooser(
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        },
        "",
    )

    val launcherSelectReplacement = launcherAcResult {
        imagesViewModel.onReplacementFileSelectionResult(it)
    }
    val launcherSelectNewImage = launcherAcResult {
        imagesViewModel.onNewImageFileSelectionResult(it)
    }
    val launcherExportImage = launcherAcResult {
        imagesViewModel.onExportFileSelectionResult(it)
    }
    val launcherImportSharedFiles = launcherAcResultMulti {
        imagesViewModel.onSharedImportSelectionResult(it)
    }
    val launcherExportDsuFile = launcherAcResult {
        imagesViewModel.onDsuFileExportSelectionResult(it)
    }

    ApplicationScreen(
        enableDefaultScrollBehavior = false,
        columnContent = false,
        modifier = Modifier.padding(horizontal = 16.dp),
        topBar = {
            TopBar(
                barTitle = stringResource(id = R.string.dsu_images),
                icon = Icons.Outlined.Refresh,
                scrollBehavior = it,
                onClickIcon = { imagesViewModel.refreshImages() },
                onClickBackButton = { navigate(Destinations.Up) },
            )
        },
        content = {
            val canModifyImages = uiState.operationState == ImagesOperationState.IDLE
            val items = buildList {
                if (uiState.operationState != ImagesOperationState.IDLE) {
                    add(ImagesListItem.Operation)
                }
                add(ImagesListItem.SharedActions)
                add(ImagesListItem.Roots)
                if (uiState.currentFilePath.isNotEmpty()) {
                    add(ImagesListItem.Parent)
                }
                if (uiState.dsuFiles.isEmpty()) {
                    add(ImagesListItem.EmptyFiles)
                } else {
                    uiState.dsuFiles.forEach { add(ImagesListItem.File(it)) }
                }
                if (uiState.availablePrefixes.isNotEmpty() || uiState.images.isNotEmpty()) {
                    add(ImagesListItem.Add)
                }
                if (uiState.images.isEmpty()) {
                    add(ImagesListItem.Empty)
                } else {
                    uiState.images.forEach { add(ImagesListItem.Image(it)) }
                }
            }

            LazySplicedColumnGroup(
                items = items,
                modifier = Modifier.fillMaxSize(),
                key = { it.key },
                contentPadding = PaddingValues(bottom = bottomPadding),
            ) { item ->
                when (item) {
                    ImagesListItem.Operation ->
                        ImagesOperationItem(
                            uiState = uiState,
                            onClickRetry = { imagesViewModel.refreshImages() },
                        )

                    ImagesListItem.Empty ->
                        EmptyImagesItem()

                    ImagesListItem.SharedActions ->
                        SharedActionsItem(
                            canModify = canModifyImages,
                            canImport = uiState.dsuFileRoots.any { it.isSharedStorage },
                            onClickImport = { launcherImportSharedFiles.launch(chooseSharedFiles) },
                            onClickRefresh = { imagesViewModel.refreshDsuFiles() },
                        )

                    ImagesListItem.Roots ->
                        DsuFileRootsItem(
                            roots = uiState.dsuFileRoots,
                            currentRoot = uiState.currentFileRoot,
                            onClickRoot = { imagesViewModel.openDsuFileRoot(it) },
                        )

                    ImagesListItem.Parent ->
                        ParentDirectoryItem(
                            path = uiState.currentFilePath,
                            onClick = { imagesViewModel.openParentDsuDirectory() },
                        )

                    ImagesListItem.EmptyFiles ->
                        EmptyFilesItem(errorText = uiState.dsuFileErrorText)

                    is ImagesListItem.File ->
                        DsuFileItem(
                            file = item.file,
                            canModify = canModifyImages,
                            onClickDirectory = { imagesViewModel.openDsuDirectory(it) },
                            onClickExport = {
                                imagesViewModel.onClickExportDsuFile(it)
                                val intent = Intent.createChooser(
                                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_TITLE, it.name)
                                    },
                                    "",
                                )
                                launcherExportDsuFile.launch(intent)
                            },
                        )

                    ImagesListItem.Add ->
                        AddDsuImageItem(
                            canModify = canModifyImages,
                            onClickAdd = {
                                if (imagesViewModel.onClickAddImage()) {
                                    launcherSelectNewImage.launch(chooseFile)
                                }
                            },
                        )

                    is ImagesListItem.Image ->
                        DsuImageItem(
                            image = item.image,
                            canModify = canModifyImages,
                            onClickReplace = {
                                imagesViewModel.onClickReplaceImage(it)
                                launcherSelectReplacement.launch(chooseFile)
                            },
                            onClickExport = {
                                imagesViewModel.onClickExportImage(it)

                                val imageName = it.name
                                val filename = if (imageName.endsWith(
                                        ".img",
                                        ignoreCase = true
                                    )
                                ) imageName else "$imageName.img"
                                val intent = Intent.createChooser(
                                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_TITLE, filename)
                                    },
                                    "",
                                )
                                launcherExportImage.launch(intent)
                            },
                            onClickDelete = { imagesViewModel.showDeleteImageSheet(it) },
                        )
                }
            }
        },
    )

    when (uiState.sheetDisplay) {
        ImagesSheetDisplayState.CONFIRM_ADD_DSU_IMAGE ->
            ConfirmDSUImageAddSheet(
                prefix = uiState.pendingPrefix,
                imageName = uiState.newImageName,
                filename = uiState.replacementFileName,
                isImageNameError = uiState.newImageNameError,
                onImageNameChange = { imagesViewModel.onNewImageNameChange(it) },
                onClickConfirm = { imagesViewModel.confirmAddImage() },
                onClickCancel = { imagesViewModel.dismissSheet() },
            )

        ImagesSheetDisplayState.CONFIRM_REPLACE_DSU_IMAGE ->
            ConfirmDSUImageReplacementSheet(
                imageName = uiState.pendingImage?.name.orEmpty(),
                filename = uiState.replacementFileName,
                onClickConfirm = { imagesViewModel.confirmReplaceImage() },
                onClickCancel = { imagesViewModel.dismissSheet() },
            )

        ImagesSheetDisplayState.DELETE_DSU_IMAGE ->
            DeleteDSUImageSheet(
                imageName = uiState.pendingImage?.name.orEmpty(),
                onClickConfirm = { imagesViewModel.confirmDeleteImage() },
                onClickCancel = { imagesViewModel.dismissSheet() },
            )

        ImagesSheetDisplayState.CONFIRM_IMPORT_SHARED_FILES ->
            DialogLikeBottomSheet(
                title = stringResource(id = R.string.import_shared_files),
                icon = Icons.Outlined.UploadFile,
                text = stringResource(
                    id = R.string.import_shared_files_description,
                    uiState.pendingImportFilenames.size,
                ),
                confirmText = stringResource(id = R.string.proceed),
                cancelText = stringResource(id = R.string.cancel),
                onClickConfirm = { imagesViewModel.confirmImportSharedFiles() },
                onClickCancel = { imagesViewModel.dismissSheet() },
                content = {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = uiState.pendingImportFilenames.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

        ImagesSheetDisplayState.NONE -> {}
    }
}

private sealed class ImagesListItem {
    abstract val key: String

    data object Operation : ImagesListItem() {
        override val key: String = "operation"
    }

    data object Empty : ImagesListItem() {
        override val key: String = "empty"
    }

    data object Add : ImagesListItem() {
        override val key: String = "add"
    }

    data object SharedActions : ImagesListItem() {
        override val key: String = "shared-actions"
    }

    data object Roots : ImagesListItem() {
        override val key: String = "roots"
    }

    data object Parent : ImagesListItem() {
        override val key: String = "parent"
    }

    data object EmptyFiles : ImagesListItem() {
        override val key: String = "empty-files"
    }

    data class Image(val image: DsuImageState) : ImagesListItem() {
        override val key: String = "image:${image.prefix}/${image.name}"
    }

    data class File(val file: DsuFileState) : ImagesListItem() {
        override val key: String = "file:${file.root}/${file.path}"
    }
}

@Composable
private fun ImagesOperationItem(
    uiState: ImagesUiState,
    onClickRetry: () -> Unit,
) {
    val text = when (uiState.operationState) {
        ImagesOperationState.IDLE -> return
        ImagesOperationState.LOADING -> stringResource(id = R.string.loading_dsu_images)
        ImagesOperationState.LOADING_FILES -> stringResource(id = R.string.loading_dsu_files)
        ImagesOperationState.ADDING ->
            stringResource(id = R.string.adding_dsu_image, uiState.currentImageName)

        ImagesOperationState.IMPORTING ->
            stringResource(id = R.string.importing_shared_files, uiState.currentImageName)

        ImagesOperationState.EXPORTING ->
            stringResource(id = R.string.exporting_dsu_image, uiState.currentImageName)

        ImagesOperationState.REPLACING ->
            stringResource(id = R.string.replacing_dsu_image, uiState.currentImageName)

        ImagesOperationState.DELETING ->
            stringResource(id = R.string.deleting_dsu_image, uiState.currentImageName)

        ImagesOperationState.ERROR ->
            stringResource(id = R.string.dsu_image_operation_failed, uiState.errorText)
    }

    SettingsItem(
        title = stringResource(id = R.string.dsu_images),
        summary = text,
        onClick = null,
        columnTrailingContent = {
            if (uiState.operationState != ImagesOperationState.ERROR) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                ) {
                    Spacer(modifier = Modifier.weight(1F))
                    SecondaryButton(
                        text = stringResource(id = R.string.try_again),
                        onClick = onClickRetry,
                    )
                }
            }
        },
    )
}

@Composable
private fun SharedActionsItem(
    canModify: Boolean,
    canImport: Boolean,
    onClickImport: () -> Unit,
    onClickRefresh: () -> Unit,
) {
    SettingsItem(
        title = stringResource(id = R.string.shared_files),
        summary = stringResource(id = R.string.shared_files_description),
        onClick = null,
        rowTrailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = stringResource(id = R.string.refresh),
                    onClick = onClickRefresh,
                    isEnabled = canModify,
                )
                PrimaryButton(
                    text = stringResource(id = R.string.import_file),
                    onClick = onClickImport,
                    isEnabled = canModify && canImport,
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DsuFileRootsItem(
    roots: List<DsuFileRootState>,
    currentRoot: String,
    onClickRoot: (String) -> Unit,
) {
    val currentRootLabel = roots.firstOrNull { it.id == currentRoot }?.label.orEmpty()
    SettingsItem(
        title = stringResource(id = R.string.dsu_file_roots),
        summary = currentRootLabel.ifEmpty { stringResource(id = R.string.no_browsable_dsu_images) },
        onClick = null,
        columnTrailingContent = {
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                roots.forEach { root ->
                    SecondaryButton(
                        text = root.label,
                        onClick = { onClickRoot(root.id) },
                        isEnabled = root.id != currentRoot,
                    )
                }
            }
        },
    )
}

@Composable
private fun ParentDirectoryItem(
    path: String,
    onClick: () -> Unit,
) {
    SettingsItem(
        title = stringResource(id = R.string.parent_directory),
        summary = path,
        onClick = onClick,
    )
}

@Composable
private fun EmptyFilesItem(errorText: String) {
    SettingsItem(
        title = stringResource(id = R.string.dsu_files),
        summary = errorText.ifEmpty { stringResource(id = R.string.no_dsu_files) },
        onClick = null,
    )
}

@Composable
private fun DsuFileItem(
    file: DsuFileState,
    canModify: Boolean,
    onClickDirectory: (DsuFileState) -> Unit,
    onClickExport: (DsuFileState) -> Unit,
) {
    SettingsItem(
        title = file.name,
        summary = if (file.isDirectory) {
            stringResource(id = R.string.directory)
        } else {
            stringResource(id = R.string.file_size_bytes, file.size)
        },
        onClick = if (file.isDirectory) {
            { onClickDirectory(file) }
        } else {
            null
        },
        rowTrailingContent = {
            if (file.isDirectory) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButtonWithBackground(
                    onClick = { onClickExport(file) },
                    imageVector = Icons.Outlined.Archive,
                    contentDescription = stringResource(R.string.export_dsu_file),
                    color = MaterialTheme.colorScheme.secondary,
                    enabled = canModify,
                )
            }
        },
    )
}

@Composable
private fun AddDsuImageItem(
    canModify: Boolean,
    onClickAdd: () -> Unit,
) {
    SettingsItem(
        title = stringResource(id = R.string.add_dsu_image),
        summary = stringResource(id = R.string.add_dsu_image_description),
        onClick = null,
        rowTrailingContent = {
            PrimaryButton(
                text = stringResource(id = R.string.add),
                onClick = onClickAdd,
                isEnabled = canModify,
            )
        },
    )
}

@Composable
private fun EmptyImagesItem() {
    SettingsItem(
        title = stringResource(id = R.string.installed_dsu_images),
        summary = stringResource(id = R.string.no_installed_dsu_images),
        onClick = null,
    )
}

@Composable
private fun DsuImageItem(
    image: DsuImageState,
    canModify: Boolean,
    onClickReplace: (DsuImageState) -> Unit,
    onClickExport: (DsuImageState) -> Unit,
    onClickDelete: (DsuImageState) -> Unit,
) {
    SettingsItem(
        title = image.name,
        summary = image.prefix,
        onClick = null,
        rowTrailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButtonWithBackground(
                    onClick = { onClickReplace(image) },
                    imageVector = Icons.Outlined.SaveAs,
                    contentDescription = stringResource(R.string.replace_dsu_image),
                    color = MaterialTheme.colorScheme.primary,
                    enabled = canModify
                )

                IconButtonWithBackground(
                    onClick = { onClickExport(image) },
                    imageVector = Icons.Outlined.Archive,
                    contentDescription = stringResource(R.string.export_dsu_image),
                    color = MaterialTheme.colorScheme.secondary,
                    enabled = canModify
                )

                IconButtonWithBackground(
                    onClick = { onClickDelete(image) },
                    imageVector = Icons.Outlined.DeleteForever,
                    contentDescription = stringResource(R.string.delete_dsu_image),
                    color = MaterialTheme.colorScheme.error,
                    enabled = canModify
                )
            }
        },
    )
}

@Composable
private fun IconButtonWithBackground(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .size(32.dp),
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = color,
        )
    }
}
