package com.ykatchou.ylauncher.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ykatchou.ylauncher.R
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val body: String,
    val drawableRes: Int? = null,
    val emoji: String? = null,
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        title = "Your home, your way",
        body = "Add your favorite apps and swipe sideways to switch between panels.",
        drawableRes = R.drawable.onboarding_home,
    ),
    OnboardingPage(
        title = "Edit your favorites",
        body = "Long-press the home screen to add, remove, reorder favorites, or create folders.",
        drawableRes = R.drawable.onboarding_menu,
    ),
    OnboardingPage(
        title = "Settings, your way",
        body = "Customize the magic button, gestures, and more from Settings.",
        drawableRes = R.drawable.onboarding_hal,
    ),
    OnboardingPage(
        title = "Free, private, and yours",
        body = "100% free, no ads, ever. YLauncher doesn't collect or share any of your data — everything stays on your phone.",
        emoji = "🔒",
    ),
)

@Composable
fun OnboardingTourOverlay(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES.size })
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) { onFinish() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onFinish) { Text("Skip") }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val onboardingPage = ONBOARDING_PAGES[page]
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (onboardingPage.drawableRes != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            tonalElevation = 6.dp,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Image(
                                painter = painterResource(onboardingPage.drawableRes),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                            )
                        }
                    } else if (onboardingPage.emoji != null) {
                        Box(
                            modifier = Modifier.size(96.dp).weight(1f, fill = false),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(onboardingPage.emoji, style = MaterialTheme.typography.displayLarge)
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = onboardingPage.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = onboardingPage.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(ONBOARDING_PAGES.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .then(
                                Modifier.background(
                                    color = if (index == pagerState.currentPage) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    },
                                ),
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) { Text("Back") }
                } else {
                    Spacer(Modifier)
                }

                val isLastPage = pagerState.currentPage == ONBOARDING_PAGES.size - 1
                Button(onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }) {
                    Text(if (isLastPage) "Get started" else "Next")
                }
            }
        }
    }
}
