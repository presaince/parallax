/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * On-device dictation. The recogniser streams audio to whichever speech service the phone has and
 * returns text; this class never opens a file, so there is no audio to delete afterwards. That is
 * the voice-note rule: transcript only.
 *
 * One utterance per [start]. Android's recogniser ends on the first pause, and chaining utterances
 * by restarting on results would keep the microphone open with no visible session.
 */
class VoiceInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var onText: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onDone: (() -> Unit)? = null

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

        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { engine.startListening(intent) }
            .onFailure { onError(it.message ?: "Could not start dictation") }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        onText = null
        onError = null
        onDone = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            firstTranscript(partialResults)?.let { onText?.invoke(it) }
        }

        override fun onResults(results: Bundle?) {
            firstTranscript(results)?.let { onText?.invoke(it) }
            onDone?.invoke()
        }

        override fun onError(error: Int) {
            // Silence is not an error. A timeout or a no-match means the room was quiet, not that
            // anything failed, and the card should not keep a note of it.
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                onDone?.invoke()
                return
            }
            onError?.invoke(describe(error))
            onDone?.invoke()
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
}
