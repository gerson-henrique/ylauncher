package com.ykatchou.ylauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ykatchou.ylauncher.data.weather.Weather
import com.ykatchou.ylauncher.data.weather.WeatherRepository
import com.ykatchou.ylauncher.ui.theme.HomeTextColor
import kotlinx.coroutines.delay

/**
 * Current conditions, opposite the clock.
 *
 * Deliberately two glyphs and a number. This replaces a stock weather app measured at ~740 MB —
 * most of it graphics buffers for animated scenery — and the whole point is that the information
 * was never worth that. Nothing renders until there is a real reading: no skeleton, no dash, no
 * placeholder temperature that could be mistaken for a measurement.
 */
@Composable
fun WeatherWidget(
    repository: WeatherRepository,
    modifier: Modifier = Modifier,
) {
    var weather by remember { mutableStateOf<Weather?>(repository.cachedOrNull()) }

    LaunchedEffect(Unit) {
        while (true) {
            repository.fetch()?.let { weather = it }
            delay(REFRESH_MS)
        }
    }

    val current = weather ?: return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = current.icon, fontSize = 22.sp)
        Text(
            text = "${current.temperatureCelsius}°",
            style = MaterialTheme.typography.headlineSmall,
            color = HomeTextColor,
            fontWeight = FontWeight.Light,
        )
    }
}

/** The repository caches for 15 minutes, so this only actually hits the network four times an hour. */
private const val REFRESH_MS = 5L * 60 * 1000
