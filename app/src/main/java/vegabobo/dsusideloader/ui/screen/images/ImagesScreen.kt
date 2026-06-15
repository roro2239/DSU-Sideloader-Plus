package vegabobo.dsusideloader.ui.screen.images

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import vegabobo.dsusideloader.R
import vegabobo.dsusideloader.ui.components.ApplicationScreen
import vegabobo.dsusideloader.ui.components.LazySplicedColumnGroup
import vegabobo.dsusideloader.ui.components.SettingsItem
import vegabobo.dsusideloader.ui.components.TopBar
import vegabobo.dsusideloader.ui.components.buttons.ErrorButton
import vegabobo.dsusideloader.ui.components.buttons.SecondaryButton
import vegabobo.dsusideloader.ui.screen.Destinations
import vegabobo.dsusideloader.ui.sdialogs.ConfirmDSUImageReplacementSheet
import vegabobo.dsusideloader.ui.sdialogs.DeleteDSUImageSheet
import vegabobo.dsusideloader.ui.util.launcherAcResult
import vegabobo.dsusideloader.util.collectAsStateWithLifecycle

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

    val launcherSelectReplacement = launcherAcResult {
        imagesViewModel.onReplacementFileSelectionResult(it)
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
            val items = buildList {
                if (uiState.operationState != ImagesOperationState.IDLE) {
                    add(ImagesListItem.Operation)
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

                    is ImagesListItem.Image ->
                        DsuImageItem(
                            image = item.image,
                            canModify = uiState.operationState != ImagesOperationState.REPLACING &&
                                uiState.operationState != ImagesOperationState.DELETING,
                            onClickReplace = {
                                imagesViewModel.onClickReplaceImage(it)
                                launcherSelectReplacement.launch(chooseFile)
                            },
                            onClickDelete = { imagesViewModel.showDeleteImageSheet(it) },
                        )
                }
            }
        },
    )

    when (uiState.sheetDisplay) {
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

    data class Image(val image: DsuImageState) : ImagesListItem() {
        override val key: String = "image:${image.prefix}/${image.name}"
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
    onClickDelete: (DsuImageState) -> Unit,
) {
    SettingsItem(
        title = image.name,
        summary = image.prefix,
        onClick = null,
        columnTrailingContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                SecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.replace_dsu_image),
                    onClick = { onClickReplace(image) },
                    isEnabled = canModify,
                )
                Spacer(modifier = Modifier.padding(end = 6.dp))
                ErrorButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.delete_dsu_image),
                    onClick = { onClickDelete(image) },
                    isEnabled = canModify,
                )
            }
        },
    )
}
