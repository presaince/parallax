/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.notes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.presaince.parallax.R
import com.presaince.parallax.core.AppLog
import com.presaince.parallax.core.formatDuration
import com.presaince.parallax.data.db.NoteEntity
import com.presaince.parallax.data.db.ParallaxDb
import com.presaince.parallax.playback.PlaybackController
import kotlin.math.sin

/**
 * The deck as one card above the comments. The microphone is the only record control: hold it and
 * speak, and playback pauses until the words arrive. There is no second "write" mode — the text
 * field is always there, and one tap starts typing.
 */
@Composable
fun NotesTab(
    db: ParallaxDb,
    playback: PlaybackController,
    videoId: String,
    log: AppLog,
    dictateSignal: Int = 0,
    onCount: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: DeckViewModel = viewModel(
        key = "deck-$videoId",
        factory = viewModelFactory {
            initializer { DeckViewModel(db, videoId) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playbackState by playback.state.collectAsStateWithLifecycle()

    // The player's header is outside this card, so the count is reported up to it.
    LaunchedEffect(state.notes.size) { onCount(state.notes.size) }
    var resumeAfterDictation by remember { mutableStateOf(false) }
    var dictating by remember { mutableStateOf(false) }

    val voice = remember { VoiceInput(context) }
    DisposableEffect(voice) {
        onDispose { voice.destroy() }
    }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    // A deleted note is kept just long enough to take it back. The row is gone at once, so the
    // delete never needs a dialog in front of it.
    var deleted by remember { mutableStateOf<NoteEntity?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(deleted) {
        if (deleted != null) {
            delay(5000)
            deleted = null
        }
    }

    /** Leaving the editor: a draft with words in it is worth one question before it is lost. */
    fun leaveEditor() {
        val draft = state.editing ?: return
        if (draft.text.isNotBlank()) {
            confirmCancel = true
        } else {
            viewModel.cancelEditing()
            focus.clearFocus()
        }
    }

    fun startDictation() {
        viewModel.startDictation()
        // Pause first, and hold the microphone for a beat: the player's audio pipeline needs a
        // moment to actually stop, and a recogniser opened in the same frame records the video.
        resumeAfterDictation = playback.pauseForDictation()
        dictating = true
        scope.launch {
            delay(220)
            voice.start(
                onText = viewModel::dictationText,
                onError = { message ->
                    log.warn("notes", "dictation: $message")
                    viewModel.voiceError(message)
                    dictating = false
                    playback.resumeAfterDictation(resumeAfterDictation)
                    resumeAfterDictation = false
                },
                onDone = {
                    dictating = false
                    playback.resumeAfterDictation(resumeAfterDictation)
                    resumeAfterDictation = false
                },
            )
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startDictation()
        } else {
            viewModel.voiceError("Microphone permission is off")
        }
    }

    fun dictate() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startDictation() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(dictateSignal) {
        if (dictateSignal > 0) {
            if (state.editing == null) viewModel.begin(playbackState.positionMs / 1000)
            dictate()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val editing = state.editing
            // One top row: the list tools as a bordered group on the left, then the state, the
            // microphone and (only with a draft) the save tick.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editing == null && state.notes.size >= 2) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                val text = state.notes.joinToString("\n") {
                                    "[${formatDuration(it.atSec)}] ${it.text}"
                                }
                                clipboard.setText(AnnotatedString(text))
                                viewModel.flash("Copied ${state.notes.size} notes")
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_copy),
                                contentDescription = "Copy all notes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = { confirmDeleteAll = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = "Delete all notes",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = "All",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, end = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                val caption = state.message ?: editing?.let { formatDuration(it.atSec) }
                if (caption != null) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.message != null) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                RecordingMic(
                    recording = dictating,
                    onClick = {
                        if (state.editing == null) {
                            viewModel.begin(playbackState.positionMs / 1000)
                        }
                        dictate()
                    },
                )
                if (editing != null && !dictating) {
                    IconButton(onClick = { leaveEditor() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Cancel this note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.save()
                            focus.clearFocus()
                            // Dictation paused the video to listen; saving is the end of that,
                            // so playback comes back if it was running before.
                            if (resumeAfterDictation) {
                                playback.resumeAfterDictation(true)
                                resumeAfterDictation = false
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = "Save note",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // The field is always here: typing is the other way in, and it starts a note at the
            // playhead on the first keystroke. The microphone fills the same field.
            var fieldFocused by remember { mutableStateOf(false) }
            LaunchedEffect(editing) {
                // A saved note must not leave the cursor blinking in a field the keyboard has
                // already left. Focus belongs to whoever is typing, not to the last draft.
                if (editing == null) {
                    fieldFocused = false
                    focus.clearFocus()
                }
            }
            // The river runs inside the box, behind the text. It is the only dictation animation,
            // and it dims as soon as the field is focused so typing is clean.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                NoteRiver(
                    recording = dictating,
                    dimmed = fieldFocused,
                    modifier = Modifier.matchParentSize(),
                )
                OutlinedTextField(
                    value = editing?.text.orEmpty(),
                    onValueChange = { text ->
                        if (state.editing == null) {
                            viewModel.begin(playbackState.positionMs / 1000)
                        }
                        viewModel.updateDraft(text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            fieldFocused = it.isFocused
                            if (it.isFocused && dictating) {
                                // Typing ends the microphone: two inputs into one field at the
                                // same time is how a note ends up half-spoken, half-typed.
                                voice.stop()
                                dictating = false
                                playback.resumeAfterDictation(resumeAfterDictation)
                                resumeAfterDictation = false
                            }
                        },
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                    placeholder = {
                        Text(
                            text = "talk or type what you're thinking",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )
            }

            if (deleted != null) {
                // The row is gone before this shows: the strip is the way back, not a warning.
                deleted?.let { gone ->
                    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Note deleted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                viewModel.restore(gone)
                                deleted = null
                            }) {
                                Text("Undo")
                            }
                        }
                    }
                }
            }

            if (state.notes.isNotEmpty()) {
                // A plain column, not a lazy list: this card lives inside the player's own
                // LazyColumn, and a second scroll container inside an item has no bounded height.
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Newest first, and each new row slides in instead of appearing.
                    state.notes.sortedByDescending { it.createdAt }.forEach { note ->
                        key(note.id) {
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(initialOffsetY = { -it / 3 }) + fadeIn(),
                            ) {
                                Column {
                                    NoteRow(
                                        note = note,
                                        onSeek = { playback.seekTo(note.atSec * 1000) },
                                        onCopy = {
                                            clipboard.setText(
                                                AnnotatedString(
                                                    "[${formatDuration(note.atSec)}] ${note.text}",
                                                ),
                                            )
                                            viewModel.flash("Copied the note")
                                        },
                                        onEdit = { viewModel.beginEdit(note) },
                                        onDelete = {
                                            deleted = note
                                            viewModel.delete(note.id)
                                        },
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Throw this draft away?") },
            text = { Text("The words in the field are not saved yet.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelEditing()
                    focus.clearFocus()
                    if (resumeAfterDictation) {
                        playback.resumeAfterDictation(true)
                        resumeAfterDictation = false
                    }
                    confirmCancel = false
                }) {
                    Text("Throw away")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep writing") }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete every note?") },
            text = {
                Text(
                    "All ${state.notes.size} notes for this video. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    confirmDeleteAll = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Keep") }
            },
        )
    }
}

/** A microphone in a circle. The waves live in the text box now, so this stays a plain button. */
@Composable
private fun RecordingMic(recording: Boolean, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = if (recording) "Listening" else "Record a note",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The river: three sine layers crossing the note field at different speeds, in the accent colour.
 * Louder while recording, near-silent while the field has focus, and clipped to the field's shape
 * so it reads as liquid inside the box rather than decoration behind it.
 */
@Composable
private fun NoteRiver(recording: Boolean, dimmed: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "river")
    val movingPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    // Still water when nothing is being recorded: the river flows because the microphone is open.
    val phase = if (recording) movingPhase else 0f
    val energy by animateFloatAsState(
        targetValue = if (recording) 1f else 0.55f,
        animationSpec = tween(500),
        label = "energy",
    )
    val alpha by animateFloatAsState(
        targetValue = if (dimmed) 0.09f else 0.22f,
        animationSpec = tween(250),
        label = "river-alpha",
    )
    val accent = MaterialTheme.colorScheme.primary
    // Three neighbouring hues, ±14°, so the water reads as one colour moving rather than three
    // unrelated lines. Small enough to stay on-brand beside the accent controls.
    val hues = listOf(accent.shiftHue(-14f), accent, accent.shiftHue(14f))

    Canvas(modifier = modifier.clip(RoundedCornerShape(6.dp))) {
        val mid = size.height / 2f
        repeat(3) { layer ->
            val path = Path()
            val amplitude = size.height * (0.13f + 0.05f * layer) * energy
            val frequency = (2.0 * Math.PI / size.width.coerceAtLeast(1f)).toFloat() *
                (1.2f + layer * 0.5f)
            val shift = phase + layer * 1.7f
            var x = 0f
            while (x <= size.width) {
                val y = mid + (layer - 1) * size.height * 0.18f +
                    sin(x * frequency + shift) * amplitude
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += 3f
            }
            drawPath(
                path = path,
                color = hues[layer].copy(alpha = alpha * (1f - layer * 0.18f)),
                style = Stroke(width = 2.0f + layer * 0.9f),
            )
        }
    }
}

/** The same colour, a few degrees around the wheel. Used only to spread the river's layers. */
private fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun NoteRow(
    note: NoteEntity,
    onSeek: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // The text owns the full width; tapping it is the edit.
        Text(
            text = note.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(vertical = 4.dp),
        )
        // Time and the two actions share one row at the bottom right, under the text.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(note.atSec),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onSeek)
                    .padding(end = 6.dp),
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = "Copy note",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "Delete note",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
