package com.ykatchou.ylauncher.ui.cockpit

import android.content.ComponentName
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ykatchou.ylauncher.data.repository.AppRepository
import com.ykatchou.ylauncher.ui.home.HomeScreen

/**
 * The three-page cockpit. The launcher is the control surface, not the engine: this is only the
 * navigation shell. The home is the centre and the start page — everything the launcher already
 * is lives at [PAGE_HOME]. The orchestrator and the Claude executor flank it, and stay empty
 * placeholders until their own phases land.
 *
 * A real page's work — the Dell websocket, the Claude loop — must only run while its page is
 * settled, so the home keeps the memory and CPU budget we spent the day earning. The placeholders
 * cost nothing, so that guarantee holds trivially for now; each real page will gate its
 * connections on being the current page when it arrives.
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

    HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fill,
        // One page each side keeps the swipe instant without holding all three alive at once.
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
            )
            PAGE_CLAUDE -> PlaceholderPage("Claude", "executor via Shizuku — em breve")
        }
    }
}

const val PAGE_ORCHESTRATOR = 0
const val PAGE_HOME = 1
const val PAGE_CLAUDE = 2
private const val PAGE_COUNT = 3
