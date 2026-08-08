package com.ykatchou.ylauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ykatchou.ylauncher.billing.BillingManager
import com.ykatchou.ylauncher.data.db.PanelDao
import com.ykatchou.ylauncher.data.model.AppInfo
import com.ykatchou.ylauncher.data.model.Panel
import com.ykatchou.ylauncher.data.repository.AppRepository
import com.ykatchou.ylauncher.data.repository.PrefsRepository
import com.ykatchou.ylauncher.ui.components.CoffeeFab
import com.ykatchou.ylauncher.ui.hal.HalAction
import com.ykatchou.ylauncher.ui.home.EditPanelsDialog
import com.ykatchou.ylauncher.util.AppIconCache
import com.ykatchou.ylauncher.util.openDefaultLauncherSettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    prefsRepository: PrefsRepository,
    appRepository: AppRepository,
    billingManager: BillingManager,
    panelDao: PanelDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val showClock by prefsRepository.showClock.collectAsState(initial = true)
    val autoShowKeyboard by prefsRepository.autoShowKeyboard.collectAsState(initial = true)
    val swipeLeftEnabled by prefsRepository.swipeLeftEnabled.collectAsState(initial = true)
    val swipeRightEnabled by prefsRepository.swipeRightEnabled.collectAsState(initial = true)
    val swipeLeftName by prefsRepository.swipeLeftName.collectAsState(initial = "Camera")
    val swipeRightName by prefsRepository.swipeRightName.collectAsState(initial = "Phone")
    val textSizeScale by prefsRepository.textSizeScale.collectAsState(initial = 1f)
    val suggestionCount by prefsRepository.suggestionCount.collectAsState(initial = 3)
    val recentAppsCount by prefsRepository.recentAppsCount.collectAsState(initial = 0)
    val panels by panelDao.getAllPanels().collectAsState(initial = emptyList())
    val autoLaunchDelay by prefsRepository.autoLaunchDelay.collectAsState(initial = 0f)
    val showNotifBubble by prefsRepository.showNotifBubble.collectAsState(initial = true)
    val showNotifPreview by prefsRepository.showNotifPreview.collectAsState(initial = true)
    val showNotifBadge by prefsRepository.showNotifBadge.collectAsState(initial = true)
    val showDonation by prefsRepository.showDonation.collectAsState(initial = true)
    val billingState by billingManager.billingState.collectAsState()
    val activePanel by prefsRepository.activePanel.collectAsState(initial = 0L)
    val halTap by prefsRepository.halTapAction.collectAsState(initial = "ASSISTANT")
    val halLongPress by prefsRepository.halLongPressAction.collectAsState(initial = "SETTINGS")
    val halDoubleTap by prefsRepository.halDoubleTapAction.collectAsState(initial = "APP_DRAWER")
    var showManagePanels by remember { mutableStateOf(false) }

    // Local state for sliders to avoid excessive DataStore writes during drag
    var sliderValue by remember(textSizeScale) { mutableFloatStateOf(textSizeScale) }
    var suggestionSlider by remember(suggestionCount) { mutableFloatStateOf(suggestionCount.toFloat()) }
    var recentSlider by remember(recentAppsCount) { mutableFloatStateOf(recentAppsCount.toFloat()) }
    var autoLaunchDelaySlider by remember(autoLaunchDelay) { mutableFloatStateOf(autoLaunchDelay) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Display")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Show clock",
                subtitle = "Display clock & date on home screen",
                checked = showClock,
                onCheckedChange = { scope.launch { prefsRepository.setShowClock(it) } },
            )


            // Text size slider — writes only on release
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Text size",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${(sliderValue * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        scope.launch { prefsRepository.setTextSizeScale((sliderValue * 100).roundToInt()) }
                    },
                    valueRange = 0.8f..1.4f,
                    steps = 5,
                )
            }

            // Suggestion count slider
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Suggested apps",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (suggestionSlider.roundToInt() == 0) "Off" else "${suggestionSlider.roundToInt()} most used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Slider(
                    value = suggestionSlider,
                    onValueChange = { suggestionSlider = it },
                    onValueChangeFinished = {
                        scope.launch { prefsRepository.setSuggestionCount(suggestionSlider.roundToInt()) }
                    },
                    valueRange = 0f..8f,
                    steps = 7,
                )
            }

            // Recent apps count slider
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Recent apps",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (recentSlider.roundToInt() == 0) "Off" else "${recentSlider.roundToInt()} last used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Slider(
                    value = recentSlider,
                    onValueChange = { recentSlider = it },
                    onValueChangeFinished = {
                        scope.launch { prefsRepository.setRecentAppsCount(recentSlider.roundToInt()) }
                    },
                    valueRange = 0f..8f,
                    steps = 7,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Notifications")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Notification bubble",
                subtitle = "Show notifications next to the clock",
                checked = showNotifBubble,
                onCheckedChange = { scope.launch { prefsRepository.setShowNotifBubble(it) } },
            )

            SettingsToggle(
                title = "Notification preview",
                subtitle = "Show notification text below app name",
                checked = showNotifPreview,
                onCheckedChange = { scope.launch { prefsRepository.setShowNotifPreview(it) } },
            )

            SettingsToggle(
                title = "Notification badge",
                subtitle = "Show count badge on app icon",
                checked = showNotifBadge,
                onCheckedChange = { scope.launch { prefsRepository.setShowNotifBadge(it) } },
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Panels")
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = panels.joinToString(", ") { it.name }.ifBlank { "No panels yet" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Manage panels",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { showManagePanels = true }
                        .padding(vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Gestures")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Swipe left → $swipeLeftName",
                subtitle = "Swipe left on home to open $swipeLeftName",
                checked = swipeLeftEnabled,
                onCheckedChange = { scope.launch { prefsRepository.setSwipeLeftEnabled(it) } },
            )

            SettingsToggle(
                title = "Swipe right → $swipeRightName",
                subtitle = "Swipe right on home to open $swipeRightName",
                checked = swipeRightEnabled,
                onCheckedChange = { scope.launch { prefsRepository.setSwipeRightEnabled(it) } },
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Magic button")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The magic button sits on your home screen — choose what tap, long press, and double tap do.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(8.dp))

            ActionPicker(
                label = "Tap",
                currentAction = halTap,
                onActionSelected = { scope.launch { prefsRepository.setHalTapAction(it) } },
                appRepository = appRepository,
            )
            ActionPicker(
                label = "Long press",
                currentAction = halLongPress,
                onActionSelected = { scope.launch { prefsRepository.setHalLongPressAction(it) } },
                appRepository = appRepository,
            )
            ActionPicker(
                label = "Double tap",
                currentAction = halDoubleTap,
                onActionSelected = { scope.launch { prefsRepository.setHalDoubleTapAction(it) } },
                appRepository = appRepository,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Behavior")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Auto-show keyboard",
                subtitle = "Open keyboard when app drawer opens",
                checked = autoShowKeyboard,
                onCheckedChange = { scope.launch { prefsRepository.setAutoShowKeyboard(it) } },
            )

            // Auto-launch delay slider (0.0–5.0s, 0.1 steps)
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Auto-launch delay",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (autoLaunchDelaySlider <= 0f) "Off (instant)" else "${"%.1f".format(autoLaunchDelaySlider)}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Slider(
                    value = autoLaunchDelaySlider,
                    onValueChange = { autoLaunchDelaySlider = (it * 10).roundToInt() / 10f },
                    onValueChangeFinished = {
                        scope.launch { prefsRepository.setAutoLaunchDelay((autoLaunchDelaySlider * 10).roundToInt()) }
                    },
                    valueRange = 0f..5f,
                    steps = 49,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Support")
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggle(
                title = "Show donation button",
                subtitle = "Display the coffee button on home & about screens",
                checked = showDonation,
                onCheckedChange = { scope.launch { prefsRepository.setShowDonation(it) } },
            )

            if (showDonation) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CoffeeFab(
                        billingManager = billingManager,
                        billingState = billingState,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("System")
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Default launcher",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Change which launcher is used as your home screen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Open launcher settings →",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { context.openDefaultLauncherSettings() }
                    .padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Launch guided tour",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Replay the onboarding tour from the home screen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Start tour →",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        scope.launch {
                            prefsRepository.resetOnboardingTour()
                            onBack()
                        }
                    }
                    .padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "← Back",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(vertical = 8.dp),
            )
        }
    }

    if (showManagePanels) {
        EditPanelsDialog(
            panels = panels,
            onRename = { id, name -> scope.launch { panelDao.renamePanel(id, name) } },
            onReorder = { newOrder ->
                scope.launch {
                    newOrder.forEachIndexed { index, panel ->
                        if (panel.position != index) panelDao.updatePanel(panel.copy(position = index))
                    }
                }
            },
            onAdd = { name ->
                scope.launch {
                    val nextPosition = (panels.maxOfOrNull { it.position } ?: -1) + 1
                    panelDao.insertPanel(Panel(name = name, position = nextPosition))
                }
            },
            onDelete = { id ->
                scope.launch {
                    val remaining = panels.filterNot { it.id == id }
                    if (remaining.isEmpty()) return@launch
                    panelDao.deletePanel(id)
                    if (activePanel == id) {
                        prefsRepository.setActivePanel(remaining.first().id)
                    }
                }
            },
            onDismiss = { showManagePanels = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ActionPicker(
    label: String,
    currentAction: String,
    onActionSelected: (String) -> Unit,
    appRepository: AppRepository,
) {
    var expanded by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    val action = HalAction.fromKey(currentAction)
    val context = LocalContext.current
    val displayName = if (action == HalAction.CUSTOM_APP) {
        val decoded = HalAction.decodeApp(currentAction)
        if (decoded != null) {
            try {
                val pm = context.packageManager
                val appLabel = pm.getApplicationInfo(decoded.first, 0).loadLabel(pm).toString()
                appLabel
            } catch (_: Exception) { decoded.first.substringAfterLast('.') }
        } else "?"
    } else action.label

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            HalAction.entries.forEach { halAction ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(halAction.label) },
                    onClick = {
                        expanded = false
                        if (halAction == HalAction.CUSTOM_APP) {
                            showAppPicker = true
                        } else {
                            onActionSelected(halAction.name)
                        }
                    },
                )
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            appRepository = appRepository,
            onAppSelected = { app ->
                showAppPicker = false
                onActionSelected(HalAction.encodeApp(app.packageName, app.activityClassName))
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

@Composable
private fun AppPickerDialog(
    appRepository: AppRepository,
    onAppSelected: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val apps by appRepository.appList.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.appLabel.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(350.dp)) {
                    items(filtered, key = { it.packageName + it.activityClassName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAppSelected(app) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            app.icon?.let { icon ->
                                val bitmap = remember(app.packageName) {
                                    AppIconCache.get(icon, app.packageName, 36)
                                }
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            Text(
                                text = app.appLabel,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
