package com.ykatchou.ylauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private enum class ReviewStage { RATE, THANKS, FEEDBACK }

private const val HIGH_RATING_THRESHOLD = 4

@Composable
fun ReviewPromptDialog(
    onRateHigh: () -> Unit,
    onSendFeedback: (String) -> Unit,
    onSnooze: () -> Unit,
) {
    var stage by remember { mutableStateOf(ReviewStage.RATE) }
    var selectedStars by remember { mutableStateOf(0) }
    var feedbackText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onSnooze) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                when (stage) {
                    ReviewStage.RATE -> {
                        Text(
                            text = "Enjoying YLauncher?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap a star to let us know how it's going.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            for (star in 1..5) {
                                Text(
                                    text = if (star <= selectedStars) "★" else "☆",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clickable {
                                            selectedStars = star
                                            stage = if (star >= HIGH_RATING_THRESHOLD) {
                                                ReviewStage.THANKS
                                            } else {
                                                ReviewStage.FEEDBACK
                                            }
                                        },
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onSnooze) { Text("Later") }
                        }
                    }

                    ReviewStage.THANKS -> {
                        Text(
                            text = "Thanks for the love!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Mind rating us on the Play Store? It really helps.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onSnooze) { Text("Not now") }
                            Button(onClick = onRateHigh) { Text("Rate on Play Store") }
                        }
                    }

                    ReviewStage.FEEDBACK -> {
                        Text(
                            text = "Sorry to hear that",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Want to tell us what could be better? Your insights go straight to the product team.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            placeholder = { Text("What could we improve?") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onSnooze) { Text("Skip") }
                            Button(
                                onClick = { onSendFeedback(feedbackText) },
                                enabled = feedbackText.isNotBlank(),
                            ) { Text("Send feedback") }
                        }
                    }
                }
            }
        }
    }
}
