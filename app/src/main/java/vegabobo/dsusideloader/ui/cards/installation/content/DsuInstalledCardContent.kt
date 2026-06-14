package vegabobo.dsusideloader.ui.cards.installation.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vegabobo.dsusideloader.R
import vegabobo.dsusideloader.ui.components.SettingsItem
import vegabobo.dsusideloader.ui.components.buttons.PrimaryButton

@Composable
fun DsuInstalledCardContent(
    onClickInstall: () -> Unit,
    onClickManageImages: () -> Unit,
    onClickRebootToDynOS: () -> Unit,
    onClickDiscardDsu: () -> Unit,
) {
    SettingsItem(
        title = stringResource(R.string.installation),
        summary = stringResource(R.string.dsu_already_installed),
        onClick = onClickInstall,
        rowTrailingContent = {
            PrimaryButton(
                modifier = Modifier.height(36.dp),
                text = stringResource(R.string.update),
                onClick = onClickInstall,
            )
        }
    )

    CardButton(
        modifier = Modifier,
        icon = Icons.Outlined.Inventory2,
        text = stringResource(id = R.string.manage_dsu_images),
        color = MaterialTheme.colorScheme.primary,
        onClick = onClickManageImages,
    )
    CardButton(
        modifier = Modifier,
        icon = Icons.Outlined.PowerSettingsNew,
        text = stringResource(id = R.string.reboot_into_dsu),
        color = MaterialTheme.colorScheme.secondary,
        onClick = onClickRebootToDynOS,
    )
    CardButton(
        modifier = Modifier
            .padding(bottom = 16.dp),
        icon = Icons.Outlined.DeleteForever,
        text = stringResource(id = R.string.discard),
        color = MaterialTheme.colorScheme.error,
        onClick = onClickDiscardDsu,
    )
}

@Composable
private fun CardButton(
    modifier: Modifier,
    icon: ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = icon,
                contentDescription = text,
                tint = color,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
