package cc.orbexa.hhy.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Project-owned semantic registry for the official Material vector icon set.
 * Screens must reference this registry; text, emoji and Unicode glyphs are not icons.
 */
object HhyIcons {
    val Back = Icons.AutoMirrored.Filled.ArrowBack
    val Home = Icons.Outlined.Home
    val Reward = Icons.Outlined.CardGiftcard
    val Publish = Icons.Outlined.AddCircleOutline
    val Message = Icons.Outlined.ChatBubbleOutline
    val Profile = Icons.Outlined.PersonOutline
    val Camera = Icons.Outlined.CameraAlt
    val Shield = Icons.Outlined.Shield
    val Face = Icons.Outlined.Face
    val Check = Icons.Filled.CheckCircle
    val Information = Icons.Outlined.Info
    val Error = Icons.Outlined.ErrorOutline
    val Pending = Icons.Outlined.Schedule
    val Refresh = Icons.Outlined.Refresh
    val Devices = Icons.Outlined.Devices
    val Lock = Icons.Outlined.Lock
    val Delete = Icons.Outlined.DeleteForever
    val Search = Icons.Outlined.Search
    val Projects = Icons.Outlined.WorkOutline
    val Applications = Icons.Outlined.Apps
    val Groups = Icons.Outlined.Groups
    val Verified = Icons.Outlined.Verified
    val ChevronRight = Icons.Outlined.ChevronRight
}

@Composable
fun HhyIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun HhyBackButton(onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        HhyIcon(HhyIcons.Back, contentDescription = "返回")
    }
}
