package com.presaince.parallax.feature.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.presaince.parallax.data.update.UpdateState

/**
 * The update dialog: the server's version against the one installed, the notes, and the way
 * forward. Downloading is not dismissible; everything else lets the person leave.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    needsInstallPermission: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onLater: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state) {
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = onLater,
                title = { Text("A new version is available") },
                text = {
                    Column {
                        Text(
                            "Version: ${state.info.versionName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.info.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            state.info.notes.split('\n').filter { it.isNotBlank() }.forEachIndexed { i, line ->
                                if (i > 0) Spacer(Modifier.height(4.dp))
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDownload) { Text("Download") } },
                dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
            )
        }
        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Download") },
                text = {
                    Column {
                        Text(
                            "Downloading… ${(state.progress * 100).toInt().coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { state.progress })
                    }
                },
                confirmButton = {},
                dismissButton = {},
            )
        }
        is UpdateState.Downloaded -> {
            if (needsInstallPermission) {
                AlertDialog(
                    onDismissRequest = onLater,
                    title = { Text("Permission to install") },
                    text = { Text("To install the update, allow this app to install other apps.") },
                    confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open settings") } },
                    dismissButton = { TextButton(onClick = onInstall) { Text("Install") } },
                )
            } else {
                AlertDialog(
                    onDismissRequest = onLater,
                    title = { Text("Ready to install") },
                    text = { Text("The update is downloaded. Tap Install to apply it.") },
                    confirmButton = { TextButton(onClick = onInstall) { Text("Install") } },
                    dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
                )
            }
        }
        is UpdateState.Failed -> {
            AlertDialog(
                onDismissRequest = onLater,
                title = { Text("Could not download the update") },
                text = { Text(state.message.orEmpty()) },
                confirmButton = { TextButton(onClick = onRetry) { Text("Try again") } },
                dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
            )
        }
        else -> Unit
    }
}
