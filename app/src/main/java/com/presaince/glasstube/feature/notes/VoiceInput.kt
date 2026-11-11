/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * On-device dictation. The recogniser streams audio to whichever speech service the phone has and
 * returns text; this class never opens a file, so there is no audio to delete afterwards. That is
 * the voice-note rule: transcript only.
 *
 * Android's recogniser ends on the first pause, so a minute of speech is a *chain* of utterances.
 * It used to be one utterance per [start] and every partial replaced the note, which is report
 * `220547`: a minute of talking survived as its last second, then the session ended on
 * `ERROR_CLIENT`. Now a finished utterance is committed, the next one is started while the session
 * is live, and a partial can only ever extend what is already there — [mergeTranscript] is the
 * rule, and it has a test.
 */
class VoiceInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var onText: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onDone: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())

    /** Everything the recogniser has finished saying, oldest first. */
    private var committed = ""
    private var partial = ""

    /** True between [start] and [stop]: the microphone is ours and the UI shows recording. */
    private var live = false
    private var segments = 0

    val isAvailable: Boolean
        get() = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }
            .getOrDefault(false)

    fun start(
        onText: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit = {},
    ) {
        if (!isAvailable) {
            onError("No speech recognition on this device")
            onDone()
            return
        }
        this.onText = onText
        this.onError = onError
        this.onDone = onDone
        committed = ""
        partial = ""
        segments = 0
        live = true

        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        // The first listen can fail the same way any later one can, so it takes the same path.
        listen(engine)
    }

    /** The reader is done talking: keep what was said, stop listening, end the session once. */
    fun stop() {
        if (!live) return
        live = false
        handler.removeCallbacksAndMessages(null)
        // The words on screen are the words kept: a partial that arrived just before the tap is
        // the tail of the note, not something to throw away.
        commitPartial()
        runCatching { recognizer?.stopListening() }
        finish()
    }

    fun destroy() {
        live = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        onText = null
        onError = null
        onDone = null
    }

    private fun listen(engine: SpeechRecognizer) {
        if (!live) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { engine.startListening(intent) }
            .onFailure { fail(it.message ?: "Could not start dictation") }
    }

    /**
     * Start the next utterance. A beat between them: the service is not always ready to be told to
     * listen again from inside its own result callback.
     */
    private fun next() {
        if (!live) {
            finish()
            return
        }
        if (segments >= MAX_SEGMENTS) {
            fail("Dictation stopped after $MAX_SEGMENTS segments")
            return
        }
        val engine = recognizer ?: return finish()
        handler.postDelayed({ listen(engine) }, RESTART_MS)
    }

    private fun commitPartial() {
        if (partial.isNotBlank()) {
            committed = mergeTranscript(committed, partial)
            partial = ""
        }
    }

    private fun emit() {
        val text = mergeTranscript(committed, partial)
        if (text.isNotBlank()) onText?.invoke(text)
    }

    private fun fail(message: String) {
        live = false
        onError?.invoke(message)
        finish()
    }

    private fun finish() {
        val done = onDone
        // Clear first: a service that calls back after this must not start the note again.
        onDone = null
        done?.invoke()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val said = firstTranscript(partialResults) ?: return
            partial = said
            segments++
            emit()
        }

        override fun onResults(results: Bundle?) {
            val said = firstTranscript(results)
            if (said != null) {
                partial = said
                commitPartial()
                emit()
            } else {
                // A finished utterance with nothing in it: the tail of the note is whatever was
                // already there.
                commitPartial()
            }
            next()
        }

        override fun onError(error: Int) {
            // Silence is not an error. A timeout or a no-match means the room was quiet, and the
            // session carries on: that pause is the gap between two sentences, not the end.
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                commitPartial()
                next()
                return
            }
            // A service that dropped us is not the reader's fault either: keep the words, try the
            // next utterance, and only report once the tries are gone.
            if (error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_SERVER ||
                error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            ) {
                commitPartial()
                next()
                return
            }
            fail(describe(error))
        }
    }

    private fun firstTranscript(bundle: Bundle?): String? = bundle
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service unreachable"
        SpeechRecognizer.ERROR_NO_MATCH -> "Heard nothing"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recogniser is busy"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Heard nothing"
        else -> "Dictation failed ($error)"
    }

    private companion object {
        /** A beat between utterances: a service told to listen from its own callback can refuse. */
        const val RESTART_MS = 200L

        /** About an hour of speech in 15-second utterances, then the session ends politely. */
        const val MAX_SEGMENTS = 300
    }
}

/**
 * What the note says, given what the recogniser has finished and what it is saying now.
 *
 * The one rule: text is never lost. The recogniser restates its current utterance on every partial
 * — sometimes shorter than before — and a later utterance arrives without the earlier ones, so a
 * plain replace is how a minute of speech became its last second (`220547`). A partial that
 * repeats the committed text extends it; anything else is the next sentence.
 */
internal fun mergeTranscript(committed: String, partial: String): String {
    val head = committed.trim()
    val tail = partial.trim()
    return when {
        tail.isEmpty() -> head
        head.isEmpty() -> tail
        // The recogniser re-sent the whole utterance: the longer version wins.
        tail.startsWith(head) -> tail
        // A restatement of a piece already in the note, not a new sentence. This is the shrink
        // case: "the middle part" then "the middle" is the same words, said again.
        head.contains(tail) -> head
        else -> "$head $tail"
    }
}
