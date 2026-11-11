/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.presaince.glasstube.core.AppLog
import com.presaince.glasstube.core.BugShot
import com.presaince.glasstube.core.BugUpload
import com.presaince.glasstube.core.BugUploadState
import kotlinx.coroutines.delay
import com.presaince.glasstube.ui.theme.accentTextColour

/**
 * The bug screen, adapted from oko's feedback sheet: the log, the device, the screenshot the app
 * captured on the way in, and one button that sends the whole bundle to GlassTube's drop box.
 */
@Composable
fun BugScreen(log: AppLog) {
    val context = LocalContext.current
    // The draft survives leaving the screen. Checking something and coming back used to lose the
    // note (`110857`), which is the one thing a report form must never do. It is cleared when the
    // server has the report, not when the composition goes away.
    var note by remember { mutableStateOf(BugDraft.note) }
    var includeShot by remember { mutableStateOf(BugDraft.includeShot ?: (BugShot.bitmap != null)) }
    var includeLogs by remember { mutableStateOf(BugDraft.includeLogs) }
    val upload by BugUpload.state.collectAsStateWithLifecycle()
    // The list is a snapshot, so it is taken again when a report lands: the `filed` line the send
    // wrote has to be visible without closing and reopening the screen (`113229`).
    val entries = remember(upload is BugUploadState.Done) { log.recent().take(80) }
    val sending = upload is BugUploadState.Building || upload is BugUploadState.Sending
    val retryAt = (upload as? BugUploadState.Failed)?.retryAtMillis ?: 0L
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(retryAt) {
        val wait = retryAt - System.currentTimeMillis()
        if (wait > 0) {
            delay(wait)
            clock = System.currentTimeMillis()
        }
    }
    val canSend = !sending && clock >= retryAt

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = "Send this straight to the developer, with the log and your settings attached. " +
                "Keys and tokens are redacted; nothing leaves the phone until you press Send.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        OutlinedTextField(
            value = note,
            onValueChange = { text ->
                note = text
                BugDraft.note = text
            },
            label = { Text("What happened?") },
            placeholder = { Text("What you did, and what the app did instead") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        BugShot.bitmap?.let { shot ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Image(
                    bitmap = shot.asImageBitmap(),
                    contentDescription = "Screenshot of the app when the report was opened",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(96.dp)
                        .fillMaxWidth(0.45f)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Text(
                    text = "Include screenshot",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                Switch(
                    checked = includeShot,
                    onCheckedChange = {
                        includeShot = it
                        BugDraft.includeShot = it
                    },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Include logs",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "The last 48 hours, plus a redacted copy of your settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = includeLogs,
                onCheckedChange = {
                    includeLogs = it
                    BugDraft.includeLogs = it
                },
            )
        }

        // One action, one state. After the server has the report the form resets on its own;
        // writing the next note starts clean with no extra tap.
        val done = upload as? BugUploadState.Done
        if (done != null) {
            LaunchedEffect(done) {
                delay(2000)
                BugUpload.dismiss()
                // The server has it: the draft has done its job.
                note = ""
                BugDraft.note = ""
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    text = "Sent",
                    style = MaterialTheme.typography.titleSmall,
                    color = accentTextColour(),
                )
                Text(
                    text = "Filed as ${done.name}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { BugUpload.send(context, log, note, includeShot, includeLogs) },
                    enabled = canSend,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(
                        when (upload) {
                            BugUploadState.Building -> "Preparing…"
                            is BugUploadState.Sending -> "Sending…"
                            else -> "Send"
                        },
                    )
                }
                val status = when (val state = upload) {
                    is BugUploadState.Sending -> "${state.bytes / 1024} KB, straight to the developer"
                    is BugUploadState.Failed -> if (state.retryAtMillis != null) {
                        "The server asked for a pause. The button comes back on its own."
                    } else {
                        "Could not send: ${state.reason}"
                    }
                    else -> ""
                }
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (upload is BugUploadState.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        if (entries.isEmpty()) {
            Text(
                text = "No entries yet. Play a video and pull the feed, then come back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries) { entry ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            text = "${entry.time()} · ${entry.level.name} · ${entry.tag}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (entry.level) {
                                AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
                                AppLog.Level.WARN -> MaterialTheme.colorScheme.secondary
                                AppLog.Level.INFO -> MaterialTheme.colorScheme.primary
                            },
                        )
                        Text(text = entry.message, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

/**
 * The report being written, held outside the composition. A note is the one thing here that took
 * effort, so it outlives the screen (`110857`); the switches ride along because re-setting them is
 * the same kind of tax.
 */
private object BugDraft {
    var note: String = ""
    /** Null until the screen has seen whether there is a screenshot to attach. */
    var includeShot: Boolean? = null
    var includeLogs: Boolean = true
}
