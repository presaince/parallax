/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.glasstube.data.db.NoteEntity
import com.presaince.glasstube.data.db.GlassTubeDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A note being written. It only becomes a row when [DeckViewModel.save] is called. */
data class PendingNote(val atSec: Long, val text: String = "", val id: Long? = null)

data class DeckUiState(
    val notes: List<NoteEntity> = emptyList(),
    val editing: PendingNote? = null,
    val message: String? = null,
)

class DeckViewModel(
    private val db: GlassTubeDb,
    val videoId: String,
) : ViewModel() {

    private val status = MutableStateFlow(DeckUiState())

    /** The note text as it was when the microphone started. */
    private var dictationBase: String = ""

    val state: StateFlow<DeckUiState> = combine(
        db.notes().observeFor(videoId, KIND),
        status,
    ) { notes, current ->
        current.copy(notes = notes)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeckUiState(),
    )

    fun begin(atSec: Long) {
        status.update { it.copy(editing = PendingNote(atSec = atSec.coerceAtLeast(0)), message = null) }
    }

    /** Tapping a saved note's text loads it back into the field, at its own timestamp. */
    fun beginEdit(note: NoteEntity) {
        status.update {
            it.copy(
                editing = PendingNote(atSec = note.atSec, text = note.text, id = note.id),
                message = null,
            )
        }
    }

    fun cancelEditing() {
        status.update { it.copy(editing = null) }
    }

    fun updateDraft(text: String) {
        status.update { current ->
            current.copy(editing = current.editing?.copy(text = text))
        }
    }

    /** Partial and final results both restate the sentence, so the draft is rebuilt, not appended. */
    fun dictationText(text: String) {
        status.update { current ->
            val draft = current.editing ?: return@update current
            val joined = if (dictationBase.isBlank()) text else "${dictationBase.trim()} $text"
            current.copy(editing = draft.copy(text = joined))
        }
    }

    /** Called once when the microphone starts, so a second utterance continues the same note. */
    fun startDictation() {
        dictationBase = status.value.editing?.text.orEmpty()
    }

    fun cancel() {
        status.update { it.copy(editing = null) }
    }

    fun save() {
        val draft = status.value.editing ?: return
        val text = draft.text.trim()
        if (text.isEmpty()) {
            status.update { it.copy(message = "A note needs words in it") }
            return
        }
        viewModelScope.launch {
            val id = draft.id
            if (id != null) {
                db.notes().updateText(id, text)
            } else {
                db.notes().insert(
                    NoteEntity(
                        videoId = videoId,
                        atSec = draft.atSec,
                        text = text,
                        createdAt = System.currentTimeMillis(),
                        kind = KIND,
                    ),
                )
            }
            status.update { it.copy(editing = null, message = null) }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { db.notes().delete(id) }
    }

    /** Puts back the note the undo strip is still offering. The same row, not a new one. */
    fun restore(note: NoteEntity) {
        viewModelScope.launch { db.notes().insert(note) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            db.notes().deleteAll(videoId, KIND)
            status.update { it.copy(message = null) }
        }
    }

    /** A line of feedback that is not an error, such as a completed copy. */
    fun flash(message: String) {
        status.update { it.copy(message = message) }
    }

    fun dismissMessage() {
        status.update { it.copy(message = null) }
    }

    /** Dictation problems belong in the message line, never in the note body. */
    fun voiceError(message: String) {
        status.update { it.copy(message = message) }
    }

    private companion object {
        const val KIND = "deck"
    }
}
