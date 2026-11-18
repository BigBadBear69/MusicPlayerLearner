package com.wavelength.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.wavelength.player.ui.theme.WavelengthColors
import kotlin.math.log10
import kotlin.math.pow

/**
 * Single-series column chart: one hue, thin columns (≤ 24dp) with rounded tops anchored to the
 * baseline, a 2dp gap between columns, hairline gridlines and a 3-tick axis. Tap a column to see its
 * exact value; the tapped column stays highlighted.
 */
@Composable
fun ColumnChart(
    values: List<Double>,
    labels: List<String>,
    formatValue: (Double) -> String,
    formatAxis: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1,
    height: androidx.compose.ui.unit.Dp = 160.dp,
) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val max = niceMinutes(values.maxOrNull() ?: 0.0)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bar = WavelengthColors.Violet
    val dim = WavelengthColors.Violet.copy(alpha = 0.45f)

    Column(modifier.semantics { contentDescription = description }) {
        // Tooltip line: shows the tapped value (or the hint).
        Text(
            selected?.let { "${labels[it]}: ${formatValue(values[it])}" } ?: "Tap a bar for details",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row {
            // Y axis ticks: 0, half, max.
            Column(Modifier.height(height).width(44.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                listOf(max, max / 2, 0.0).forEach {
                    Text(formatAxis(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(height)
                    .pointerInput(values) {
                        detectTapGestures { pos ->
                            val slot = size.width / values.size.coerceAtLeast(1)
                            val i = (pos.x / slot).toInt().coerceIn(0, values.lastIndex)
                            selected = if (selected == i) null else i
                        }
                    },
            ) {
                val stroke = 1.dp.toPx()
                // Hairline gridlines at 0, ½, max.
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = size.height * f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), stroke)
                }
                if (values.isEmpty() || max <= 0) return@Canvas
                val slot = size.width / values.size
                val gap = 2.dp.toPx()
                val w = minOf(24.dp.toPx(), slot - gap).coerceAtLeast(1f)
                val r = minOf(4.dp.toPx(), w / 2)
                values.forEachIndexed { i, v ->
                    if (v <= 0) return@forEachIndexed
                    val h = (v / max * size.height).toFloat().coerceAtLeast(r)
                    val left = i * slot + (slot - w) / 2
                    val top = size.height - h
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left, top, left + w, size.height,
                                topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, if (selected == null || selected == i) bar else dim)
                }
            }
        }
        // X labels: drawn centred under their column so they can be wider than one column.
        Canvas(Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp).height(16.dp)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            labels.forEachIndexed { i, l ->
                if (i % labelEvery != 0 || l.isEmpty()) return@forEachIndexed
                val m = measurer.measure(l, labelStyle)
                val x = (i * slot + slot / 2 - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
                drawText(m, topLeft = Offset(x, 0f))
            }
        }
    }
}

/** Axis maximum for a value in minutes: whole minutes under an hour, whole hours above. */
fun niceMinutes(v: Double): Double = if (v > 60) niceMax(v / 60).let { if (it < 2) 2.0 else it } * 60 else niceMax(v)

/** Rounds an axis maximum up to 1, 2 or 5 × 10ⁿ. */
fun niceMax(v: Double): Double {
    if (v <= 0) return 0.0
    val mag = 10.0.pow(kotlin.math.floor(log10(v)))
    val n = v / mag
    val nice = when { n <= 1 -> 1.0; n <= 2 -> 2.0; n <= 5 -> 5.0; else -> 10.0 }
    return nice * mag
}

/** A big number with a caption: used for the overview tiles. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** A thin horizontal magnitude bar for ranked lists (share of the top item). */
@Composable
fun MagnitudeBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(4.dp)
                .background(WavelengthColors.Violet, RoundedCornerShape(2.dp)),
        )
    }
}

