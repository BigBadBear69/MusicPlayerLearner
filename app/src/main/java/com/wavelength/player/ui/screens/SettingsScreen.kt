@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavelength.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wavelength.player.data.discover.InvalidApiKeyException
import com.wavelength.player.data.settings.AppSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSaveKey: (String) -> Unit,
    onTestKey: suspend (String) -> Result<Unit>,
    onDiscoverOnline: (Boolean) -> Unit,
    onClearDiscoverCache: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember(settings.lastFmApiKey) { mutableStateOf(settings.lastFmApiKey) }
    var show by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Discover", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Discover uses the internet")
                    Text(
                        "Sends only song and artist names to Last.fm and Deezer to find similar music. Nothing else leaves your phone.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = settings.discoverOnline, onCheckedChange = onDiscoverOnline)
            }

            Spacer(Modifier.height(20.dp))
            Text("Last.fm API key", style = MaterialTheme.typography.titleSmall)
            Text(
                "Optional but recommended: it gives Discover song-by-song matches instead of artist-level ones. It's free.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(12.dp),
            ) {
                Text("How to get one (about 2 minutes):", style = MaterialTheme.typography.labelLarge)
                Text(
                    "1. Tap \"Open Last.fm\" below and log in, or create a free account.\n" +
                        "2. Fill in the form: Application name \"Wavelength\", anything for the description. Leave Callback URL and Homepage empty.\n" +
                        "3. Tap Submit, copy the \"API key\" it shows (32 letters and numbers; you don't need the \"Shared secret\").\n" +
                        "4. Paste it below and tap Save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm/api/account/create")))
                }) { Text("Open Last.fm") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim(); testResult = null },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { show = !show }) {
                        Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide key" else "Show key")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val r = onTestKey(key)
                            testing = false
                            testResult = if (r.isSuccess) {
                                onSaveKey(key)
                                true to "Key works and is saved."
                            } else {
                                false to if (r.exceptionOrNull() is InvalidApiKeyException) "Last.fm says this key isn't valid. Check you copied the API key, not the shared secret."
                                else "Couldn't reach Last.fm: ${r.exceptionOrNull()?.message ?: "no connection"}"
                            }
                        }
                    },
                    enabled = key.isNotBlank() && !testing,
                ) { Text("Test & save") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onSaveKey(key); testResult = true to if (key.isBlank()) "Key removed." else "Saved." }) { Text("Save") }
                if (testing) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                }
            }
            testResult?.let { (ok, msg) ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
                        tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onClearDiscoverCache) { Text("Clear Discover cache") }
            Text(
                "Discover saves music-service answers for a week to stay fast. Clear it to fetch everything fresh.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            Text("Library", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRescan) { Text("Rescan music now") }
            Text(
                "Wavelength rescans automatically when files change; use this if something's missing.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
