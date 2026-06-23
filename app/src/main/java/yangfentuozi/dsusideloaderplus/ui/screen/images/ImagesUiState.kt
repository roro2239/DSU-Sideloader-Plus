package yangfentuozi.dsusideloaderplus.ui.screen.images

data class DsuImageState(
    val prefix: String,
    val name: String,
)

data class DsuFileState(
    val root: String,
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)

data class DsuFileRootState(
    val id: String,
    val label: String,
    val isSharedStorage: Boolean = false,
)

enum class ImagesOperationState {
    IDLE,
    LOADING,
    LOADING_FILES,
    ADDING,
    IMPORTING,
    EXPORTING,
    REPLACING,
    DELETING,
    ERROR,
}

enum class ImagesSheetDisplayState {
    NONE,
    CONFIRM_ADD_DSU_IMAGE,
    CONFIRM_REPLACE_DSU_IMAGE,
    DELETE_DSU_IMAGE,
    CONFIRM_IMPORT_SHARED_FILES,
}

data class ImagesUiState(
    val images: List<DsuImageState> = emptyList(),
    val dsuFiles: List<DsuFileState> = emptyList(),
    val dsuFileErrorText: String = "",
    val dsuFileRoots: List<DsuFileRootState> = emptyList(),
    val availablePrefixes: List<String> = emptyList(),
    val currentFileRoot: String = "",
    val currentFilePath: String = "",
    val currentImageName: String = "",
    val errorText: String = "",
    val pendingImage: DsuImageState? = null,
    val pendingDsuFile: DsuFileState? = null,
    val pendingPrefix: String = "",
    val newImageName: String = "",
    val newImageNameError: Boolean = false,
    val pendingImportFilenames: List<String> = emptyList(),
    val replacementFileName: String = "",
    val operationState: ImagesOperationState = ImagesOperationState.LOADING,
    val sheetDisplay: ImagesSheetDisplayState = ImagesSheetDisplayState.NONE,
)
