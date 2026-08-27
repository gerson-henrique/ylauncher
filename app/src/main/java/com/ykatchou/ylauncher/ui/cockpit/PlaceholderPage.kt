package com.ykatchou.ylauncher.ui.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ykatchou.ylauncher.ui.theme.Y

/**
 * A page that has a place in the cockpit but no engine behind it yet. Kept deliberately empty so
 * the skeleton can ship and be swiped through before either real page exists. First consumer of
 * the [Y] design system — title and subtitle come from the tokens, not ad-hoc styles.
 */
@Composable
fun PlaceholderPage(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Y.space.xs),
        ) {
            Text(text = title, style = Y.type.subtitle, color = Y.text)
            Text(
                text = subtitle,
                style = Y.type.bodySm,
                color = Y.textDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}
