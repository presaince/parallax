/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.posting

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.presaince.parallax.data.auth.PostingManager
import com.presaince.parallax.data.auth.YouTubeAuth

/**
 * Optional posting. It is off by default and needs a Google OAuth client of the user's own, which
 * is why this screen reads like a setup page: pretending a login button would work without one is
 * the sort of lie that wastes an afternoon.
 */
@Composable
fun PostingScreen(
    manager: PostingManager,
    auth: YouTubeAuth,
) {
    val context = LocalContext.current
    val viewModel: PostingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PostingViewModel(manager, auth) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text(
            text = if (!state.settings.configured) {
                "Posting is off. Add your own OAuth client to turn it on."
            } else if (state.settings.signedIn) {
                "Posting is on for this account."
            } else {
                "Client saved. Sign in to turn posting on."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = "Create an OAuth client of type \"Desktop app\" in Google Cloud Console, enable " +
                "the YouTube Data API v3, and paste its id and secret here. The redirect is " +
                "http://127.0.0.1:<port>/, which the client type allows.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.clientId,
            onValueChange = viewModel::onClientId,
            label = { Text("Client id") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.clientSecret,
            onValueChange = viewModel::onClientSecret,
            label = { Text("Client secret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    viewModel.signIn { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                },
                enabled = !state.busy,
            ) {
                Text(if (state.busy) "Waiting…" else "Sign in")
            }
            if (state.settings.signedIn) {
                TextButton(onClick = viewModel::signOut) { Text("Sign out") }
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }

        if (state.settings.signedIn && state.settings.enabled) {
            Text(
                text = "Signed in. Comment boxes appear under any video. Sentences you write are " +
                    "sent to YouTube with your account, and nowhere else.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
