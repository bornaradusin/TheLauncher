package app.thelauncher.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.thelauncher.backup.ui.restoreBackupOpener
import app.thelauncher.backup.ui.restoreHyperionBackupOpener
import app.thelauncher.backup.ui.restoreNovaBackupOpener
import app.thelauncher.ui.preferences.LocalIsExpandedScreen
import app.thelauncher.ui.preferences.components.NavigationActionPreference
import app.thelauncher.ui.preferences.components.controls.ClickablePreference
import app.thelauncher.ui.preferences.components.layout.PreferenceGroup
import app.thelauncher.ui.preferences.components.layout.PreferenceLayout
import app.thelauncher.ui.preferences.navigation.CreateBackup
import com.android.launcher3.R

@Composable
fun BackupAndRestorePreference(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(R.string.backup_and_restore_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(R.string.create_backup),
                subtitle = stringResource(R.string.create_backup_description),
                destination = CreateBackup,
            )
            ClickablePreference(
                label = stringResource(R.string.restore_backup),
                subtitle = stringResource(R.string.restore_backup_description),
                onClick = restoreBackupOpener(),
            )
        }
        PreferenceGroup {
            ClickablePreference(
                label = stringResource(R.string.restore_nova_backup),
                subtitle = stringResource(R.string.restore_nova_backup_description),
                onClick = restoreNovaBackupOpener(),
            )
            ClickablePreference(
                label = stringResource(R.string.restore_hyperion_backup),
                subtitle = stringResource(R.string.restore_hyperion_backup_description),
                onClick = restoreHyperionBackupOpener(),
            )
        }
    }
}
