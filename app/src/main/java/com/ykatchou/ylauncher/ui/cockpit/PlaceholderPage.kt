package com.ykatchou.ylauncher.ui.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ykatchou.ylauncher.ui.theme.HomeTextColor
import com.ykatchou.ylauncher.ui.theme.HomeTextColorDim

/**
 * A page that has a place in the cockpit but no engine behind it yet. Kept deliberately empty so
 * the skeleton can ship and be swiped through before either real page exists.
 */
@Composable
fun PlaceholderPage(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, color = HomeTextColor)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = HomeTextColorDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}
