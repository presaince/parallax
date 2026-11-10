/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.posting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.glasstube.core.AppResult
import com.presaince.glasstube.data.auth.LoopbackServer
import com.presaince.glasstube.data.auth.Pkce
import com.presaince.glasstube.data.auth.PostingManager
import com.presaince.glasstube.data.auth.PostingSettings
import com.presaince.glasstube.data.auth.YouTubeAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostingUiState(
    val settings: PostingSettings = PostingSettings(),
    val clientId: String = "",
    val clientSecret: String = "",
    val text: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

class PostingViewModel(
    private val manager: PostingManager,
    private val auth: YouTubeAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(
        manager.settings().let {
            PostingUiState(settings = it, clientId = it.clientId, clientSecret = it.clientSecret)
        },
    )
    val state: StateFlow<PostingUiState> = _state.asStateFlow()

    fun onClientId(value: String) = _state.update { it.copy(clientId = value) }
    fun onClientSecret(value: String) = _state.update { it.copy(clientSecret = value) }
    fun onText(value: String) = _state.update { it.copy(text = value) }

    fun save(enabled: Boolean) {
        val current = _state.value
        manager.save(enabled, current.clientId, current.clientSecret)
        val saved = manager.settings()
        _state.update {
            it.copy(
                settings = saved,
                message = if (enabled) "Posting on" else "Posting off",
            )
        }
    }

    /**
     * Opens the consent page in the browser and waits for the loopback redirect. The browser is the
     * only place a Google login should happen: an embedded web view is both blocked by Google and
     * the wrong place to type a password.
     */
    fun signIn(openUrl: (String) -> Unit) {
        val settings = manager.settings()
        if (!settings.configured) {
            _state.update { it.copy(message = "Add a client id and secret first") }
            return
        }
        if (_state.value.busy) return

        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = "Waiting for the browser…") }
            val server = LoopbackServer()
            try {
                val verifier = Pkce.verifier()
                openUrl(
                    auth.authorizeUrl(
                        clientId = settings.clientId,
                        redirectUri = server.redirectUri,
                        verifier = verifier,
                        state = Pkce.verifier(),
                    ),
                )

                val code = when (val result = server.awaitCode()) {
                    is AppResult.Ok -> result.value
                    is AppResult.Err -> {
                        _state.update { it.copy(busy = false, message = result.message) }
                        return@launch
                    }
                }

                when (
                    val tokens = auth.exchangeCode(
                        clientId = settings.clientId,
                        clientSecret = settings.clientSecret,
                        code = code,
                        verifier = verifier,
                        redirectUri = server.redirectUri,
                    )
                ) {
                    is AppResult.Ok -> {
                        manager.storeTokens(tokens.value)
                        // Signing in is the enable step: a separate switch that can be on while
                        // there is no account is a state that means nothing.
                        manager.save(true, settings.clientId, settings.clientSecret)
                        val saved = manager.settings()
                        _state.update {
                            it.copy(busy = false, settings = saved, message = "Signed in, posting on")
                        }
                    }
                    is AppResult.Err -> _state.update {
                        it.copy(busy = false, message = tokens.message)
                    }
                }
            } finally {
                server.close()
            }
        }
    }

    fun signOut() {
        manager.signOut()
        val settings = manager.settings()
        manager.save(false, settings.clientId, settings.clientSecret)
        val saved = manager.settings()
        _state.update { it.copy(settings = saved, message = "Signed out, posting off") }
    }

    fun post(videoId: String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            when (val result = manager.post(videoId, _state.value.text.trim())) {
                is AppResult.Ok -> _state.update {
                    it.copy(busy = false, text = "", message = "Comment posted")
                }
                is AppResult.Err -> _state.update {
                    it.copy(busy = false, message = result.message)
                }
            }
        }
    }
}
