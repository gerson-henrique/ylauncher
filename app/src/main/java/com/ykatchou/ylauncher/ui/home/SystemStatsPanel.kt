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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ykatchou.ylauncher.R
import com.ykatchou.ylauncher.data.stats.SystemStats
import com.ykatchou.ylauncher.ui.theme.HomeAccent
import com.ykatchou.ylauncher.ui.theme.HomeTextColor
import com.ykatchou.ylauncher.ui.theme.HomeTextColorDim
import com.ykatchou.ylauncher.ui.theme.HomeWarn
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
                label = stringResource(R.string.stat_ram),
                value = stats.memAvailableBytes?.let { stringResource(R.string.stat_ram_free, it.toGb()) } ?: "",
                fraction = used,
            )
        }

        stats.cpuPercent?.let { cpu ->
            StatRow(label = stringResource(R.string.stat_cpu), value = "$cpu%", fraction = cpu / 100f)
        }

        stats.batteryPercent?.let { pct ->
            StatRow(
                label = stringResource(R.string.stat_battery),
                value = stringResource(R.string.stat_percent, pct),
                fraction = pct / 100f,
                // Battery reads the opposite way to the others: a full bar is good here, an
                // empty one is the problem. Without this a healthy 90% would glow amber.
                warnWhenLow = true,
            )
        }

        // Draw carries no bar on purpose. Milliamps have no ceiling to measure against — any
        // full-bar reference would be a number invented here, and a bar filled against an
        // invented scale reads as a measurement while meaning nothing. The figure itself is
        // real, so it stands alone next to the temperature.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            stats.currentMilliAmps?.let { ma ->
                Text(
                    text = stringResource(
                        if (stats.isCharging) R.string.stat_charging_ma else R.string.stat_draw_ma,
                        abs(ma),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeTextColor,
                    fontWeight = FontWeight.Medium,
                )
            }
            stats.temperatureCelsius?.let { temp ->
                Text(
                    text = stringResource(R.string.stat_celsius, "%.0f".format(temp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (temp >= HOT_CELSIUS) HomeWarn else HomeTextColorDim,
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    fraction: Float,
    /** Warn on an empty bar rather than a full one, for meters where more is better. */
    warnWhenLow: Boolean = false,
) {
    val warn = if (warnWhenLow) fraction <= LOW_FRACTION else fraction >= BUSY_FRACTION
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
                    .background(if (warn) HomeWarn else HomeAccent),
            )
        }
    }
}

private fun Long.toGb(): String = "%.1f".format(this / 1_073_741_824f)

private const val HOT_CELSIUS = 42f
private const val BUSY_FRACTION = 0.85f
private const val LOW_FRACTION = 0.15f

