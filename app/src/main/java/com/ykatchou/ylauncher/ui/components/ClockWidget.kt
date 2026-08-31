package com.ykatchou.ylauncher.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ykatchou.ylauncher.ui.theme.ProverbBrush
import com.ykatchou.ylauncher.ui.theme.Y
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The clock, "Tinta" style: crowned by the crane seal (鶴), the time in a thin ink stroke, the date
 * quieter below — all dark ink over the light crane painting. Right-aligned, it mirrors the proverb
 * scroll on the opposite corner.
 */
@Composable
fun ClockWidget(
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val date = remember(currentTime / 60_000) { Date(currentTime) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        // The crane seal — the same stamp language as the proverb's author and Cricket's 蛩.
        Box(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .rotate(-3f)
                .size(22.dp)
                .border(1.5.dp, Y.seal, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "鶴", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ProverbBrush), color = Y.seal)
        }
        Text(
            text = timeFormat.format(date),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light),
            color = Y.inkStrong,
            modifier = Modifier.clickable { onClockClick() },
        )
        Text(
            text = dateFormat.format(date),
            style = MaterialTheme.typography.labelLarge,
            color = Y.inkDim,
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable { onDateClick() },
        )
    }
}
