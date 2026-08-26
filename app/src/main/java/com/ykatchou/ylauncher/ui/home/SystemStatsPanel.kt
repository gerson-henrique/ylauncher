package com.ykatchou.ylauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ykatchou.ylauncher.data.stats.SystemStats
import com.ykatchou.ylauncher.ui.theme.HomeTextColor
import com.ykatchou.ylauncher.ui.theme.HomeTextColorDim
import kotlin.math.abs

/**
 * Sits under the running-apps column and answers one question: is it worth closing something?
 *
 * Every row is picked for that. Free memory moves when an app closes; current draw shows what the
 * open apps cost right now, where battery percentage would take hours to budge; CPU separates an
 * app that is merely open from one that is working. Anything that does not help decide — clock
 * speed, cycle count, network detail — is left to the monitor apps.
 */
@Composable
fun SystemStatsPanel(
    stats: SystemStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        stats.memUsedFraction?.let { used ->
            StatRow(
                label = "RAM",
                value = stats.memAvailableBytes?.let { "${it.toGb()} GB livres" } ?: "",
                fraction = used,
            )
        }

        stats.cpuPercent?.let { cpu ->
            StatRow(label = "CPU", value = "$cpu%", fraction = cpu / 100f)
        }

        stats.batteryPercent?.let { pct ->
            StatRow(
                label = "Bateria",
                value = buildString {
                    append("$pct%")
                    // The draw is the part that reacts to closing an app, so it earns its place
                    // next to the percentage rather than replacing it.
                    stats.currentMilliAmps?.let { ma ->
                        append(if (stats.isCharging) "  ↑" else "  ↓")
                        append("${abs(ma)} mA")
                    }
                },
                fraction = pct / 100f,
            )
        }

        stats.temperatureCelsius?.let { temp ->
            Text(
                text = "${"%.0f".format(temp)}°C",
                style = MaterialTheme.typography.labelSmall,
                color = if (temp >= HOT_CELSIUS) WarnColor else HomeTextColorDim,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, fraction: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextColorDim,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = HomeTextColor,
                fontWeight = FontWeight.Medium,
            )
        }
        // A bar as well as a number: the fill is readable at a glance from across the room,
        // which is how a home screen actually gets looked at.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(HomeTextColorDim.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (fraction >= BUSY_FRACTION) WarnColor else HomeTextColor),
            )
        }
    }
}

private fun Long.toGb(): String = "%.1f".format(this / 1_073_741_824f)

private const val HOT_CELSIUS = 42f
private const val BUSY_FRACTION = 0.85f
private val WarnColor = Color(0xFFE0873F)
