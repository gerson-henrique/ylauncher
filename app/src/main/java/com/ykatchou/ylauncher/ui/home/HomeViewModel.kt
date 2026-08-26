package com.ykatchou.ylauncher.ui.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykatchou.ylauncher.data.db.FavoriteDao
import com.ykatchou.ylauncher.data.db.FolderDao
import com.ykatchou.ylauncher.data.db.PanelDao
import com.ykatchou.ylauncher.data.model.AppInfo
import com.ykatchou.ylauncher.data.model.FavoriteApp
import com.ykatchou.ylauncher.data.model.Folder
import com.ykatchou.ylauncher.data.model.FolderApp
import com.ykatchou.ylauncher.data.model.Panel
import com.ykatchou.ylauncher.data.repository.AppRepository
import com.ykatchou.ylauncher.data.repository.PrefsRepository
import com.ykatchou.ylauncher.data.running.RunningAppsSource
import com.ykatchou.ylauncher.data.stats.SystemStats
import com.ykatchou.ylauncher.data.stats.SystemStatsReader
import com.ykatchou.ylauncher.util.ONE_WEEK_MS
import com.ykatchou.ylauncher.util.UsageStatsHelper
import com.ykatchou.ylauncher.widget.LauncherWidgetHost
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ykatchou.ylauncher.util.YLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val favoriteDao: FavoriteDao,
    private val folderDao: FolderDao,
    private val panelDao: PanelDao,
    private val prefsRepository: PrefsRepository,
    private val runningAppsSource: RunningAppsSource,
    private val systemStatsReader: SystemStatsReader,
    val widgetHost: LauncherWidgetHost,
) : ViewModel() {

    private val _usageStatsVersion = MutableStateFlow(0)

    fun refreshUsageStats() {
        _usageStatsVersion.value++
    }

    fun refreshAppsIfEmpty() {
        if (appRepository.appList.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(10_000) { appRepository.refreshApps() }
            } catch (e: Exception) {
                YLogger.e(TAG, "refreshAppsIfEmpty failed", e)
            }
        }
    }

    // All favorites (unfiltered, used by suggestions/recent to exclude all fav packages)
    private val allFavorites = favoriteDao.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Panel state
    val activePanel = prefsRepository.activePanel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val panels: StateFlow<List<Panel>> = panelDao.getAllPanels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Panels shown on the home screen / pickers — disabled panels are hidden but keep their favorites.
    val enabledPanels: StateFlow<List<Panel>> = panels
        .map { list -> list.filter { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites filtered by active panel
    val favorites: StateFlow<List<FavoriteApp>> = combine(
        allFavorites,
        prefsRepository.activePanel,
    ) { favs, panel ->
        favs.filter { it.panelId == panel }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suggestedApps: StateFlow<List<AppInfo>> = combine(
        allFavorites,
        prefsRepository.suggestionCount,
        appRepository.appList,
        _usageStatsVersion,
    ) { favs, count, _, _ ->
        if (count == 0) return@combine emptyList()
        val favPackages = favs.map { it.packageName }.toSet()
        val topApps = UsageStatsHelper.getTopApps(context, appRepository, count = count + 10)
        topApps.filter { it.packageName !in favPackages }.take(count)
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentApps: StateFlow<List<AppInfo>> = combine(
        allFavorites,
        prefsRepository.recentAppsCount,
        suggestedApps,
        _usageStatsVersion,
    ) { favs, recentCount, suggested, _ ->
        if (recentCount == 0) return@combine emptyList()
        val favPackages = favs.map { it.packageName }.toSet()
        val suggestedPackages = suggested.map { it.packageName }.toSet()
        UsageStatsHelper.getRecentApps(
            context, appRepository, count = recentCount,
            excludePackages = favPackages + suggestedPackages,
        )
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Whether the running-apps column should offer drag-to-close at all. */
    val canCloseRunningApps: Boolean get() = runningAppsSource.canClose

    /**
     * Live readings for the panel under the running-apps column. Polled rather than pushed,
     * because none of these sources emit events, and only while the home screen is actually being
     * looked at — WhileSubscribed stops the loop the moment the user opens an app, so this costs
     * nothing in the background.
     */
    val systemStats: StateFlow<SystemStats> = flow {
        while (true) {
            emit(systemStatsReader.read())
            delay(STATS_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SystemStats())

    /**
     * The left column: what is open right now, most recent first. Recomputed whenever the home
     * screen comes back to the foreground, since the list goes stale the moment the user leaves.
     */
    val runningApps: StateFlow<List<AppInfo>> = combine(
        _usageStatsVersion,
        appRepository.appList,
    ) { _, _ ->
        // Null means the source could not read; an empty list means nothing is open. Both show
        // an empty column, but only the second one is the truth — and the adaptive source has
        // already tried the fallback before returning null here.
        runningAppsSource.getRunningApps(RUNNING_APPS_LIMIT).orEmpty()
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ends [app] and drops it from the column. No-op when the source cannot close. */
    fun closeRunningApp(app: AppInfo) {
        if (!runningAppsSource.canClose) return
        viewModelScope.launch(Dispatchers.IO) {
            runningAppsSource.close(app)
            refreshUsageStats()
        }
    }

    /**
     * The pinned right-hand column, resolved to installed apps and kept in the order the
     * user set. A package that is not installed simply drops out instead of leaving a hole.
     * Where the same package exists in both the personal and the work profile, the personal
     * one wins — the column is a shortcut, not a profile switcher.
     */
    val quickApps: StateFlow<List<AppInfo>> = combine(
        prefsRepository.quickApps,
        appRepository.appList,
    ) { packages, apps ->
        val mine = android.os.Process.myUserHandle()
        packages.mapNotNull { pkg ->
            val matches = apps.filter { it.packageName == pkg }
            matches.firstOrNull { it.userHandle == mine } ?: matches.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeWidgetIds = prefsRepository.homeWidgetIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All display/swipe/HAL prefs in one snapshot — single collectAsState() in the UI. */
    val homePrefs: StateFlow<com.ykatchou.ylauncher.data.repository.HomePrefs> =
        prefsRepository.homePrefs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.ykatchou.ylauncher.data.repository.HomePrefs())

    // Magic-button (HAL) actions are global, shared across all panels.
    val halTapAction: StateFlow<String> = homePrefs
        .map { it.halTapActionRaw }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrefsRepository.DEFAULT_HAL_TAP_ACTION)

    val halLongPressAction: StateFlow<String> = homePrefs
        .map { it.halLongPressActionRaw }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SETTINGS")

    val halDoubleTapAction: StateFlow<String> = homePrefs
        .map { it.halDoubleTapActionRaw }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "APP_DRAWER")

    private val dbWriteMutex = Mutex()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    // Null until DataStore has emitted a real value — avoids a race where the tour and the
    // usage-stats permission prompt could both fire off a seeded default on the same frame.
    val hasSeenOnboardingTour: StateFlow<Boolean?> = prefsRepository.hasSeenOnboardingTour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        reconcileLegacyPanelNamesIfNeeded()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(10_000) { appRepository.refreshApps() }
            } catch (e: TimeoutCancellationException) {
                YLogger.e(TAG, "refreshApps timed out", e)
            } catch (e: Exception) {
                YLogger.e(TAG, "refreshApps failed", e)
            }
            appRepository.registerCallback()
            try {
                autoPopulateFavoritesIfNeeded()
            } catch (e: Exception) {
                YLogger.e(TAG, "autoPopulate failed", e)
            }
            try {
                prefsRepository.recordFirstLaunchIfNeeded()
            } catch (e: Exception) {
                YLogger.e(TAG, "recordFirstLaunch failed", e)
            }
        }
        viewModelScope.launch {
            com.ykatchou.ylauncher.MainActivity.homePressed.collect { closeDrawer() }
        }
        // If the active panel gets disabled (or deleted) elsewhere, hop to the first enabled one.
        viewModelScope.launch {
            combine(activePanel, enabledPanels) { active, enabled -> active to enabled }
                .collect { (active, enabled) ->
                    if (enabled.isNotEmpty() && enabled.none { it.id == active }) {
                        prefsRepository.setActivePanel(enabled.first().id)
                    }
                }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"

        /** Beyond a handful the column stops being a switcher and becomes a list to read. */
        private const val RUNNING_APPS_LIMIT = 6

        /** Fast enough to feel live, slow enough not to be a battery cost of its own. */
        private const val STATS_INTERVAL_MS = 3000L
    }

    /**
     * The active panel straight from DataStore. [activePanel] is a `WhileSubscribed` StateFlow
     * seeded with 0L, so its `value` can still be that placeholder (or a panel deleted since)
     * when a write runs — and a favorite written against a panel that does not exist is exactly
     * the FK violation we must never produce. Reading the pref keeps writes on the panel the
     * user is actually looking at; FavoriteDao clamps whatever we hand it as the last line of
     * defence.
     */
    private suspend fun currentPanelId(): Long = prefsRepository.activePanel.first()

    private suspend fun autoPopulateFavoritesIfNeeded() {
        if (favoriteDao.count() > 0) return

        val panelId = currentPanelId()

        // Try usage stats first (imports your real most-used apps)
        val topApps = UsageStatsHelper.getTopApps(context, appRepository, count = 10)
        if (topApps.isNotEmpty()) {
            val favorites = topApps.mapIndexed { index, app ->
                FavoriteApp(index, app.packageName, app.activityClassName, app.appLabel, app.userHandle.toString(), panelId = panelId)
            }
            favoriteDao.insertAll(favorites)
            prefsRepository.setFirstLaunchDone()
            return
        }

        // Fallback: standard apps every phone has a default handler for
        val defaults = mutableListOf<FavoriteApp>()
        var position = 0

        fun addDefault(displayName: String, app: AppInfo?) {
            if (app == null) return
            defaults.add(FavoriteApp(position++, app.packageName, app.activityClassName, displayName, app.userHandle.toString(), panelId = panelId))
        }

        addDefault("Messages", appRepository.resolveDefaultApp(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)))
        addDefault("Phone", appRepository.resolveDefaultApp(Intent(Intent.ACTION_DIAL)))
        addDefault(
            "Camera",
            appRepository.resolveDefaultApp(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        )
        addDefault(
            "Browser",
            appRepository.resolveDefaultApp(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://"))),
        )
        addDefault("YouTube", appRepository.findAppByPackage("com.google.android.youtube"))

        if (defaults.isNotEmpty()) {
            favoriteDao.insertAll(defaults)
        }
        prefsRepository.setFirstLaunchDone()
    }

    /**
     * Usage-stats permission is now requested only after the onboarding tour closes, so the
     * initial [autoPopulateFavoritesIfNeeded] pass usually runs before permission is granted and
     * falls back to (or finds no) default apps. Call this on resume to retry with real usage
     * data once the user grants the permission, as long as favorites are still empty.
     */
    fun autoPopulateFromUsageStatsIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!UsageStatsHelper.hasPermission(context)) return@launch
            if (favoriteDao.count() > 0) return@launch
            val topApps = UsageStatsHelper.getTopApps(context, appRepository, count = 10)
            if (topApps.isEmpty()) return@launch
            val panelId = currentPanelId()
            val favorites = topApps.mapIndexed { index, app ->
                FavoriteApp(index, app.packageName, app.activityClassName, app.appLabel, app.userHandle.toString(), panelId = panelId)
            }
            favoriteDao.insertAll(favorites)
        }
    }

    fun reimportFromUsageStats() {
        viewModelScope.launch {
            val topApps = UsageStatsHelper.getTopApps(context, appRepository, count = 10)
            if (topApps.isNotEmpty()) {
                dbWriteMutex.withLock {
                    val panelId = currentPanelId()
                    favoriteDao.deleteByPanel(panelId)
                    val otherMaxPos = favoriteDao.getAllFavoritesOnce()
                        .maxOfOrNull { it.position } ?: -1
                    val basePos = otherMaxPos + 1
                    val favorites = topApps.mapIndexed { index, app ->
                        FavoriteApp(basePos + index, app.packageName, app.activityClassName, app.appLabel, app.userHandle.toString(), panelId = panelId)
                    }
                    favoriteDao.insertAll(favorites)
                }
            }
        }
    }

    fun hasUsageStatsPermission(): Boolean = UsageStatsHelper.hasPermission(context)

    fun requestUsageStatsPermission() = UsageStatsHelper.requestPermission(context)

    fun onOnboardingTourFinished() {
        viewModelScope.launch { prefsRepository.setOnboardingTourSeen() }
    }

    fun onReviewRateHighConfirmed() {
        viewModelScope.launch { prefsRepository.setReviewNeverAsk(true) }
    }

    fun onReviewSnoozed() {
        viewModelScope.launch { prefsRepository.setReviewSnoozedUntil(System.currentTimeMillis() + ONE_WEEK_MS) }
    }

    fun removeWidget(widgetId: Int) {
        widgetHost.deleteAppWidgetId(widgetId)
        viewModelScope.launch { prefsRepository.removeHomeWidgetId(widgetId) }
    }

    fun removeAllWidgets() {
        viewModelScope.launch {
            val ids = prefsRepository.homeWidgetIdsOnce()
            ids.forEach { widgetHost.deleteAppWidgetId(it) }
            prefsRepository.clearHomeWidgetIds()
        }
    }

    fun openDrawer() { _isDrawerOpen.value = true }
    fun closeDrawer() { _isDrawerOpen.value = false }

    fun addFavoriteApp(app: AppInfo) {
        viewModelScope.launch {
            dbWriteMutex.withLock {
                val panelId = currentPanelId()
                val nextPosition = (favoriteDao.getAllFavoritesOnce().maxOfOrNull { it.position } ?: -1) + 1
                favoriteDao.insertFavorite(
                    FavoriteApp(
                        position = nextPosition,
                        packageName = app.packageName,
                        activityClassName = app.activityClassName,
                        displayName = app.appLabel,
                        userHandleString = app.userHandle.toString(),
                        panelId = panelId,
                    )
                )
            }
        }
    }

    fun saveFavorites(favorites: List<FavoriteApp>) {
        viewModelScope.launch {
            dbWriteMutex.withLock {
                val panelId = currentPanelId()
                // Delete only this panel's favorites
                favoriteDao.deleteByPanel(panelId)
                // Compute a safe starting position that won't collide with other panels
                val otherMaxPos = favoriteDao.getAllFavoritesOnce()
                    .maxOfOrNull { it.position } ?: -1
                val basePos = otherMaxPos + 1
                favoriteDao.insertAll(favorites.mapIndexed { index, fav ->
                    fav.copy(position = basePos + index, panelId = panelId)
                })
            }
        }
    }

    // Panel operations

    fun switchPanel(panelId: Long) {
        viewModelScope.launch { prefsRepository.setActivePanel(panelId) }
    }

    fun moveFavoriteToPanel(favorite: FavoriteApp, targetPanelId: Long) {
        viewModelScope.launch {
            val targetFavs = favoriteDao.getAllFavoritesOnce().filter { it.panelId == targetPanelId }
            val nextPosition = (targetFavs.maxOfOrNull { it.position } ?: -1) + 1
            favoriteDao.deleteFavoriteAt(favorite.position)
            favoriteDao.insertFavorite(favorite.copy(position = nextPosition, panelId = targetPanelId))
        }
    }

    fun addPanel(name: String) {
        viewModelScope.launch {
            val nextPosition = (panels.value.maxOfOrNull { it.position } ?: -1) + 1
            val newId = panelDao.insertPanel(Panel(name = name, position = nextPosition))
            prefsRepository.setActivePanel(newId)
        }
    }

    fun renamePanel(id: Long, newName: String) {
        viewModelScope.launch { panelDao.renamePanel(id, newName) }
    }

    fun reorderPanels(newOrder: List<Panel>) {
        viewModelScope.launch {
            newOrder.forEachIndexed { index, panel ->
                if (panel.position != index) panelDao.updatePanel(panel.copy(position = index))
            }
        }
    }

    fun deletePanel(id: Long) {
        viewModelScope.launch {
            // Read the panels back from the DB, not the StateFlow: `panels` may still be its
            // empty placeholder, and deleting the last panel would leave favorites nothing to
            // hang off (see FavoriteDao — the panelId foreign key must always resolve).
            val remaining = panelDao.getAllPanelsOnce().filterNot { it.id == id }
            if (remaining.isEmpty()) return@launch
            panelDao.deletePanel(id) // FK cascade removes this panel's favorite_apps rows — installed apps untouched
            if (currentPanelId() == id) {
                prefsRepository.setActivePanel(remaining.first().id)
            }
        }
    }

    fun setPanelEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            val stillEnabled = panelDao.getAllPanelsOnce().filter { it.enabled && it.id != id }
            if (!enabled && stillEnabled.isEmpty()) return@launch
            panelDao.setEnabled(id, enabled)
            if (!enabled && currentPanelId() == id) {
                prefsRepository.setActivePanel(stillEnabled.first().id)
            }
        }
    }

    /**
     * One-time reconciliation: after MIGRATION_3_4 seeds `panels` with placeholder names
     * (Room migrations can't read DataStore), copy the user's real legacy panel names
     * (if any) from DataStore onto the matching Panel rows by id. Safe to call on every
     * launch — no-ops once `markLegacyPanelNamesMigrated()` has run.
     */
    fun reconcileLegacyPanelNamesIfNeeded() {
        viewModelScope.launch {
            if (prefsRepository.legacyPanelNamesMigrated()) return@launch
            val legacyNames = prefsRepository.legacyPanelNamesCsvOnce()
                ?.split("|")?.filter { it.isNotBlank() }
            if (legacyNames != null) {
                val existingIds = panelDao.getAllPanelsOnce().map { it.id }.toSet()
                legacyNames.forEachIndexed { index, name ->
                    if (index.toLong() in existingIds) panelDao.renamePanel(index.toLong(), name)
                }
            }
            prefsRepository.markLegacyPanelNamesMigrated()
        }
    }

    // Folder operations

    fun createFolder(name: String = "New Folder", emoji: String = "📁") {
        viewModelScope.launch {
            val panelId = currentPanelId()
            val currentFavs = favoriteDao.getAllFavoritesOnce()
            val nextPosition = (currentFavs.maxOfOrNull { it.position } ?: -1) + 1
            val folderId = folderDao.insertFolder(Folder(name = name, position = nextPosition, iconEmoji = emoji))
            favoriteDao.insertFavorite(
                FavoriteApp(
                    position = nextPosition,
                    packageName = "",
                    displayName = name,
                    folderId = folderId,
                    iconEmoji = emoji,
                    panelId = panelId,
                )
            )
        }
    }

    fun getFolderApps(folderId: Long): kotlinx.coroutines.flow.Flow<List<FolderApp>> {
        return folderDao.getAppsInFolder(folderId)
    }

    suspend fun getFolderAppsOnce(folderId: Long): List<FolderApp> {
        return folderDao.getAppsInFolderOnce(folderId)
    }

    fun updateFolder(folderId: Long, name: String, emoji: String, apps: List<FolderApp>) {
        viewModelScope.launch {
            // Update folder entity
            val existing = folderDao.getFolderById(folderId) ?: return@launch
            folderDao.updateFolder(existing.copy(name = name, iconEmoji = emoji))

            // Update the favorite entry to keep display in sync
            val favs = favoriteDao.getAllFavoritesOnce()
            val folderFav = favs.find { it.folderId == folderId }
            if (folderFav != null) {
                favoriteDao.updateFavorite(folderFav.copy(displayName = name, iconEmoji = emoji))
            }

            // Replace folder apps
            folderDao.deleteAllAppsInFolder(folderId)
            apps.forEachIndexed { index, app ->
                folderDao.insertFolderApp(app.copy(folderId = folderId, position = index))
            }
        }
    }

    fun addAppToFolder(folderId: Long, app: AppInfo) {
        viewModelScope.launch {
            val existingApps = folderDao.getAppsInFolderOnce(folderId)
            val nextPos = (existingApps.maxOfOrNull { it.position } ?: -1) + 1
            folderDao.insertFolderApp(
                FolderApp(
                    folderId = folderId,
                    packageName = app.packageName,
                    activityClassName = app.activityClassName,
                    displayName = app.appLabel,
                    position = nextPos,
                    userHandleString = app.userHandle.toString(),
                )
            )
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            folderDao.deleteFolder(folderId)
            // Remove the favorite entry
            val favs = favoriteDao.getAllFavoritesOnce()
            val folderFav = favs.find { it.folderId == folderId }
            if (folderFav != null) {
                favoriteDao.deleteFavoriteAt(folderFav.position)
            }
        }
    }

    fun getAllFolders(): kotlinx.coroutines.flow.Flow<List<Folder>> {
        return folderDao.getAllFolders()
    }

    fun moveFavoriteToFolder(favorite: FavoriteApp, folderId: Long) {
        viewModelScope.launch {
            // Add the app into the folder
            val existingApps = folderDao.getAppsInFolderOnce(folderId)
            val nextPos = (existingApps.maxOfOrNull { it.position } ?: -1) + 1
            folderDao.insertFolderApp(
                FolderApp(
                    folderId = folderId,
                    packageName = favorite.packageName,
                    activityClassName = favorite.activityClassName,
                    displayName = favorite.displayName,
                    position = nextPos,
                    userHandleString = favorite.userHandleString,
                )
            )
            // Remove it from favorites
            favoriteDao.deleteFavoriteAt(favorite.position)
        }
    }

    override fun onCleared() {
        super.onCleared()
        appRepository.unregisterCallback()
    }

}
