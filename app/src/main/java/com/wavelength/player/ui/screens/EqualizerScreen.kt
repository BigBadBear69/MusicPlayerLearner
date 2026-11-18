@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wavelength.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wavelength.player.data.audio.EqInfo
import com.wavelength.player.data.audio.EqPresets
import com.wavelength.player.data.audio.EqSettings
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    info: EqInfo?,
    settings: EqSettings,
    supported: Boolean,
    onEnabled: (Boolean) -> Unit,
    onPreset: (String) -> Unit,
    onBand: (Int, Float) -> Unit,
    onBass: (Float) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Equalizer", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = { Switch(settings.enabled, onCheckedChange = onEnabled, modifier = Modifier.padding(end = 12.dp), enabled = supported) },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            when {
                !supported -> Text("This phone doesn't allow apps to use its equalizer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                info == null -> Text("Play a song once, then come back here: the equalizer needs to see this phone's audio first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Text("Presets", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (EqPresets.names + if (settings.preset == EqPresets.CUSTOM) listOf(EqPresets.CUSTOM) else emptyList()).forEach { name ->
                            FilterChip(selected = settings.enabled && settings.preset == name, onClick = { onPreset(name) }, label = { Text(name) })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bands", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onPreset(EqPresets.FLAT) }) { Text("Reset") }
                    }
                    info.bandCentersHz.forEachIndexed { i, hz ->
                        val db = settings.bandsDb.getOrNull(i) ?: 0f
                        BandSlider(
                            label = if (hz >= 1000) "${"%.1f".format(hz / 1000f).removeSuffix(".0")} kHz" else "$hz Hz",
                            value = db, min = info.minDb, max = info.maxDb,
                            enabled = settings.enabled,
                            onChange = { onBand(i, it) },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    var bass by remember(settings.bassBoost) { mutableFloatStateOf(settings.bassBoost) }
                    Text("Bass boost: ${(bass * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(bass, onValueChange = { bass = it }, onValueChangeFinished = { onBass(bass) }, enabled = settings.enabled)
                    Text(
                        "Tip: big boosts can distort on small speakers. Small changes of 2 to 4 dB usually sound best.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BandSlider(label: String, value: Float, min: Float, max: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
        Slider(
            v, onValueChange = { v = (it * 2).roundToInt() / 2f }, onValueChangeFinished = { onChange(v) },
            valueRange = min..max, enabled = enabled, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary),
        )
        Text("${if (v > 0) "+" else ""}${"%.1f".format(v)} dB", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
    }
}
