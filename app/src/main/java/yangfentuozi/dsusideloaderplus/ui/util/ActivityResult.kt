package yangfentuozi.dsusideloaderplus.ui.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
fun launcherAcResult(
    result: (Uri) -> Unit,
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    return rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            result(it.data!!.data!!)
        }
    }
}

@Composable
fun launcherAcResultMulti(
    result: (List<Uri>) -> Unit,
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    return rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            val data = it.data ?: return@rememberLauncherForActivityResult
            val clipData = data.clipData
            if (clipData != null) {
                result(
                    List(clipData.itemCount) { index ->
                        clipData.getItemAt(index).uri
                    },
                )
                return@rememberLauncherForActivityResult
            }
            data.data?.let { uri -> result(listOf(uri)) }
        }
    }
}
