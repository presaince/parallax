/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.presaince.parallax.BuildConfig
import com.presaince.parallax.data.update.UPDATE_BASE_URL
import com.presaince.parallax.data.update.UpdateState
import com.presaince.parallax.ui.theme.oklch
import com.presaince.parallax.ui.theme.accentTextColour

/** The Presaince ramp, dark, as defined in the portfolio README. Only the wordmark wears it. */
private val BrandRampDark = listOf(
    Color(0xFFF8FAFC),
    Color(0xFFA5E4F5),
    Color(0xFF4CC2E8),
)

/** The light ramp from the same README, in oklch, because light-on-light is invisible. */
private val BrandRampLight = listOf(
    oklch(0.25, 0.06, 193.0),
    oklch(0.52, 0.10, 193.0),
    oklch(0.38, 0.12, 193.0),
)

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val viewModel: UpdateViewModel = viewModel(
        factory = viewModelFactory {
            initializer { UpdateViewModel(context) }
        },
    )
    val updateState by viewModel.state.collectAsStateWithLifecycle()
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brandRamp = if (darkTheme) BrandRampDark else BrandRampLight

    // The installer permission lives on a system screen, so the app can only send the person there
    // and pick the thread up on the way back. Without this the Install button had nowhere to go and
    // simply closed the dialog.
    var canInstall by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    var waitingForPermission by remember { mutableStateOf(false) }

    fun launchInstall() {
        val intent = viewModel.installIntent()
        if (intent == null) {
            waitingForPermission = true
            viewModel.openInstallPermissionSettings()
            return
        }
        waitingForPermission = false
        runCatching { context.startActivity(intent) }
    }

    // Downloading is the consent. Android still shows its own confirmation, and that one is the
    // decision; a second question from us had the same answer by construction. One automatic
    // attempt only, so a cancelled install does not re-open the sheet on every resume.
    var installLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(updateState, canInstall) {
        val ready = updateState is UpdateState.Downloaded
        if (!ready) installLaunched = false
        if (ready && canInstall && !waitingForPermission && !installLaunched) {
            installLaunched = true
            launchInstall()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = context.packageManager.canRequestPackageInstalls()
        canInstall = granted
        if (granted && waitingForPermission) launchInstall()
    }

    // The manifest check runs once when the page is opened; the button runs it again on demand.
    LaunchedEffect(Unit) {
        viewModel.check()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Parallax",
            style = MaterialTheme.typography.headlineMedium.copy(
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.onBackground,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.primary,
                    ),
                ),
            ),
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "A local-first YouTube client. No account, no server, no analytics.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val isChecking = updateState is UpdateState.Checking
        // One line: the version, and its status appended to the same text. A second line under it
        // said the same thing the line above could hold.
        val status = when (val current = updateState) {
            is UpdateState.UpToDate -> "You have the latest version"
            is UpdateState.Failed -> "Update check failed"
            is UpdateState.Available -> "${current.info.versionName} is available"
            is UpdateState.Downloading -> "Downloading"
            is UpdateState.Downloaded -> "Ready to install"
            UpdateState.Checking -> "Checking"
            else -> null
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append("Version ${BuildConfig.VERSION_NAME}")
                    if (status != null) append(" · ").append(status)
                    if (isChecking) append("…")
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (updateState) {
                    is UpdateState.Failed -> MaterialTheme.colorScheme.error
                    is UpdateState.Available -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            // The button sits on the right edge of the version line, not on its own row below it.
            // Once the archive is downloaded it is the install control: the automatic attempt can
            // be cancelled at the system sheet, and then this is the way back to it.
            Button(
                onClick = {
                    if (updateState is UpdateState.Downloaded) launchInstall() else viewModel.check()
                },
                enabled = !isChecking,
            ) {
                Text(
                    when {
                        isChecking -> "Checking…"
                        updateState is UpdateState.Downloaded -> "Install"
                        else -> "Check for updates"
                    },
                )
            }
        }
        // The download page sits beside the APK and lists the published build. The check button
        // above installs in place; this link is for a browser, a second device, or a share.
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_BASE_URL))
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.padding(top = 4.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Text("Visit product page")
        }
        UpdateDialog(
            state = updateState,
            needsInstallPermission = !canInstall,
            onDownload = { viewModel.download() },
            onRetry = { viewModel.check() },
            onLater = { viewModel.dismiss() },
            onOpenSettings = {
                waitingForPermission = true
                viewModel.openInstallPermissionSettings()
            },
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )

        Text(
            text = "Privacy",
            style = MaterialTheme.typography.titleSmall,
            color = accentTextColour(),
        )
        Text(
            text = "Subscriptions, history, progress and notes stay in the app's own database on " +
                "this phone. Uninstalling deletes them. Nothing is sent anywhere except YouTube " +
                "for the videos themselves, SponsorBlock for segments, and the model endpoint you " +
                "choose if you turn Timestamp Resync on.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            text = "Licence",
            style = MaterialTheme.typography.titleSmall,
            color = accentTextColour(),
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Parallax is free software under the GPLv3. It uses NewPipeExtractor, also " +
                "GPLv3, as a dependency and vendors none of its code. Stream URLs are never " +
                "stored: they expire, and they belong to nobody.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            text = "Source and licence text: see the project repository.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )

        // The maker's mark closes the page, centred, where a signature belongs.
        Text(
            text = "Made by Presaince",
            style = MaterialTheme.typography.headlineSmall.copy(
                brush = Brush.linearGradient(brandRamp),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 24.dp)
                .clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://odesaplay.com.ua/other_apps/portfolio/"),
                    )
                    runCatching { context.startActivity(intent) }
                },
        )
    }
}
