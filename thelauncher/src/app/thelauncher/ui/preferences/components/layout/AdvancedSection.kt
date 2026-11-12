package app.thelauncher.ui.preferences.components.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R

/**
 * A row reading "Advanced" that opens to show settings most people never need, so a screen
 * starts with the common ones only.
 */
@Composable
fun AdvancedSection(
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "advanced")
    PreferenceTemplate(
        title = { Text(text = stringResource(id = R.string.advanced)) },
        modifier = modifier,
        endWidget = {
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
        },
        onClick = { open = !open },
    )
    ExpandAndShrink(visible = open) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            content()
        }
    }
}
