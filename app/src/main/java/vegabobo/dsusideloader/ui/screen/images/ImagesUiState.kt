package vegabobo.dsusideloader.ui.screen.images

data class DsuImageState(
    val prefix: String,
    val name: String,
)

enum class ImagesOperationState {
    IDLE,
    LOADING,
    REPLACING,
    DELETING,
    ERROR,
}

enum class ImagesSheetDisplayState {
    NONE,
    CONFIRM_REPLACE_DSU_IMAGE,
    DELETE_DSU_IMAGE,
}

data class ImagesUiState(
    val images: List<DsuImageState> = emptyList(),
    val currentImageName: String = "",
    val errorText: String = "",
    val pendingImage: DsuImageState? = null,
    val replacementFileName: String = "",
    val operationState: ImagesOperationState = ImagesOperationState.LOADING,
    val sheetDisplay: ImagesSheetDisplayState = ImagesSheetDisplayState.NONE,
)
