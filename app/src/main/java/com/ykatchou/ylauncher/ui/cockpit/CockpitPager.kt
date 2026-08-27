package com.ykatchou.ylauncher.ui.cockpit

import android.content.ComponentName
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.ykatchou.ylauncher.data.repository.AppRepository
import com.ykatchou.ylauncher.ui.home.HomeScreen

/**
 * The three-page cockpit. The launcher is the control surface, not the engine: this is only the
 * navigation shell. The home is the centre and the start page — everything the launcher already
 * is lives untouched at [PAGE_HOME]. The orchestrator and the Claude executor flank it, and stay
 * empty placeholders until their own phases land.
 *
 * A real page's work — the Dell websocket, the Claude loop — must only run while its page is
 * settled, so the home keeps the memory and CPU budget we spent the day earning. The placeholders
 * cost nothing, so that guarantee holds trivially for now.
 */
@Composable
fun CockpitPager(
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequestWidgetPicker: () -> Unit,
    onWidgetSelected: (ComponentName) -> Unit,
    onWidgetPickerDismiss: () -> Unit,
    appRepository: AppRepository,
) {
    val pagerState = rememberPagerState(initialPage = PAGE_HOME) { PAGE_COUNT }

    // Pressing Home always returns to the centre. The side pages are somewhere you go on purpose;
    // the home is the resting place, so the Home key brings it back rather than leaving you parked
    // on the orchestrator or Claude. Reuses the same signal the drawer already listens to.
    LaunchedEffect(pagerState) {
        com.ykatchou.ylauncher.MainActivity.homePressed.collect {
            if (pagerState.currentPage != PAGE_HOME) pagerState.animateScrollToPage(PAGE_HOME)
        }
    }

    // Where the home's running-apps column sits, and whether the pager should yield to it.
    var leftColumnBounds by remember { mutableStateOf<Rect?>(null) }
    var pagerSwipeEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Watch the down event before the pager's own gesture detector reads it. Touch-slop
            // needs several move events to arm, so flipping userScrollEnabled here — on the down,
            // in the Initial pass, without consuming — settles before the pager would start
            // scrolling. A drag beginning inside the column closes an app; anywhere else pages.
            .pointerInput(leftColumnBounds) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val bounds = leftColumnBounds
                    pagerSwipeEnabled = bounds == null || !bounds.contains(down.position)
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fill,
            userScrollEnabled = pagerSwipeEnabled,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                PAGE_ORCHESTRATOR -> PlaceholderPage("Orquestrador", "controle do Dell — em breve")
                PAGE_HOME -> HomeScreen(
                    onNavigateToAbout = onNavigateToAbout,
                    onNavigateToSettings = onNavigateToSettings,
                    onRequestWidgetPicker = onRequestWidgetPicker,
                    onWidgetSelected = onWidgetSelected,
                    onWidgetPickerDismiss = onWidgetPickerDismiss,
                    appRepository = appRepository,
                    onLeftColumnBounds = { leftColumnBounds = it },
                )
                PAGE_CLAUDE -> com.ykatchou.ylauncher.ui.claude.ClaudeScreen()
            }
        }
    }
}

const val PAGE_ORCHESTRATOR = 0
const val PAGE_HOME = 1
const val PAGE_CLAUDE = 2
private const val PAGE_COUNT = 3
